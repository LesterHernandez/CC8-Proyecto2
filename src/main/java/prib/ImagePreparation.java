package prib;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;
import java.util.zip.*;

/** Una preparación por servidor. ZIP/URL sobreviven al cierre de la pestaña;
 * la subida local necesita su sesión hasta recibir el último fragmento.
 * El proceso hijo reutiliza PrepareImage con heap propio; no carga el ZIP en RAM.
 */
final class ImagePreparation implements AutoCloseable {
    private final Path archives, data;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Process process;
    private volatile Map<String, Object> status = Map.of("state", "IDLE", "percent", 0, "message", "Sin preparación activa");
    private boolean busy, closed;

    ImagePreparation(Path archives, Path data) {
        this.archives = archives.toAbsolutePath().normalize();
        this.data = data.toAbsolutePath().normalize();
    }
    Map<String, Object> status() { return status; }

    // Solo archivos directos de imagenes/. No aceptamos rutas arbitrarias ni enlaces exteriores.
    private Path archive(String name) throws IOException {
        if (name.isBlank() || name.contains("/") || name.contains("\\") || !name.toLowerCase(Locale.ROOT).endsWith(".zip"))
            throw new IOException("Selecciona un ZIP de imagenes/");
        Path root = archives.toRealPath(), file = root.resolve(name).toRealPath();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) throw new IOException("ZIP fuera de imagenes/");
        return file;
    }
    List<String> archives() throws IOException {
        if (!Files.exists(archives)) return List.of();
        try (var files = Files.list(archives)) {
            List<String> names = files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted().limit(101).toList();
            if (names.size() > 100) throw new IOException("Máximo 100 ZIP en imagenes/");
            return names;
        }
    }
    List<String> entries(String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive(name).toFile())) {
            List<String> names = zip.stream().filter(e -> !e.isDirectory() && ImageRows.supportedName(e.getName()))
                    .map(ZipEntry::getName).limit(1001).toList();
            if (names.size() > 1000 || names.stream().mapToInt(String::length).sum() > 60000)
                throw new IOException("Demasiadas entradas de imagen para el selector");
            return names;
        }
    }

    // Escribir al proceso puede bloquear: nunca hacerlo en el Selector de red.
    private final ExecutorService inputWorker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private Upload upload;
    private static final class Upload {
        final String owner;
        final long size;
        final CompletableFuture<Void> ended = new CompletableFuture<>();
        long offset, lastActivity = System.nanoTime();
        boolean writing, ending;
        OutputStream input;
        Upload(String owner, long size) { this.owner = owner; this.size = size; }
    }
    private void validateName(String name) {
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}") || name.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])"))
            throw new IllegalArgumentException("Nombre: 1 a 64 letras, números, guiones o guion bajo; sin rutas");
    }
    synchronized void start(String zip, String entry, String name, Consumer<PribServer.Prepared> publish) {
        begin("ZIP", zip, entry, name, null, publish, null);
    }
    synchronized void startUrl(String url, String name, Consumer<PribServer.Prepared> publish) {
        try { PngSource.validate(url); } catch (IOException e) { throw new IllegalArgumentException(e.getMessage()); }
        begin("URL", url, "", name, null, publish, null);
    }
    synchronized void startUpload(String owner, long size, String name, Consumer<PribServer.Prepared> publish, Consumer<Long> ready) {
        if (size < 33 || size > CreditWindow.MAX_COUNTER) throw new IllegalArgumentException("Archivo de imagen vacío o tamaño inválido");
        begin("UPLOAD", "", "", name, new Upload(owner, size), publish, ready);
    }
    private synchronized void begin(String mode, String sourceName, String entry, String name, Upload incoming,
                                    Consumer<PribServer.Prepared> publish, Consumer<Long> ready) {
        if (closed) throw new IllegalArgumentException("Servidor cerrándose");
        if (busy) throw new IllegalArgumentException("Ya hay una preparación activa; espera a que termine");
        validateName(name);
        busy = true; upload = incoming;
        status = Map.of("state", "RUNNING", "percent", 0, "name", name, "message", "Validando imagen y espacio disponible…");
        if (incoming != null) watchdog.schedule(() -> checkUpload(incoming), 5, TimeUnit.SECONDS);
        worker.execute(() -> {
            Process child = null;
            try {
                Files.createDirectories(data);
                Path output = data.toRealPath().resolve(name);
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("La carpeta de destino ya existe; elige otro nombre");
                String source, option;
                if (mode.equals("ZIP")) {
                    source = archive(sourceName).toString(); option = entry;
                    if (!ImageRows.supportedName(entry)) throw new IOException("Selecciona una entrada de imagen");
                } else {
                    source = mode.equals("UPLOAD") ? "--stdin" : "--url";
                    option = mode.equals("UPLOAD") ? "imagen local" : sourceName;
                }
                String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
                if (!Files.exists(Path.of(java))) java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                synchronized (this) {
                    if (closed || incoming != null && incoming.ended.isCompletedExceptionally()) throw new IOException("Preparación detenida");
                    child = new ProcessBuilder(java, "-Xmx256m", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                            "-Dprib.allowLocalImageUrls=" + Boolean.getBoolean("prib.allowLocalImageUrls"), "-cp",
                            System.getProperty("java.class.path"), "prib.PrepareImage", source, option, output.toString()).redirectErrorStream(true).start();
                    process = child;
                    if (incoming != null) incoming.input = child.getOutputStream();
                }
                if (ready != null) ready.accept(0L);
                Pattern progress = Pattern.compile("Filas: .*\\(([0-9.]+)%\\).*" );
                String problem = "No se pudo preparar la imagen";
                try (BufferedReader reader = child.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher match = progress.matcher(line);
                        if (match.matches()) status = Map.of("state", "RUNNING", "name", name,
                                "percent", Math.min(99, (int)Double.parseDouble(match.group(1))), "message", line);
                        if (line.startsWith("Exception in thread")) problem = line.contains("EOFException")
                                ? "Archivo truncado o no válido" : line.substring(line.indexOf(":") + 1).trim();
                    }
                }
                if (child.waitFor() != 0) throw new IOException("No se pudo preparar la imagen: " + problem + ". Si quedó una carpeta incompleta, usa otro nombre.");
                // Decodificar un PNG no basta: confirmar también que llegó toda la subida.
                // INCOMPLETE impide que un almacén parcial aparezca al reiniciar el servidor.
                if (incoming != null) {
                    incoming.ended.get(60, TimeUnit.SECONDS);
                    Files.delete(output.resolve("INCOMPLETE"));
                }
                ImageStore store = new ImageStore(output);
                publish.accept(new PribServer.Prepared(name, store));
                status = Map.of("state", "DONE", "name", name, "percent", 100, "message", "Imagen preparada: " + name);
            } catch (Exception error) {
                status = Map.of("state", "FAILED", "name", name, "percent", 0,
                        "message", error.getMessage() == null ? "No se pudo preparar la imagen" : error.getMessage());
            } finally {
                if (child != null) child.destroy();
                synchronized (this) { busy = false; process = null; if (upload == incoming) upload = null; }
            }
        });
    }
    private synchronized Upload owned(String owner) {
        if (upload == null || !upload.owner.equals(owner)) throw new IllegalArgumentException("No hay una subida de imagen activa en esta sesión");
        return upload;
    }
    synchronized void chunk(String owner, long offset, String encoded, Consumer<Long> acknowledged) {
        Upload item = owned(owner);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Fragmento de imagen inválido"); }
        if (item.writing || item.ending || item.input == null || offset != item.offset || bytes.length < 1 || bytes.length > 4096 || bytes.length > item.size-item.offset)
            throw new IllegalArgumentException("Fragmento fuera de secuencia; espera la confirmación");
        item.writing = true; item.lastActivity = System.nanoTime();
        inputWorker.execute(() -> {
            try {
                item.input.write(bytes);
                item.input.flush();
                long next;
                synchronized (this) {
                    item.offset += bytes.length;
                    next = item.offset;
                    item.writing = false;
                    item.lastActivity = System.nanoTime();
                }
                // ACK confirma escritura, no solo recepción: aplica presión al navegador.
                acknowledged.accept(next);
            } catch (IOException error) { abortUpload(owner); }
        });
    }
    synchronized void endUpload(String owner) {
        Upload item = owned(owner);
        if (item.writing || item.ending || item.offset != item.size) throw new IllegalArgumentException("La subida de imagen está incompleta");
        item.ending = true;
        inputWorker.execute(() -> {
            try { item.input.close(); item.ended.complete(null); }
            catch (IOException e) { item.ended.completeExceptionally(e); }
        });
    }
    synchronized void abortUpload(String owner) {
        if (upload == null || !upload.owner.equals(owner) || upload.ending) return;
        upload.ended.completeExceptionally(new IOException("Subida imagen interrumpida"));
        if (process != null) process.destroy();
    }
    private synchronized void checkUpload(Upload item) {
        // Comparar la identidad evita que un temporizador antiguo cancele la siguiente tarea.
        if (upload != item || item.ending || closed) return;
        if (System.nanoTime()-item.lastActivity > 60_000_000_000L) abortUpload(item.owner);
        else watchdog.schedule(() -> checkUpload(item), 5, TimeUnit.SECONDS);
    }
    public synchronized void close() {
        closed = true;
        if (process != null) process.destroy();
        if (upload != null) upload.ended.completeExceptionally(new IOException("Servidor detenido"));
        inputWorker.shutdownNow(); watchdog.shutdownNow(); worker.shutdownNow();
    }
}
