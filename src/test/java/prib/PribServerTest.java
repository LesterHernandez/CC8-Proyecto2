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
        writeBudget();
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
                    while (true) {
                        String reply = invalid.next().toString();
                        if (reply.contains("CREDIT_STATUS")) continue;
                        check(reply.contains("ERROR"), "Rechazar sesión ajena"); break;
                    }
                }
                try (Peer invalid = new Peer(http, server.port())) {
                    invalid.send("HELLO", Map.of("version", 2));
                    check(invalid.next().toString().contains("ERROR"), "Rechazar versión no soportada");
                }
                creditTests(http, server.port(), first);
                reuseTests(http, server.port(), first, second);
                System.out.println("PASS HTTP local, rutas restringidas y controles inválidos");
            } finally { server.close(); thread.join(5000); check(!thread.isAlive(), "Cierre del servidor"); }
        }
    }

    /** El cliente lento no devuelve capacidad hasta que la prueba lo ordena.
     * Comparar bytes reales detecta errores de contabilidad de cabeceras, no solo de RGB.
     */
    private static void creditTests(HttpClient http, int port, ImageStore image) throws Exception {
        try (Peer slow = new Peer(http, port); Peer fast = new Peer(http, port)) {
            slow.hello(false); fast.hello();
            slow.view(image, 1, 0, 0, 0, 256, 256);
            creditStatus(slow, "WAIT_INIT"); noData(slow);
            slow.send("CREDIT_INIT", Map.of("capacityBytes", CreditWindow.MAX_PACKET));
            byte[] one = nextBlock(slow);
            Map<String, Object> state = creditStatus(slow, "WAIT_CREDIT");
            check(Json.integer(state, "availableBytes") == CreditWindow.MAX_PACKET-one.length, "Descuento exacto PRIB");
            Map<String, Object> header = blockHeader(one);
            slow.send("ACK", Map.of("viewId", 1, "transferId", Json.integer(header, "transferId"), "hash", Json.text(header, "expectedHash")));
            // Ni ACK ni repetir INIT pueden reponer crédito.
            slow.send("CREDIT_INIT", Map.of("capacityBytes", CreditWindow.MAX_PACKET));
            slow.send("CREDIT_STATUS", Map.of());
            state = creditStatus(slow, "WAIT_CREDIT");
            check(Json.integer(state, "availableBytes") == CreditWindow.MAX_PACKET-one.length, "ACK e INIT no liberan bytes");
            noData(slow);
            fast.view(image, 1, 0, 0, 0, 320, 260); fast.finish(image, 1, 0, 0, 0, 320, 260);
            slow.send("CREDIT_GRANT", Map.of("grantId", 1, "releasedBytes", one.length));
            byte[] two = nextBlock(slow); creditStatus(slow, "WAIT_CREDIT");
            slow.send("CREDIT_GRANT", Map.of("grantId", 1, "releasedBytes", one.length));
            noData(slow); // La misma concesión no financia un tercer bloque.
            slow.view(image, 2, 0, 0, 0, 256, 128);
            slow.send("CREDIT_STATUS", Map.of());
            state = creditStatus(slow, "WAIT_CREDIT");
            check(Json.integer(state, "outstandingBytes") == two.length, "Cambio de vista conserva deuda");
            slow.send("CREDIT_GRANT", Map.of("grantId", 2, "releasedBytes", one.length+two.length));
            byte[] three = nextBlock(slow); creditStatus(slow, "WAIT_CREDIT");
            check(Json.integer(blockHeader(three), "viewId") == 2, "Reanudar vista nueva");
            slow.send("CREDIT_GRANT", Map.of("grantId", 1, "releasedBytes", one.length)); noData(slow);
            long total = one.length+two.length+three.length;
            slow.send("CREDIT_GRANT", Map.of("grantId", 3, "releasedBytes", total));
            byte[] four = nextBlock(slow); creditStatus(slow, "IDLE");
            slow.send("CREDIT_GRANT", Map.of("grantId", 4, "releasedBytes", total+four.length));
            state = creditStatus(slow, "IDLE");
            check(Json.integer(state, "availableBytes") == CreditWindow.MAX_PACKET, "Capacidad recuperada sin ACK");
        }
        try (Peer fresh = new Peer(http, port)) {
            fresh.hello();
            Map<String, Object> state = creditStatus(fresh, "IDLE");
            check(Json.integer(state, "outstandingBytes") == 0 && Json.integer(state, "availableBytes") == 262144, "Nueva sesión sin deuda");
            fresh.send("CREDIT_GRANT", Map.of("grantId", 1, "releasedBytes", 1));
            check(fresh.next().toString().contains("ERROR"), "Rechazar liberación de bytes no enviados");
        }
        System.out.println("PASS créditos: pausa, reanudación, duplicados, ACK separado, vistas, cliente lento y sesión nueva");
    }
    private static void reuseTests(HttpClient http, int port, ImageStore first, ImageStore second) throws Exception {
        try (Peer cached = new Peer(http, port); Peer independent = new Peer(http, port)) {
            cached.caching = true; cached.hello(); independent.hello();
            cached.view(first, 1, 0, 0, 0, 320, 260); cached.finish(first, 1, 0, 0, 0, 320, 260);
            int initialBytes = Json.integer(cached.summary, "pribBytes");
            check(Json.integer(cached.summary, "full") == 9, "Primera vista FULL");
            cached.view(first, 2, 0, 0, 0, 320, 260); cached.finish(first, 2, 0, 0, 0, 320, 260);
            check(Json.integer(cached.summary, "reuse") == 9 && Json.integer(cached.summary, "full") == 0, "Volver a región usa REUSE");
            check(Json.integer(cached.summary, "pribBytes") < initialBytes/10, "Ahorro medido frente a FULL");
            cached.view(second, 3, 0, 0, 0, 320, 260); cached.finish(second, 3, 0, 0, 0, 320, 260);
            check(Json.integer(cached.summary, "ref") == 9, "Otra identidad con RGB idéntico usa REF");
            independent.view(second, 1, 0, 0, 0, 320, 260); independent.finish(second, 1, 0, 0, 0, 320, 260);
            check(Json.integer(independent.summary, "full") == 9, "Inventario independiente por cliente");
            cached.send("CACHE_STATE", Map.of("cacheSeq", ++cached.cacheSeq, "operation", "CLEAR")); cached.cached.clear();
            cached.view(first, 4, 0, 0, 0, 128, 128); cached.finish(first, 4, 0, 0, 0, 128, 128);
            check(Json.integer(cached.summary, "full") == 1, "Inventario vaciado requiere FULL");
        }
        try (Peer slow = new Peer(http, port)) {
            slow.hello(false); slow.send("CREDIT_INIT", Map.of("capacityBytes", CreditWindow.MAX_PACKET));
            slow.view(first, 1, 0, 0, 0, 256, 256);
            byte[] packet = nextBlock(slow); creditStatus(slow, "WAIT_CREDIT");
            slow.send("CANCEL", Map.of("viewId", 1));
            Map<String, Object> cancelled;
            do { cancelled = Json.parse(slow.next().toString()); }
            while (!Json.text(cancelled, "type").equals("CANCELLED"));
            check(Json.integer(cancelled, "outstandingBytes") == packet.length, "CANCEL no devuelve crédito en tránsito");
            noData(slow);
            slow.released = packet.length; slow.grantId = 1;
            slow.send("CREDIT_GRANT", Map.of("grantId", 1, "releasedBytes", packet.length));
            slow.view(first, 2, 0, 256, 256, 64, 4); slow.finish(first, 2, 0, 256, 256, 64, 4);
        }
        // Con saldo insuficiente para FULL, una referencia pequeña elegible aún progresa.
        try (Peer eligible = new Peer(http, port)) {
            eligible.hello(false); eligible.send("CREDIT_INIT", Map.of("capacityBytes", CreditWindow.MAX_PACKET));
            eligible.view(first, 1, 0, 0, 0, 128, 128);
            byte[] data = nextBlock(eligible); Map<String, Object> header = blockHeader(data);
            eligible.send("CACHE_STATE", Map.of("cacheSeq", 1, "operation", "PUT", "imageId", first.imageId,
                    "blockId", "0:0:0", "width", 128, "height", 128, "hash", Json.text(header, "expectedHash")));
            eligible.view(first, 2, 0, 0, 0, 256, 128);
            byte[] reference = nextBlock(eligible);
            check(Json.text(blockHeader(reference), "mode").equals("REUSE") && reference.length < 4096, "Referencia elegible con saldo pequeño");
            Map<String, Object> state = creditStatus(eligible, "WAIT_CREDIT");
            check(Json.integer(state, "outstandingBytes") == data.length+reference.length, "REF consume bytes PRIB exactos");
        }
        System.out.println("PASS etapa 5: FULL/REUSE/REF, ahorro, inventario aislado, CLEAR, CANCEL y referencia elegible");
    }

    private static Map<String, Object> blockHeader(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes); byte[] header = new byte[data.getInt()]; data.get(header);
        return Json.parse(WebSocketFrames.utf8(header));
    }
    private static byte[] nextBlock(Peer peer) throws Exception {
        while (true) {
            Object item = peer.next();
            if (item instanceof byte[] bytes) return bytes;
            check(!item.toString().contains("ERROR"), item.toString());
        }
    }
    private static Map<String, Object> creditStatus(Peer peer, String expectedState) throws Exception {
        while (true) {
            Object item = peer.next(); check(item instanceof String, "Bloque inesperado mientras se espera estado");
            Map<String, Object> message = Json.parse((String)item);
            check(!Json.text(message, "type").equals("ERROR"), item.toString());
            if (Json.text(message, "type").equals("CREDIT_STATUS") && Json.text(message, "state").equals(expectedState)) return message;
        }
    }
    private static void noData(Peer peer) throws Exception {
        long end = System.nanoTime() + 250_000_000L;
        while (System.nanoTime() < end) {
            Object item = peer.received.poll(25, TimeUnit.MILLISECONDS);
            check(!(item instanceof byte[]) && !(item instanceof Throwable), "Datos enviados sin capacidad");
            if (item != null) check(!item.toString().contains("ERROR"), item.toString());
        }
    }

    /** Un canal que acepta todo permite comprobar el límite sin depender de la red real. */
    private static void writeBudget() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(200000);
        java.nio.channels.WritableByteChannel channel = new java.nio.channels.WritableByteChannel() {
            public int write(ByteBuffer source) {
                int bytes = source.remaining(); source.position(source.limit()); return bytes;
            }
            public boolean isOpen() { return true; }
            public void close() { }
        };
        check(PribServer.writeLimited(channel, buffer, 65536)==65536, "Presupuesto estricto");
        check(buffer.position()==65536 && buffer.limit()==200000, "Conservar bytes pendientes");
        check(PribServer.writeLimited(channel, buffer, 19)==19, "Respetar saldo restante del turno");
        java.nio.channels.WritableByteChannel partial = new java.nio.channels.WritableByteChannel() {
            public int write(ByteBuffer source) { source.position(source.position()+7); return 7; }
            public boolean isOpen() { return true; }
            public void close() { }
        };
        check(PribServer.writeLimited(partial, buffer, 100)==7 && buffer.limit()==200000, "Escritura parcial");
        java.nio.channels.WritableByteChannel broken = new java.nio.channels.WritableByteChannel() {
            public int write(ByteBuffer source) throws IOException { throw new IOException("prueba"); }
            public boolean isOpen() { return true; }
            public void close() { }
        };
        try { PribServer.writeLimited(broken, buffer, 100); throw new AssertionError("Falta error"); }
        catch (IOException expected) { check(buffer.limit()==200000, "Restaurar límite tras error"); }
        System.out.println("PASS presupuesto de escritura: estricto, parcial y restauración tras error");
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

    static final class Peer implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder(); final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        final WebSocket socket; String session = ""; long released, grantId, cacheSeq;
        boolean caching; Map<String, byte[]> cached = new HashMap<>(); Map<String, Object> summary;
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
        void hello() throws InterruptedException { hello(true); }
        void hello(boolean initialize) throws InterruptedException {
            send("HELLO", Map.of()); String catalog = next().toString();
            Matcher matcher = Pattern.compile("\"sessionId\":\"([^\"]+)\"").matcher(catalog);
            check(catalog.contains("IMAGE_INFO") && matcher.find(), "Catálogo inicial"); session = matcher.group(1);
            if (initialize) send("CREDIT_INIT", Map.of("capacityBytes", 262144));
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
                    if (Json.text(message, "type").equals("CREDIT_STATUS")) continue;
                    if (Json.integer(message, "viewId") != id) continue;
                    check(session.equals(Json.text(message, "sessionId")), "Sesión de respuesta");
                    if (Json.text(message, "type").equals("VIEW_ACCEPTED")) expected = Json.integer(message, "blocks");
                    if (Json.text(message, "type").equals("VIEW_DONE")) {
                        int actual = ((x+width-1)/128-x/128+1)*((y+height-1)/128-y/128+1);
                        check(expected == actual && seen.size()==actual, "Solo bloques intersectados, sin faltantes"); summary = message; return;
                    }
                } else {
                    released += ((byte[])item).length;
                    send("CREDIT_GRANT", Map.of("grantId", ++grantId, "releasedBytes", released));
                    ByteBuffer data = ByteBuffer.wrap((byte[])item); int size = data.getInt();
                    check(size>0 && size<=4096, "Cabecera binaria limitada"); byte[] header = new byte[size]; data.get(header);
                    Map<String, Object> message = Json.parse(WebSocketFrames.utf8(header));
                    if (Json.integer(message, "viewId") != id) continue;
                    check(session.equals(Json.text(message, "sessionId")) && store.imageId.equals(Json.text(message, "imageId")), "Identidad del bloque");
                    int bx = Json.integer(message, "x"), by = Json.integer(message, "y");
                    check(bx < x+width && by < y+height && bx+128 > x && by+128 > y, "No enviar región ajena");
                    byte[] rgb = new byte[data.remaining()]; data.get(rgb);
                    String mode = Json.text(message, "mode");
                    if (!mode.equals("FULL")) {
                        check(rgb.length == 0, "REF sin payload RGB");
                        rgb = cached.get(Json.text(message, "baseImageId") + "/" + Json.text(message, "baseId"));
                        check(rgb != null, "Base materializada para referencia");
                    }
                    ImageStore.Block block = store.readBlock(level, bx/128, by/128);
                    check(Arrays.equals(rgb, block.rgb()) && block.hash().equals(Json.text(message, "expectedHash")), "RGB y hash exactos");
                    check(seen.add(Json.text(message, "blockId")), "Sin duplicados");
                    if (caching) {
                        String blockId = Json.text(message, "blockId"), key = store.imageId + "/" + blockId;
                        if (!cached.containsKey(key)) {
                            cached.put(key, rgb);
                            send("CACHE_STATE", Map.of("cacheSeq", ++cacheSeq, "operation", "PUT", "imageId", store.imageId,
                                    "blockId", blockId, "width", block.width(), "height", block.height(), "hash", block.hash()));
                        }
                    }
                    send("ACK", Map.of("viewId", id, "transferId", Json.integer(message, "transferId"), "hash", block.hash()));
                }
            }
        }
        public void close() { socket.abort(); }
    }
}
