package prib;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Servidor local HTTP + WebSocket con Selector NIO.
 * El hilo de red nunca lee bloques del disco: esas tareas van a un pool acotado.
 * Para detenerlo desde la terminal, usar Ctrl+C.
 */
public final class PribServer implements AutoCloseable {
    record Prepared(String name, ImageStore store) { }
    final Map<String, Prepared> images = new LinkedHashMap<>();
    private final Map<String, byte[]> assets = new HashMap<>();
    private final Selector selector;
    private final ServerSocketChannel listener;
    private final Set<Client> clients = new HashSet<>();
    private final ConcurrentLinkedQueue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    private volatile boolean running = true;
    private final int port;

    public PribServer(Path data, Path web, int port) throws IOException {
        // Cargar únicamente metadatos y recursos web pequeños antes de aceptar conexiones.
        try (var directories = Files.list(data)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                if (!Files.exists(directory.resolve("image.properties")) || Files.exists(directory.resolve("INCOMPLETE"))) continue;
                try {
                    ImageStore store = new ImageStore(directory);
                    images.put(store.imageId, new Prepared(directory.getFileName().toString(), store));
                } catch (IOException error) { System.err.println("Se omitió almacén inválido: " + directory.getFileName()); }
            }
        }
        if (images.isEmpty()) throw new IOException("No hay imágenes preparadas en " + data);
        if (images.size() > 100) throw new IOException("Máximo 100 imágenes en el catálogo de esta etapa");
        for (String name : List.of("index.html", "app.js", "style.css", "icon.svg")) {
            Path file = web.resolve(name);
            if (Files.size(file) > 524288) throw new IOException("Recurso web demasiado grande");
            assets.put("/" + name, Files.readAllBytes(file));
        }
        selector = Selector.open(); listener = ServerSocketChannel.open();
        try {
            listener.configureBlocking(false); listener.bind(new InetSocketAddress("127.0.0.1", port));
            listener.register(selector, SelectionKey.OP_ACCEPT);
            this.port = ((InetSocketAddress)listener.getLocalAddress()).getPort();
        } catch (IOException error) { listener.close(); selector.close(); workers.shutdownNow(); throw error; }
    }
    public int port() { return port; }
    boolean submit(Runnable work) {
        try { workers.execute(work); return true; }
        catch (RejectedExecutionException full) { return false; }
    }
    void complete(Runnable task) { completions.add(task); selector.wakeup(); }

    public void run() throws IOException {
        System.out.println("PRIB: http://localhost:" + port + " — " + images.size() + " imágenes. Ctrl+C para detener.");
        try {
            while (running) {
                selector.select(100);
                Runnable completion;
                while ((completion = completions.poll()) != null) completion.run();
                var keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next(); keys.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) { accept(); continue; }
                    Client client = (Client)key.attachment();
                    try {
                        if (key.isReadable()) client.read();
                        if (key.isValid() && key.isWritable()) client.write();
                    } catch (IllegalArgumentException invalid) { client.protocolError(invalid.getMessage()); }
                    catch (IOException disconnected) { client.drop(); }
                }
                long now = System.nanoTime();
                for (Client client : List.copyOf(clients)) {
                    // Límites de tiempo para cabeceras incompletas y conexiones que no responden.
                    long timeout = client.websocket ? 60_000_000_000L : 10_000_000_000L;
                    if (now - client.lastRead > timeout || (client.closing && now - client.closeStarted > 2_000_000_000L)) {
                        client.drop(); continue;
                    }
                    if (client.websocket && !client.closing) {
                        if (now - client.lastPing > 20_000_000_000L) {
                            client.enqueue(WebSocketFrames.frame(9, new byte[0])); client.lastPing = now;
                        }
                        client.session.pump();
                    }
                }
            }
        } finally {
            for (Client client : List.copyOf(clients)) client.drop();
            workers.shutdownNow(); listener.close(); selector.close();
        }
    }
    private void accept() throws IOException {
        SocketChannel socket = listener.accept(); if (socket == null) return;
        if (clients.size() >= 32) { socket.close(); return; }
        socket.configureBlocking(false); socket.setOption(StandardSocketOptions.TCP_NODELAY, true);
        SelectionKey key = socket.register(selector, SelectionKey.OP_READ);
        Client client = new Client(socket, key); key.attach(client); clients.add(client);
    }
    public void close() { running = false; selector.wakeup(); }

    final class Client {
        final SocketChannel socket;
        final SelectionKey key;
        final ByteBuffer input = ByteBuffer.allocate(32768);
        final ArrayDeque<ByteBuffer> output = new ArrayDeque<>();
        final WebSocketFrames frames = new WebSocketFrames();
        final PribSession session;
        boolean websocket, closing;
        long lastRead = System.nanoTime(), lastPing = lastRead, closeStarted;
        long rateStart = lastRead; int messages;

        Client(SocketChannel socket, SelectionKey key) {
            this.socket = socket; this.key = key; session = new PribSession(PribServer.this, this);
        }
        void read() throws IOException {
            int count = socket.read(input);
            if (count == -1) { drop(); return; }
            if (count == 0) return;
            lastRead = System.nanoTime(); input.flip();
            try {
                if (!websocket) http();
                if (websocket && !closing) frames.consume(input, (opcode, payload) -> {
                    if (closing) return;
                    long now = System.nanoTime();
                    if (now - rateStart > 1_000_000_000L) { messages = 0; rateStart = now; }
                    if (++messages > 1000) throw new IllegalArgumentException("Demasiados mensajes por segundo");
                    if (opcode == 9) enqueue(WebSocketFrames.frame(10, payload));
                    else if (opcode == 8) { enqueue(WebSocketFrames.frame(8, payload)); markClosing(); }
                    else if (opcode == 1) session.receive(WebSocketFrames.utf8(payload));
                });
            } finally { input.compact(); }
            if (!input.hasRemaining()) throw new IllegalArgumentException("Entrada demasiado grande");
        }
        void http() throws IOException {
            int start = input.position(), end = -1;
            for (int i = start; i + 3 < input.limit(); i++)
                if (input.get(i)==13 && input.get(i+1)==10 && input.get(i+2)==13 && input.get(i+3)==10) { end=i+4; break; }
            if (end == -1) {
                if (input.remaining() > 8192) respond(431, "text/plain", "Cabecera demasiado grande".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (end-start > 8192) throw new IllegalArgumentException("Cabecera demasiado grande");
            byte[] header = new byte[end-start]; input.get(header);
            String[] lines = new String(header, StandardCharsets.US_ASCII).split("\r\n");
            String[] request = lines[0].split(" ");
            if (request.length != 3 || !request[2].equals("HTTP/1.1")) throw new IllegalArgumentException("HTTP inválido");
            Map<String, String> headers = new HashMap<>();
            for (int i=1; i<lines.length; i++) {
                int colon = lines[i].indexOf(':'); if (colon <= 0) throw new IllegalArgumentException("Cabecera inválida");
                String name = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
                if (headers.putIfAbsent(name, lines[i].substring(colon+1).trim()) != null)
                    throw new IllegalArgumentException("Cabecera repetida");
            }
            String host = headers.getOrDefault("host", "").toLowerCase(Locale.ROOT);
            if (!host.equals("localhost:" + port) && !host.equals("127.0.0.1:" + port))
                throw new IllegalArgumentException("Host no admitido");
            if (!request[0].equals("GET")) { respond(405, "text/plain", new byte[0]); return; }
            if (headers.containsKey("transfer-encoding") || !headers.getOrDefault("content-length", "0").equals("0"))
                throw new IllegalArgumentException("GET no admite cuerpo");
            if (request[1].equals("/ws")) {
                String origin = headers.get("origin");
                if (origin != null && !origin.equals("http://" + host)) throw new IllegalArgumentException("Origen no admitido");
                if (!headers.getOrDefault("upgrade", "").equalsIgnoreCase("websocket")
                        || !Arrays.stream(headers.getOrDefault("connection", "").split(",")).anyMatch(v -> v.trim().equalsIgnoreCase("upgrade"))
                        || !headers.getOrDefault("sec-websocket-version", "").equals("13"))
                    throw new IllegalArgumentException("Upgrade WebSocket inválido");
                String keyText = headers.getOrDefault("sec-websocket-key", "");
                if (Base64.getDecoder().decode(keyText).length != 16) throw new IllegalArgumentException("Clave WebSocket inválida");
                String accept;
                try {
                    accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
                            (keyText + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
                } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                enqueue(ByteBuffer.wrap(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                        + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)));
                websocket = true; return;
            }
            String path = request[1].equals("/") ? "/index.html" : request[1];
            byte[] body = assets.get(path);
            String mime = path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
            respond(body == null ? 404 : 200, mime, body == null ? new byte[0] : body);
        }
        void respond(int status, String mime, byte[] body) {
            String headers = "HTTP/1.1 " + status + " Response\r\nContent-Type: " + mime + "; charset=utf-8\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n"
                    + "Content-Security-Policy: default-src 'self'; connect-src 'self' ws://localhost:" + port
                    + " ws://127.0.0.1:" + port + "; object-src 'none'; frame-ancestors 'none'\r\n\r\n";
            enqueue(ByteBuffer.wrap(headers.getBytes(StandardCharsets.US_ASCII))); enqueue(ByteBuffer.wrap(body)); markClosing();
        }
        void text(Map<String, ?> data) { enqueue(WebSocketFrames.frame(1, Json.encode(data).getBytes(StandardCharsets.UTF_8))); }
        void protocolError(String reason) {
            if (!key.isValid() || closing) return;
            if (!websocket) { respond(400, "text/plain", "Petición inválida".getBytes(StandardCharsets.UTF_8)); return; }
            text(Map.of("version", 1, "type", "ERROR", "message", reason == null ? "Error de protocolo" : reason));
            closeFrame(1008);
        }
        void closeFrame(int code) { enqueue(WebSocketFrames.frame(8, new byte[]{(byte)(code>>8), (byte)code})); markClosing(); }
        void markClosing() { closing = true; closeStarted = System.nanoTime(); }
        void enqueue(ByteBuffer buffer) {
            if (!key.isValid()) return;
            int bytes = buffer.remaining(); for (ByteBuffer item : output) bytes += item.remaining();
            if (bytes > 1048576 || output.size() >= 128) { drop(); return; }
            output.add(buffer); key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
        void write() throws IOException {
            // Un máximo por vuelta evita que una conexión monopolice el hilo de red.
            int budget = 65536;
            while (!output.isEmpty() && budget > 0) {
                ByteBuffer buffer = output.peek(); int written = socket.write(buffer); budget -= written;
                if (!buffer.hasRemaining()) output.remove(); else if (written == 0) break;
            }
            if (output.isEmpty()) { if (closing) drop(); else key.interestOps(SelectionKey.OP_READ); }
        }
        void drop() {
            key.cancel(); clients.remove(this); output.clear();
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path data = Path.of(args.length > 1 ? args[1] : "data");
        try (PribServer server = new PribServer(data, Path.of("web"), port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close)); server.run();
        }
    }
}
