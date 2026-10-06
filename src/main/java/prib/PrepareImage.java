package prib;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Prepara una pirámide RGB desde ZIP, subida o URL.
 * Cada nivel mantiene una franja pequeña y entrega filas reducidas al siguiente.
 * PNG no materializa la imagen completa. JPEG/GIF/BMP tienen decodificación acotada.
 */
public final class PrepareImage {
    private static final long DISK_RESERVE = 256L * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("Uso: PrepareImage archivo.zip entrada.png directorio-nuevo");
        if (args[0].equals("--stdin")) prepare(System.in, args[1], Path.of(args[2]), false);
        else if (args[0].equals("--url")) {
            try (InputStream input = PngSource.open(args[1])) { prepare(input, "Imagen desde URL", Path.of(args[2])); }
        } else prepare(Path.of(args[0]), args[1], Path.of(args[2]));
    }

    public static void prepare(Path archive, String entryName, Path output) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) throw new IOException("Imagen no encontrada en el ZIP");
            try (InputStream input = zip.getInputStream(entry)) { prepare(input, entryName, output); }
        }
    }

    static void prepare(InputStream input, String entryName, Path output) throws IOException {
        prepare(input, entryName, output, true);
    }
    private static void prepare(InputStream input, String entryName, Path output, boolean complete) throws IOException {
        long start = System.nanoTime(), peakHeap = 0;
        if (Files.exists(output)) throw new IOException("La carpeta de salida debe ser nueva");
        try (ImageRows png = new ImageRows(input)) {
                Path parent = output.toAbsolutePath().getParent(); Files.createDirectories(parent);
                long worstCase = estimateDisk(png.width, png.height);
                long available = Files.getFileStore(parent).getUsableSpace();
                if (available < worstCase + DISK_RESERVE)
                    throw new IOException("Espacio insuficiente: se requieren hasta " + worstCase + " bytes más reserva");
                Files.createDirectory(output);
                // Un fallo deja evidencia parcial, pero ImageStore impide usarla como imagen lista.
                Files.writeString(output.resolve("INCOMPLETE"), "Preparación en curso. Si falla, repetir en otra carpeta.\n");
                System.out.printf(Locale.ROOT, "Preparando %d x %d; hasta %.2f GiB de salida sin compresión%n",
                        png.width, png.height, worstCase / 1073741824.0);
                Properties info = new Properties();
                int levels = ImageStore.levelCount(png.width, png.height);
                long lastProgress = start;
                try (Level root = new Level(output, 0, png.width, png.height)) {
                    for (int y = 0; y < png.height; y++) {
                        root.add(png.next());
                        long now = System.nanoTime();
                        if (y % ImageStore.BLOCK == 0) {
                            Runtime runtime = Runtime.getRuntime();
                            peakHeap = Math.max(peakHeap, runtime.totalMemory() - runtime.freeMemory());
                        }
                        if (now - lastProgress >= 5_000_000_000L) {
                            System.out.printf(Locale.ROOT, "Filas: %d/%d (%.1f%%), %.1f s%n",
                                    y+1, png.height, 100.0*(y+1)/png.height, (now-start)/1e9);
                            if (Files.getFileStore(output).getUsableSpace() < DISK_RESERVE)
                                throw new IOException("Se alcanzó la reserva de espacio libre");
                            lastProgress = now;
                        }
                    }
                    png.finish(); root.finish(); root.describe(info);
                }
                // Publicar el manifiesto al final: todos los packs e índices ya están cerrados.
                info.setProperty("version", "1"); info.setProperty("imageId", UUID.randomUUID().toString());
                info.setProperty("source", entryName); info.setProperty("sourceFormat", png.sourceFormat); info.setProperty("format", "RGB8");
                info.setProperty("blockSize", Integer.toString(ImageStore.BLOCK));
                info.setProperty("width", Integer.toString(png.width)); info.setProperty("height", Integer.toString(png.height));
                info.setProperty("levels", Integer.toString(levels)); info.setProperty("downsample", "box-2x2-floor");
                try (OutputStream manifest = Files.newOutputStream(output.resolve("image.properties"))) {
                    info.store(manifest, "PRIB image store v1");
                }
                long size;
                try (var files = Files.list(output)) {
                    size = files.filter(p -> !p.getFileName().toString().equals("INCOMPLETE"))
                            .mapToLong(p -> p.toFile().length()).sum();
                }
                String report = String.format(Locale.ROOT,
                        "Imagen: %s%nDimensiones: %d x %d%nNiveles: %d%nSalida medida (packs, índices y manifiesto): %d bytes%nEstimación máxima previa: %d bytes%nTemporal comprimido: PNG 0 bytes; JPEG/GIF/BMP hasta 64 MiB, eliminado al decodificar%nTiempo: %.3f s%nHeap observado: %.2f MiB (muestreo, no RSS ni pico exacto)%n",
                        entryName, png.width, png.height, levels, size, worstCase,
                        (System.nanoTime()-start)/1e9, peakHeap/1048576.0);
                Files.writeString(output.resolve("result.txt"), report);
                if (complete) Files.delete(output.resolve("INCOMPLETE"));
                System.out.print(report);
        }
    }

    /** Cota conservadora: RGB sin compresión más índices y margen de metadatos. */
    static long estimateDisk(int width, int height) {
        long total = 65536;
        while (true) {
            total += (long)width * height * 3;
            total += ((width + 127L)/128) * ((height + 127L)/128) * ImageStore.RECORD_BYTES;
            if (width <= ImageStore.BLOCK && height <= ImageStore.BLOCK) return total;
            width = ImageStore.half(width); height = ImageStore.half(height);
        }
    }

    /** Un nivel recibe filas en orden, guarda bloques y produce la siguiente escala. */
    private static final class Level implements AutoCloseable {
        final int number, width, height;
        final ImageStore.Writer writer;
        final Level next;
        final byte[][] strip = new byte[ImageStore.BLOCK][];
        byte[] pending; // Primera fila del par usado para promediar 2 x 2.
        int count, received;

        Level(Path output, int number, int width, int height) throws IOException {
            this.number = number; this.width = width; this.height = height;
            writer = new ImageStore.Writer(output, number);
            try {
                next = width > ImageStore.BLOCK || height > ImageStore.BLOCK
                        ? new Level(output, number+1, ImageStore.half(width), ImageStore.half(height)) : null;
            } catch (IOException error) { writer.close(); throw error; }
        }
        void add(byte[] row) throws IOException {
            if (row.length != width * 3 || received >= height) throw new IOException("Fila inesperada");
            byte[] copy = row.clone(); // El lector de filas puede reutilizar buffers; cada franja debe conservar los suyos.
            strip[count++] = copy; received++;
            if (next != null) {
                if (pending == null) pending = copy;
                else { next.add(reduce(pending, copy)); pending = null; }
            }
            if (count == strip.length) flush();
        }
        private void flush() throws IOException {
            for (int x = 0; x < width; x += ImageStore.BLOCK) {
                int rowBytes = Math.min(ImageStore.BLOCK, width-x) * 3;
                byte[] block = new byte[rowBytes * count];
                for (int y = 0; y < count; y++)
                    System.arraycopy(strip[y], x*3, block, y*rowBytes, rowBytes);
                writer.write(block);
            }
            Arrays.fill(strip, null); count = 0; // Liberar filas antes de llenar otra franja.
        }
        private byte[] reduce(byte[] top, byte[] bottom) {
            byte[] reduced = new byte[ImageStore.half(width)*3];
            for (int x = 0; x < width; x += 2) {
                int samples = (x+1 < width ? 2 : 1) * (bottom != null ? 2 : 1);
                for (int channel = 0; channel < 3; channel++) {
                    int sum = top[x*3+channel] & 255;
                    if (x+1 < width) sum += top[(x+1)*3+channel] & 255;
                    if (bottom != null) {
                        sum += bottom[x*3+channel] & 255;
                        if (x+1 < width) sum += bottom[(x+1)*3+channel] & 255;
                    }
                    // En bordes impares se promedian solo las muestras existentes.
                    reduced[(x/2)*3+channel] = (byte)(sum/samples);
                }
            }
            return reduced;
        }
        void finish() throws IOException {
            if (received != height) throw new IOException("Nivel incompleto");
            if (count != 0) flush();
            if (next != null) {
                if (pending != null) { next.add(reduce(pending, null)); pending = null; }
                next.finish();
            }
        }
        void describe(Properties info) {
            String prefix = "level." + number + ".";
            info.setProperty(prefix+"width", Integer.toString(width));
            info.setProperty(prefix+"height", Integer.toString(height));
            info.setProperty(prefix+"blocks", Long.toString(writer.blocks));
            info.setProperty(prefix+"bytes", Long.toString(writer.bytes));
            if (next != null) next.describe(info);
        }
        public void close() throws IOException {
            try { writer.close(); } finally { if (next != null) next.close(); }
        }
    }
}
