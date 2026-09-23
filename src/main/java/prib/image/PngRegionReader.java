package prib.image;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

/**
 * Prueba de lectura regional para los PNG RGB8 no entrelazados del curso.
 * No es todavía el almacén de bloques: PNG exige recorrer datos anteriores
 * para llegar a una región. La etapa 2 evitará repetir ese recorrido.
 */
public final class PngRegionReader {
    // Acotamos tanto el resultado como el ancho que determina los buffers de filas.
    public static final int MAX_REGION_SIDE = 512;
    private static final int MAX_SOURCE_WIDTH = 1_000_000;
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public record Info(int width, int height) {
        public long rgbBytes() {
            return (long) width * height * 3;
        }
    }

    /** Lee únicamente la cabecera, sin reservar memoria para los píxeles. */
    public Info inspect(Path source) throws IOException {
        try (var input = new FileImageInputStream(source.toFile())) {
            return header(input);
        }
    }

    /**
     * El stream respaldado por archivo evita acumular el PNG en una caché RAM.
     * ImageReadParam limita la imagen destino a la región solicitada.
     */
    public BufferedImage read(Path source, int x, int y, int width, int height)
            throws IOException {
        try (var input = new FileImageInputStream(source.toFile())) {
            Info info = header(input);
            if (x < 0 || y < 0 || width < 1 || height < 1
                    || width > MAX_REGION_SIDE || height > MAX_REGION_SIDE
                    || (long) x + width > info.width() || (long) y + height > info.height()) {
                throw new IllegalArgumentException("Región fuera de la imagen o mayor que 512 x 512.");
            }
            input.seek(0);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("No hay lector PNG disponible en este JDK.");
            }
            ImageReader reader = readers.next();
            try {
                // Ignoramos metadatos ajenos a esta prueba; preservamos las muestras RGB.
                reader.setInput(input, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(new Rectangle(x, y, width, height));
                return reader.read(0, param);
            } finally {
                // ImageReader no implementa AutoCloseable y necesita liberación explícita.
                reader.dispose();
            }
        }
    }

    private static Info header(FileImageInputStream input) throws IOException {
        byte[] signature = new byte[8];
        input.readFully(signature);
        if (!Arrays.equals(signature, SIGNATURE)
                || input.readInt() != 13 || input.readInt() != 0x49484452) {
            throw new IOException("Firma PNG o cabecera IHDR inválida.");
        }
        int width = input.readInt();
        int height = input.readInt();
        int depth = input.readUnsignedByte();
        int color = input.readUnsignedByte();
        int compression = input.readUnsignedByte();
        int filter = input.readUnsignedByte();
        int interlace = input.readUnsignedByte();
        if (width < 1 || height < 1 || width > MAX_SOURCE_WIDTH) {
            throw new IOException("Dimensiones inválidas o ancho superior al límite del lector.");
        }
        if (depth != 8 || color != 2 || compression != 0 || filter != 0 || interlace != 0) {
            throw new IOException("Etapa 1 admite únicamente PNG RGB8 no entrelazado.");
        }
        return new Info(width, height);
    }
}
