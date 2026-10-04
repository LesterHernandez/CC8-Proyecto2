package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Almacén local: dos archivos por nivel, sin cargar el índice completo en RAM.
 * Nivel 0 conserva la resolución original. Cada registro del índice ocupa 48 bytes.
 */
public final class ImageStore {
    public static final int BLOCK = 128;
    static final int RECORD_BYTES = 48;
    private final Path directory;
    private final Properties metadata = new Properties();
    public final int width, height, levels;
    public final String imageId;

    public ImageStore(Path directory) throws IOException {
        this.directory = directory;
        if (Files.exists(directory.resolve("INCOMPLETE")))
            throw new IOException("Preparación incompleta; no se puede consultar esta imagen");
        try (InputStream input = Files.newInputStream(directory.resolve("image.properties"))) {
            metadata.load(input);
        }
        if (!"1".equals(metadata.getProperty("version")) || !"RGB8".equals(metadata.getProperty("format")))
            throw new IOException("Formato de almacén no compatible");
        width = number("width"); height = number("height"); levels = number("levels");
        imageId = metadata.getProperty("imageId");
        if (width <= 0 || width > 200000 || height <= 0 || levels != levelCount(width, height)
                || number("blockSize") != BLOCK || imageId == null)
            throw new IOException("Metadatos de imagen inválidos");
    }

    private int number(String key) throws IOException {
        try { return Integer.parseInt(metadata.getProperty(key)); }
        catch (RuntimeException error) { throw new IOException("Metadato inválido: " + key, error); }
    }

    public int width(int level) { return dimension(width, level); }
    public int height(int level) { return dimension(height, level); }
    private int dimension(int size, int level) {
        if (level < 0 || level >= levels) throw new IllegalArgumentException("Nivel inexistente");
        for (int i = 0; i < level; i++) size = half(size);
        return size;
    }
    static int half(int value) { return value / 2 + value % 2; }
    static int levelCount(int width, int height) {
        int count = 1;
        while (width > BLOCK || height > BLOCK) { width = half(width); height = half(height); count++; }
        return count;
    }
    static Path dataPath(Path directory, int level) { return directory.resolve("level-" + level + ".pack"); }
    static Path indexPath(Path directory, int level) { return directory.resolve("level-" + level + ".idx"); }
    static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    /** RGB canónico: sin cabecera ni alfa. Geometría y hash identifican su contenido. */
    public record Block(int width, int height, byte[] rgb, String hash) { }

    /** Metadatos de un único bloque; no carga píxeles ni el índice completo. */
    public record Descriptor(int width, int height, long offset, int size, int codec, String hash) { }

    public Descriptor describe(int level, int column, int row) throws IOException {
        int w = width(level), h = height(level);
        int columns = (w - 1) / BLOCK + 1, rows = (h - 1) / BLOCK + 1;
        if (column < 0 || column >= columns || row < 0 || row >= rows)
            throw new IllegalArgumentException("Bloque fuera de la imagen");
        int bw = Math.min(BLOCK, w - column * BLOCK), bh = Math.min(BLOCK, h - row * BLOCK);
        int rawLength = bw * bh * 3;
        try (RandomAccessFile index = new RandomAccessFile(indexPath(directory, level).toFile(), "r")) {
            if (index.length() != (long) columns * rows * RECORD_BYTES)
                throw new IOException("Índice incompleto o de tamaño incorrecto");
            index.seek(((long) row * columns + column) * RECORD_BYTES);
            long offset = index.readLong();
            int size = index.readInt(), codec = index.readInt();
            byte[] hash = new byte[32]; index.readFully(hash);
            if (offset < 0 || size <= 0 || size > rawLength
                    || (codec != 0 && codec != 1) || (codec == 0 && size != rawLength))
                throw new IOException("Registro de bloque inválido");
            return new Descriptor(bw, bh, offset, size, codec, HexFormat.of().formatHex(hash));
        }
    }

    public Block readBlock(int level, int column, int row) throws IOException {
        Descriptor info = describe(level, column, row);
        try (RandomAccessFile pack = new RandomAccessFile(dataPath(directory, level).toFile(), "r")) {
            if (info.offset() > pack.length() - info.size()) throw new IOException("Pack incompleto");
            byte[] encoded = new byte[info.size()]; pack.seek(info.offset()); pack.readFully(encoded);
            byte[] rgb = info.codec() == 0 ? encoded : inflate(encoded, info.width() * info.height() * 3);
            if (!info.hash().equals(HexFormat.of().formatHex(sha256(rgb)))) throw new IOException("SHA-256 incorrecto");
            return new Block(info.width(), info.height(), rgb, info.hash());
        }
    }

    /** La salida está limitada por geometría, incluso si el contenido comprimido está dañado. */
    private static byte[] inflate(byte[] encoded, int length) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(encoded);
            byte[] rgb = new byte[length]; int total = 0;
            while (total < length) {
                int n = inflater.inflate(rgb, total, length - total);
                if (n == 0) break;
                total += n;
            }
            if (total != length || !inflater.finished() || inflater.getRemaining() != 0)
                throw new IOException("Longitud o cierre zlib incorrecto");
            return rgb;
        } catch (DataFormatException error) { throw new IOException("Bloque zlib dañado", error); }
        finally { inflater.end(); }
    }

    /** Consulta una región acotada y lee exclusivamente los bloques que la intersectan. */
    public BufferedImage region(int level, int x, int y, int w, int h) throws IOException {
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || (long)x + w > width(level)
                || (long)y + h > height(level) || (long)w * h > 4_194_304)
            throw new IllegalArgumentException("Región inválida o mayor de 4 millones de píxeles");
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int by = y / BLOCK; by <= (y + h - 1) / BLOCK; by++) {
            for (int bx = x / BLOCK; bx <= (x + w - 1) / BLOCK; bx++) {
                Block block = readBlock(level, bx, by);
                int left = Math.max(x, bx * BLOCK), top = Math.max(y, by * BLOCK);
                int right = Math.min(x + w, bx * BLOCK + block.width);
                int bottom = Math.min(y + h, by * BLOCK + block.height);
                for (int py = top; py < bottom; py++) for (int px = left; px < right; px++) {
                    int i = ((py - by * BLOCK) * block.width + px - bx * BLOCK) * 3;
                    int rgb = ((block.rgb[i] & 255) << 16) | ((block.rgb[i+1] & 255) << 8) | (block.rgb[i+2] & 255);
                    image.setRGB(px - x, py - y, rgb);
                }
            }
        }
        return image;
    }

    /** Escritor secuencial de un nivel. Se usa zlib solo cuando reduce bytes. */
    static final class Writer implements AutoCloseable {
        private final OutputStream pack;
        private final DataOutputStream index;
        private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        private final byte[] compressed = new byte[BLOCK * BLOCK * 3 + 256];
        long bytes, blocks;

        Writer(Path directory, int level) throws IOException {
            pack = new BufferedOutputStream(Files.newOutputStream(dataPath(directory, level)), 65536);
            try { index = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(indexPath(directory, level)))); }
            catch (IOException error) { pack.close(); deflater.end(); throw error; }
        }
        void write(byte[] rgb) throws IOException {
            deflater.reset(); deflater.setInput(rgb); deflater.finish();
            int length = deflater.deflate(compressed);
            boolean useZlib = deflater.finished() && length < rgb.length;
            byte[] source = useZlib ? compressed : rgb;
            int size = useZlib ? length : rgb.length;
            index.writeLong(bytes); index.writeInt(size); index.writeInt(useZlib ? 1 : 0);
            index.write(sha256(rgb)); pack.write(source, 0, size);
            bytes += size; blocks++;
        }
        public void close() throws IOException {
            try { pack.close(); } finally { try { index.close(); } finally { deflater.end(); } }
        }
    }
}
