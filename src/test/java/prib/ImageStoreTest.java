package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Pruebas del almacén con referencias pequeñas independientes del preprocesador. */
public final class ImageStoreTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            ImageStore store = new ImageStore(Path.of(args[0]));
            for (int level = 0; level < store.levels; level++) {
                int lastColumn = (store.width(level)-1)/ImageStore.BLOCK;
                int lastRow = (store.height(level)-1)/ImageStore.BLOCK;
                // Leer esquinas y centro en orden no secuencial: cada llamada verifica SHA-256.
                store.readBlock(level, lastColumn, lastRow);
                store.readBlock(level, 0, 0);
                store.readBlock(level, lastColumn/2, lastRow/2);
                store.readBlock(level, 0, lastRow);
                store.readBlock(level, lastColumn, 0);
            }
            System.out.println("PASS muestras de esquinas y centro en " + store.levels + " niveles");
            return;
        }
        // Modo adicional: contrastar un almacén real contra ImageIO y todos sus niveles.
        if (args.length == 3) {
            try (ZipFile zip = new ZipFile(args[0]); InputStream input = zip.getInputStream(zip.getEntry(args[1]))) {
                verify(new ImageStore(Path.of(args[2])), ImageIO.read(input));
            }
            System.out.println("PASS niveles reales y regiones"); return;
        }
        Path root = Files.createTempDirectory(Path.of("build"), "store-test-");
        int[][] sizes = {{259, 131}, {128, 128}, {1, 259}, {259, 1}, {1, 1}, {129, 129}};
        Path last = null;
        for (int[] size : sizes) {
            BufferedImage original = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            Random random = new Random(123);
            for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++)
                original.setRGB(x, y, size[0] == 129 ? random.nextInt(1 << 24) : ((x/9 + y/7) % 2 == 0 ? 0xffffff : 0));
            ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(original, "png", png);
            Path zipPath = root.resolve(size[0]+"x"+size[1]+".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                zip.putNextEntry(new ZipEntry("test.png")); zip.write(png.toByteArray()); zip.closeEntry();
            }
            last = root.resolve(size[0]+"x"+size[1]);
            PrepareImage.prepare(zipPath, "test.png", last);
            ImageStore store = new ImageStore(last); verify(store, original);
            try { PrepareImage.prepare(zipPath, "test.png", last); throw new AssertionError("Sobrescribió el almacén"); }
            catch (IOException expected) { if (!expected.getMessage().contains("nueva")) throw expected; }
            System.out.println("PASS pirámide " + size[0] + "x" + size[1]);
        }
        // Alterar un bloque comprimido debe fallar antes de devolver píxeles al consumidor.
        Path compressedStore = root.resolve("259x131");
        try (RandomAccessFile idx = new RandomAccessFile(ImageStore.indexPath(compressedStore, 0).toFile(), "r")) {
            idx.seek(12); if (idx.readInt() != 1) throw new AssertionError("Falta caso zlib");
        }
        try (RandomAccessFile pack = new RandomAccessFile(ImageStore.dataPath(compressedStore, 0).toFile(), "rw")) {
            pack.writeByte(0); // Romper la cabecera zlib sin modificar el índice.
        }
        try { new ImageStore(compressedStore).readBlock(0, 0, 0); throw new AssertionError("Aceptó zlib dañado"); }
        catch (IOException expected) { if (!expected.getMessage().contains("zlib")) throw expected; }

        // Un PNG truncado durante preparación debe dejar la salida marcada como incompleta.
        Path badZip = root.resolve("bad.zip"), partial = root.resolve("partial");
        try (ZipFile source = new ZipFile(root.resolve("259x131.zip").toFile());
             InputStream input = source.getInputStream(source.getEntry("test.png"));
             ZipOutputStream target = new ZipOutputStream(Files.newOutputStream(badZip))) {
            byte[] png = input.readAllBytes();
            target.putNextEntry(new ZipEntry("test.png")); target.write(png, 0, png.length - 8); target.closeEntry();
        }
        try { PrepareImage.prepare(badZip, "test.png", partial); throw new AssertionError("Aceptó PNG incompleto"); }
        catch (EOFException expected) { }
        if (!Files.exists(partial.resolve("INCOMPLETE"))) throw new AssertionError("Falta marca incompleta");
        try { new ImageStore(partial); throw new AssertionError("Aceptó salida interrumpida"); }
        catch (IOException expected) { if (!expected.getMessage().contains("incompleta")) throw expected; }
        ImageStore store = new ImageStore(last);
        try { store.region(0, 128, 128, 2, 2); throw new AssertionError("Aceptó región exterior"); }
        catch (IllegalArgumentException expected) { }
        try { store.readBlock(0, -1, 0); throw new AssertionError("Aceptó índice negativo"); }
        catch (IllegalArgumentException expected) { }

        // El ruido determinista no se comprime: asegura que también probamos codec RAW.
        try (RandomAccessFile idx = new RandomAccessFile(ImageStore.indexPath(last, 0).toFile(), "r")) {
            idx.seek(12); if (idx.readInt() != 0) throw new AssertionError("Falta caso RAW");
        }
        try (RandomAccessFile pack = new RandomAccessFile(ImageStore.dataPath(last, 0).toFile(), "rw")) {
            int value = pack.readUnsignedByte(); pack.seek(0); pack.writeByte(value ^ 1);
        }
        try { store.readBlock(0, 0, 0); throw new AssertionError("Aceptó bloque alterado"); }
        catch (IOException expected) { if (!expected.getMessage().contains("SHA-256")) throw expected; }
        Files.writeString(last.resolve("INCOMPLETE"), "prueba");
        try { new ImageStore(last); throw new AssertionError("Aceptó preparación incompleta"); }
        catch (IOException expected) { if (!expected.getMessage().contains("incompleta")) throw expected; }
        System.out.println("PASS límites, RAW/zlib, corrupción, salida existente y preparación interrumpida");
    }

    private static void verify(ImageStore store, BufferedImage reference) throws IOException {
        for (int level = 0; level < store.levels; level++) {
            if (store.width(level) != reference.getWidth() || store.height(level) != reference.getHeight())
                throw new AssertionError("Dimensiones distintas");
            // region() cruza todos los bloques, lee el índice y verifica hash de cada lectura.
            BufferedImage actual = store.region(level, 0, 0, reference.getWidth(), reference.getHeight());
            compare(actual, reference, 0, 0);
            int x = Math.min(125, reference.getWidth()-1), y = Math.min(125, reference.getHeight()-1);
            int w = Math.min(11, reference.getWidth()-x), h = Math.min(11, reference.getHeight()-y);
            compare(store.region(level, x, y, w, h), reference, x, y);
            if (level + 1 < store.levels) reference = downsample(reference);
        }
    }

    private static void compare(BufferedImage actual, BufferedImage expected, int x, int y) {
        for (int dy = 0; dy < actual.getHeight(); dy++) for (int dx = 0; dx < actual.getWidth(); dx++)
            if (actual.getRGB(dx, dy) != expected.getRGB(x+dx, y+dy))
                throw new AssertionError("Píxel distinto en " + (x+dx) + "," + (y+dy));
    }

    /** Referencia por coordenadas de imagen, independiente de las franjas del preprocesador. */
    private static BufferedImage downsample(BufferedImage source) {
        BufferedImage result = new BufferedImage((source.getWidth()+1)/2, (source.getHeight()+1)/2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < result.getHeight(); y++) for (int x = 0; x < result.getWidth(); x++) {
            int rgb = 0;
            for (int shift : new int[]{16, 8, 0}) {
                int sum = 0, count = 0;
                for (int sy = y*2; sy < Math.min(y*2+2, source.getHeight()); sy++)
                    for (int sx = x*2; sx < Math.min(x*2+2, source.getWidth()); sx++) {
                        sum += (source.getRGB(sx, sy) >> shift) & 255; count++;
                    }
                rgb |= (sum/count) << shift;
            }
            result.setRGB(x, y, rgb);
        }
        return result;
    }
}
