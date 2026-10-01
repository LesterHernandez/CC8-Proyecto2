package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Prueba pequeña: ZIP → filas RGB → bloques → comparación con ImageIO.
 * ImageIO es una referencia independiente, no el lector usado para generar bloques.
 */
public final class Probe {
    private static final int BLOCK_SIZE = 128;

    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("Uso: Probe archivo.zip entrada.png directorio-salida");
        Path output = Path.of(args[2]);
        if (Files.exists(output)) throw new IOException("La salida debe ser nueva para no sobrescribir resultados");
        long start = System.nanoTime();

        // Dos lecturas de la misma entrada: nuestro lector y el decodificador de referencia.
        try (ZipFile zip = new ZipFile(args[0])) {
            ZipEntry entry = zip.getEntry(args[1]);
            if (entry == null) throw new IOException("Entrada ZIP no encontrada");
            try (PngRows png = new PngRows(zip.getInputStream(entry))) {
                // La referencia sí carga la imagen completa; por eso esta prueba tiene un límite.
                if ((long) png.width * png.height > 10_000_000)
                    throw new IOException("Prueba limitada a 10 millones de píxeles");
                BufferedImage reference;
                try (InputStream input = zip.getInputStream(entry)) { reference = ImageIO.read(input); }
                if (reference == null) throw new IOException("ImageIO no pudo leer la referencia");
                Files.createDirectories(output);
                int count = 0;
                long bytes = 0, peak = 0;
                MessageDigest hash = MessageDigest.getInstance("SHA-256");

                try (BufferedWriter manifest = Files.newBufferedWriter(output.resolve("blocks.csv"))) {
                    manifest.write("id,x,y,width,height,bytes,sha256\n");
                    for (int y = 0; y < png.height; y += BLOCK_SIZE) {
                        int height = Math.min(BLOCK_SIZE, png.height - y);
                        // Una franja contiene hasta 128 filas, independientemente del alto total.
                        byte[][] rows = readStrip(png, height);
                        for (int x = 0; x < png.width; x += BLOCK_SIZE) {
                            int width = Math.min(BLOCK_SIZE, png.width - x);
                            byte[] block = cutBlock(rows, x, width);
                            String id = (x / BLOCK_SIZE) + "_" + (y / BLOCK_SIZE);
                            Path file = output.resolve(id + ".rgb");
                            Files.write(file, block);
                            byte[] saved = Files.readAllBytes(file);
                            if (!Arrays.equals(block, saved)) throw new IOException("Bloque persistido diferente");
                            verifyPixels(saved, reference, x, y, width, height);
                            String digest = HexFormat.of().formatHex(hash.digest(saved));
                            manifest.write(id + "," + x + "," + y + "," + width + "," + height + "," + saved.length + "," + digest + "\n");
                            count++;
                            bytes += saved.length;
                        }
                        // Muestreo orientativo: incluye ImageIO y no equivale al pico real del proceso.
                        Runtime runtime = Runtime.getRuntime();
                        peak = Math.max(peak, runtime.totalMemory() - runtime.freeMemory());
                    }
                }
                png.finish(); // Comprueba también el cierre del PNG y sus CRC pendientes.
                String report = String.format(Locale.ROOT,
                    "Imagen: %s%nDimensiones: %d x %d%nBloque: %d%nBloques: %d%nBytes RGB: %d%nPíxeles: verificados contra ImageIO%nSHA-256: en blocks.csv%nTiempo: %.3f s%nHeap observado máximo: %.2f MiB (incluye ImageIO; no es pico exacto)%nBuffers de filas y franja estimados: %d bytes%n",
                    entry.getName(), png.width, png.height, BLOCK_SIZE, count, bytes,
                    (System.nanoTime() - start) / 1e9, peak / 1048576.0,
                    (long) png.width * 3 * (2 + Math.min(BLOCK_SIZE, png.height)));
                Files.writeString(output.resolve("result.txt"), report);
                System.out.print(report);
            }
        }
    }

    /** Copiamos cada fila porque PngRows reutiliza sus dos buffers internos. */
    private static byte[][] readStrip(PngRows png, int height) throws IOException {
        byte[][] rows = new byte[height][];
        for (int y = 0; y < height; y++) rows[y] = png.next().clone();
        return rows;
    }

    /** Recorta la misma sección de cada fila; los bordes no llevan relleno. */
    private static byte[] cutBlock(byte[][] rows, int x, int width) {
        int rowBytes = width * 3; // Tres bytes por píxel: rojo, verde y azul.
        byte[] block = new byte[rows.length * rowBytes];
        for (int y = 0; y < rows.length; y++)
            System.arraycopy(rows[y], x * 3, block, y * rowBytes, rowBytes);
        return block;
    }

    /** Compara RGB persistido con ImageIO. & 255 interpreta el byte sin signo. */
    private static void verifyPixels(byte[] block, BufferedImage reference,
                                     int x, int y, int width, int height) throws IOException {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int offset = (dy * width + dx) * 3;
                int rgb = ((block[offset] & 255) << 16)
                        | ((block[offset + 1] & 255) << 8) | (block[offset + 2] & 255);
                // getRGB incluye alfa; lo quitamos porque el contrato de bloques es RGB.
                if (rgb != (reference.getRGB(x + dx, y + dy) & 0xffffff))
                    throw new IOException("Píxel diferente en " + (x + dx) + "," + (y + dy));
            }
        }
    }
}
