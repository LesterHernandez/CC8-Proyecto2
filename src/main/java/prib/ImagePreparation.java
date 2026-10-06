package prib;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;
import java.util.zip.*;

/** Preparación local: una tarea por servidor, independiente de las sesiones del visor.
 * El proceso hijo reutiliza PrepareImage con heap propio; no carga el ZIP en RAM.
 */
final class ImagePreparation implements AutoCloseable {
    private final Path archives, data;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Process process;
    private volatile Map<String, Object> status = Map.of("state", "IDLE", "percent", 0, "message", "Sin preparación activa");
    private boolean busy, closed;

    ImagePreparation(Path archives, Path data) { this.archives = archives.toAbsolutePath().normalize(); this.data = data.toAbsolutePath().normalize(); }
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
            List<String> names = zip.stream().filter(e -> !e.isDirectory() && e.getName().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .map(ZipEntry::getName).limit(1001).toList();
            if (names.size() > 1000 || names.stream().mapToInt(String::length).sum() > 60000)
                throw new IOException("Demasiadas entradas PNG para el selector");
            return names;
        }
    }

    synchronized void start(String zip, String entry, String name, Consumer<PribServer.Prepared> publish) {
        if (closed) throw new IllegalArgumentException("Servidor cerrándose");
        if (busy) throw new IllegalArgumentException("Ya hay una preparación activa; espera a que termine");
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}") || name.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])"))
            throw new IllegalArgumentException("Nombre: 1 a 64 letras, números, guiones o guion bajo; sin rutas");
        busy = true; status = Map.of("state", "RUNNING", "percent", 0, "name", name, "message", "Validando imagen y espacio disponible…");
        worker.execute(() -> {
            try {
                Path source = archive(zip);
                if (!entry.toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("Selecciona una entrada PNG");
                Files.createDirectories(data);
                Path output = data.toRealPath().resolve(name);
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("La carpeta de destino ya existe; elige otro nombre");
                String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
                if (!Files.exists(Path.of(java))) java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                synchronized (this) {
                    if (closed) throw new IOException("Preparación detenida al cerrar el servidor");
                    process = new ProcessBuilder(java, "-Xmx256m", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp",
                            System.getProperty("java.class.path"), "prib.PrepareImage", source.toString(), entry, output.toString())
                            .redirectErrorStream(true).start();
                }
                Pattern progress = Pattern.compile("Filas: .*\\(([0-9.]+)%\\).*" );
                String problem = "No se pudo preparar la imagen";
                try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher match = progress.matcher(line);
                        if (match.matches()) status = Map.of("state", "RUNNING", "name", name,
                                "percent", Math.min(99, (int)Double.parseDouble(match.group(1))), "message", line);
                        if (line.startsWith("Exception in thread")) problem = line.contains("EOFException")
                                ? "Archivo truncado o no válido" : line.substring(line.indexOf(":") + 1).trim();
                    }
                }
                if (process.waitFor() != 0) throw new IOException("No se pudo preparar el PNG: " + problem + ". Si quedó una carpeta incompleta, usa otro nombre.");
                ImageStore store = new ImageStore(output);
                // Publicar en el Selector antes de anunciar la finalización a las sesiones.
                publish.accept(new PribServer.Prepared(name, store));
                status = Map.of("state", "DONE", "name", name, "percent", 100, "message", "Imagen preparada: " + name);
            } catch (Exception error) {
                status = Map.of("state", "FAILED", "name", name, "percent", 0,
                        "message", error.getMessage() == null ? "No se pudo preparar la imagen" : error.getMessage());
            } finally { synchronized (this) { busy = false; process = null; } }
        });
    }
    public synchronized void close() {
        closed = true;
        if (process != null) process.destroy();
        worker.shutdownNow();
    }
}
