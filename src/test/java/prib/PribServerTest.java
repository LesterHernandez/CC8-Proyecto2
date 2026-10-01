package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Integración real por HTTP/WebSocket con clientes Java independientes.
 * Genera almacenes pequeños propios: no requiere ZIP del curso ni navegador.
 */
public final class PribServerTest {
    public static void main(String[] args) throws Exception {
        framing();
        Path root = Files.createTempDirectory(Path.of("build"), "server-test-");
        Path data = Files.createDirectory(root.resolve("data"));
        BufferedImage image = new BufferedImage(320, 260, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<260; y++) for (int x=0; x<320; x++) image.setRGB(x, y, (x*1009+y*199)&0xffffff);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image, "png", png);
        Path archive = root.resolve("source.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("test.png")); zip.write(png.toByteArray()); zip.closeEntry();
        }
        PrepareImage.prepare(archive, "test.png", data.resolve("first"));
        PrepareImage.prepare(archive, "test.png", data.resolve("second"));
        ImageStore first = new ImageStore(data.resolve("first")), second = new ImageStore(data.resolve("second"));
        try (PribServer server = new PribServer(data, Path.of("web"), 0); HttpClient http = HttpClient.newHttpClient()) {
            Thread thread = new Thread(() -> { try { server.run(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            thread.start();
            try {
                URI base = URI.create("http://localhost:" + server.port());
                HttpResponse<String> page = http.send(HttpRequest.newBuilder(base).build(), HttpResponse.BodyHandlers.ofString());
                check(page.statusCode()==200 && page.body().contains("Visor de imágenes"), "HTTP inicial");
                for (String path : List.of("/app.js", "/style.css", "/icon.svg"))
                    check(http.send(HttpRequest.newBuilder(base.resolve(path)).build(), HttpResponse.BodyHandlers.discarding()).statusCode()==200, "Recurso local");
                check(http.send(HttpRequest.newBuilder(base.resolve("/data/first/image.properties")).build(), HttpResponse.BodyHandlers.discarding()).statusCode()==404, "Sin acceso directo al almacén");
                try (Peer a = new Peer(http, server.port()); Peer b = new Peer(http, server.port())) {
                    a.hello(); b.hello(); check(!a.session.equals(b.session), "Sesiones diferentes");
                    // Las vistas de ambos clientes se solicitan antes de esperar sus respuestas.
                    a.view(first, 1, 0, 127, 127, 130, 130);
                    b.view(second, 1, 1, 0, 0, 160, 130);
                    a.finish(first, 1, 0, 127, 127, 130, 130);
                    b.finish(second, 1, 1, 0, 0, 160, 130);
                    System.out.println("PASS dos clientes: regiones independientes, FULL exacto y ACK");
                    // Sustituir una vista permite recibir lo que ya viajaba, pero no confundir generaciones.
                    a.view(first, 2, 0, 0, 0, 300, 250);
                    a.view(second, 3, 0, 300, 250, 20, 10);
                    a.finish(second, 3, 0, 300, 250, 20, 10);
                    a.close();
                    b.view(first, 2, 0, 0, 0, 1, 1); b.finish(first, 2, 0, 0, 0, 1, 1);
                    System.out.println("PASS cambio de vista, bordes y desconexión aislada");
                }
                try (Peer invalid = new Peer(http, server.port())) {
                    invalid.hello();
                    invalid.send("VIEW", Map.of("sessionId", "otra-sesion", "viewId", 1));
                    check(invalid.next().toString().contains("ERROR"), "Rechazar sesión ajena");
                }
                try (Peer invalid = new Peer(http, server.port())) {
                    invalid.send("HELLO", Map.of("version", 2));
                    check(invalid.next().toString().contains("ERROR"), "Rechazar versión no soportada");
                }
                System.out.println("PASS HTTP local, rutas restringidas y controles inválidos");
            } finally { server.close(); thread.join(5000); check(!thread.isAlive(), "Cierre del servidor"); }
        }
    }

    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }

    /** Pruebas del framing, incluyendo texto partido entre tramas y ping intercalado. */
    private static void framing() {
        WebSocketFrames frames = new WebSocketFrames(); List<String> messages = new ArrayList<>();
        byte[] all = join(masked(1, false, "ho"), masked(9, true, "ping"), masked(0, true, "la"));
        ByteBuffer in = ByteBuffer.allocate(100);
        // Simular TCP entregando un solo byte por lectura.
        for (byte b : all) {
            in.put(b); in.flip(); frames.consume(in, (op, data) -> messages.add(op+":"+WebSocketFrames.utf8(data))); in.compact();
        }
        check(messages.equals(List.of("9:ping", "1:hola")), "Fragmentación y ping");
        try { new WebSocketFrames().consume(ByteBuffer.wrap(new byte[]{(byte)129, 0}), (op, b) -> {}); throw new AssertionError("Aceptó trama sin máscara"); }
        catch (IllegalArgumentException expected) { }
        try { Json.parse("{\"type\":\"HELLO\",\"type\":\"VIEW\"}"); throw new AssertionError("Aceptó clave repetida"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS framing parcial, máscara, fragmentación, ping y JSON");
    }
    private static byte[] masked(int opcode, boolean fin, String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8), out = new byte[raw.length+6];
        out[0]=(byte)(opcode | (fin?128:0)); out[1]=(byte)(128|raw.length);
        for (int i=0; i<4; i++) out[2+i]=(byte)(i+1);
        for (int i=0; i<raw.length; i++) out[i+6]=(byte)(raw[i]^out[2+i%4]); return out;
    }
    private static byte[] join(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); for (byte[] a : arrays) out.writeBytes(a); return out.toByteArray();
    }

    private static final class Peer implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder(); final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        final WebSocket socket; String session = "";
        Peer(HttpClient client, int port) {
            socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create("ws://localhost:"+port+"/ws"), this).join();
        }
        public void onOpen(WebSocket ws) { ws.request(1); }
        public CompletionStage<?> onText(WebSocket ws, CharSequence part, boolean last) {
            text.append(part); if (last) { received.add(text.toString()); text.setLength(0); } ws.request(1); return null;
        }
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer part, boolean last) {
            byte[] bytes = new byte[part.remaining()]; part.get(bytes); binary.writeBytes(bytes);
            if (last) { received.add(binary.toByteArray()); binary.reset(); } ws.request(1); return null;
        }
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) { ws.request(1); return ws.sendPong(message); }
        public void onError(WebSocket ws, Throwable error) { received.add(error); }
        Object next() throws InterruptedException {
            Object item = received.poll(10, TimeUnit.SECONDS);
            if (item == null || item instanceof Throwable) throw new AssertionError("Respuesta ausente o error: " + item);
            return item;
        }
        void send(String type, Map<String, ?> fields) {
            Map<String, Object> data = new LinkedHashMap<>(); data.put("version", 1); data.put("type", type); data.put("sessionId", session);
            data.putAll(fields); socket.sendText(Json.encode(data), true).join();
        }
        void hello() throws InterruptedException {
            send("HELLO", Map.of()); String catalog = next().toString();
            Matcher matcher = Pattern.compile("\"sessionId\":\"([^\"]+)\"").matcher(catalog);
            check(catalog.contains("IMAGE_INFO") && matcher.find(), "Catálogo inicial"); session = matcher.group(1);
        }
        void view(ImageStore image, int id, int level, int x, int y, int width, int height) {
            send("VIEW", Map.of("imageId", image.imageId, "viewId", id, "level", level, "x", x, "y", y, "width", width, "height", height));
        }
        void finish(ImageStore store, int id, int level, int x, int y, int width, int height) throws Exception {
            Set<String> seen = new HashSet<>(); int expected = -1;
            while (true) {
                Object item = next();
                if (item instanceof String control) {
                    Map<String, Object> message = Json.parse(control);
                    check(!Json.text(message, "type").equals("ERROR"), control);
                    if (Json.integer(message, "viewId") != id) continue;
                    check(session.equals(Json.text(message, "sessionId")), "Sesión de respuesta");
                    if (Json.text(message, "type").equals("VIEW_ACCEPTED")) expected = Json.integer(message, "blocks");
                    if (Json.text(message, "type").equals("VIEW_DONE")) {
                        int actual = ((x+width-1)/128-x/128+1)*((y+height-1)/128-y/128+1);
                        check(expected == actual && seen.size()==actual, "Solo bloques intersectados, sin faltantes"); return;
                    }
                } else {
                    ByteBuffer data = ByteBuffer.wrap((byte[])item); int size = data.getInt();
                    check(size>0 && size<=4096, "Cabecera binaria limitada"); byte[] header = new byte[size]; data.get(header);
                    Map<String, Object> message = Json.parse(WebSocketFrames.utf8(header));
                    if (Json.integer(message, "viewId") != id) continue;
                    check(session.equals(Json.text(message, "sessionId")) && store.imageId.equals(Json.text(message, "imageId")), "Identidad del bloque");
                    int bx = Json.integer(message, "x"), by = Json.integer(message, "y");
                    check(bx < x+width && by < y+height && bx+128 > x && by+128 > y, "No enviar región ajena");
                    byte[] rgb = new byte[data.remaining()]; data.get(rgb);
                    ImageStore.Block block = store.readBlock(level, bx/128, by/128);
                    check(Arrays.equals(rgb, block.rgb()) && block.hash().equals(Json.text(message, "expectedHash")), "RGB y hash exactos");
                    check(seen.add(Json.text(message, "blockId")), "Sin duplicados");
                    send("ACK", Map.of("viewId", id, "transferId", Json.integer(message, "transferId"), "hash", block.hash()));
                }
            }
        }
        public void close() { socket.abort(); }
    }
}
