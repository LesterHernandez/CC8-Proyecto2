package prib;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import prib.image.PngRegionReader;
import prib.image.RgbBlock;

/** Pruebas sin librerías externas: un fallo termina el proceso con error. */
public final class StageOneTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("prib-stage1-");
        try {
            // El patrón conocido permite verificar píxeles sin usar otro recorte del lector.
            BufferedImage source = new BufferedImage(73, 59, BufferedImage.TYPE_3BYTE_BGR);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    source.setRGB(x, y, color(x, y));
                }
            }
            Path png = directory.resolve("pattern.png");
            ImageIO.write(source, "PNG", png.toFile());
            var reader = new PngRegionReader();
            check(reader.inspect(png).rgbBytes() == 73L * 59 * 3, "Dimensiones y tamaño RGB");
            verifyRegion(reader, png, 0, 0, 16, 16);
            verifyRegion(reader, png, 19, 23, 31, 29);
            verifyRegion(reader, png, 65, 52, 8, 7);
            verifyRegion(reader, png, 72, 58, 1, 1);
            verifyRegion(reader, png, 0, 0, 73, 59);

            expectFailure(() -> reader.read(png, -1, 0, 1, 1), "Origen negativo");
            expectFailure(() -> reader.read(png, 0, 0, 0, 1), "Dimensión cero");
            expectFailure(() -> reader.read(png, 72, 58, 2, 2), "Región fuera del borde");
            expectFailure(() -> reader.read(png, Integer.MAX_VALUE, 0, 2, 2), "Desbordamiento de coordenadas");
            expectFailure(() -> reader.read(png, 0, 0, 513, 1), "Límite de región");

            Path gray = directory.resolve("gray.png");
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY), "PNG", gray.toFile());
            expectFailure(() -> reader.inspect(gray), "Formato fuera del alcance");
            Path invalid = directory.resolve("invalid.png");
            Files.writeString(invalid, "No es un PNG");
            expectFailure(() -> reader.inspect(invalid), "Firma inválida");
            Path truncated = directory.resolve("truncated.png");
            Files.write(truncated, Arrays.copyOf(Files.readAllBytes(png), 48));
            expectFailure(() -> reader.read(truncated, 0, 0, 10, 10), "Datos truncados");

            // Mismos bytes con dimensiones distintas deben tener hashes diferentes.
            var horizontal = new BufferedImage(2, 1, BufferedImage.TYPE_3BYTE_BGR);
            var vertical = new BufferedImage(1, 2, BufferedImage.TYPE_3BYTE_BGR);
            horizontal.setRGB(0, 0, 0x123456);
            horizontal.setRGB(1, 0, 0xabcdef);
            vertical.setRGB(0, 0, 0x123456);
            vertical.setRGB(0, 1, 0xabcdef);
            check(Arrays.equals(RgbBlock.pixels(horizontal), new byte[] {
                    0x12, 0x34, 0x56, (byte) 0xab, (byte) 0xcd, (byte) 0xef}), "Orden RGB canónico");
            check(!RgbBlock.hash(horizontal).equals(RgbBlock.hash(vertical)), "Hash incluye dimensiones");
            // Vector calculado independientemente con hashlib para fijar el contrato binario.
            check(RgbBlock.hash(horizontal).equals("47ccd9bc324f88f9d0453e73793191d9ef73bbdac3c220c299bbbb39c99c00cd"), "Hash independiente");
            System.out.println("OK: " + checks + " comprobaciones de etapa 1.");
        } finally {
            // Solo borramos los archivos creados por esta prueba, en su carpeta temporal.
            try (var paths = Files.list(directory)) {
                for (Path path : paths.toList()) {
                    Files.delete(path);
                }
            }
            Files.delete(directory);
        }
    }

    private static int color(int x, int y) {
        return ((x * 7 + y * 3) & 255) << 16
                | ((x * 11 + y * 13) & 255) << 8 | ((x * 17 + y * 19) & 255);
    }

    private static void verifyRegion(PngRegionReader reader, Path png, int x, int y, int w, int h)
            throws IOException {
        var region = reader.read(png, x, y, w, h);
        check(region.getWidth() == w && region.getHeight() == h, "Dimensiones de región");
        for (int row = 0; row < h; row++) {
            for (int column = 0; column < w; column++) {
                if ((region.getRGB(column, row) & 0xffffff) != color(x + column, y + row)) {
                    throw new AssertionError("Píxel incorrecto en " + column + "," + row);
                }
            }
        }
        check(true, "Todos los píxeles de la región son exactos");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }

    @FunctionalInterface
    private interface Operation { void run() throws IOException; }

    private static void expectFailure(Operation operation, String message) throws IOException {
        try {
            operation.run();
        } catch (IOException | IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Se esperaba rechazar: " + message);
    }
}
