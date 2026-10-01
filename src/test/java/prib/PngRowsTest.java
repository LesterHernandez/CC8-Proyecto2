package prib;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.*;

/** Pruebas autónomas, sin dependencias: filtros PNG, bloques de borde y errores. */
public final class PngRowsTest {
    public static void main(String[] args) throws Exception {
        // Cada ejecución conserva su evidencia en build/, fuera del control de versiones.
        Path directory = Files.createTempDirectory(Path.of("build"), "png-test-");
        byte[] valid = createPng();
        check(directory, "filters", valid, null);
        byte[] corrupt = valid.clone();
        corrupt[29] ^= 1; // Alterar solo el CRC de IHDR, sin modificar los píxeles.
        check(directory, "crc", corrupt, "CRC de IHDR");
        check(directory, "truncated", Arrays.copyOf(valid, valid.length - 8), "EOF");
    }

    /** Ejecutar la misma prueba de bloques que usamos con las imágenes reales. */
    private static void check(Path directory, String name, byte[] png, String expected) throws Exception {
        Path archive = directory.resolve(name + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("test.png"));
            zip.write(png);
            zip.closeEntry();
        }
        try {
            Probe.main(new String[]{archive.toString(), "test.png", directory.resolve(name).toString()});
        } catch (IOException error) {
            // No basta con fallar: debe tratarse de la causa que introdujo esta prueba.
            boolean matches = expected != null && (expected.equals("EOF")
                    ? error instanceof EOFException
                    : error.getMessage() != null && error.getMessage().contains(expected));
            if (!matches) throw error;
            System.out.println("PASS " + name);
            return;
        }
        if (expected != null) throw new AssertionError("Se aceptó un PNG inválido: " + name);
        System.out.println("PASS " + name);
    }

    /** Imagen determinista con bordes parciales y una fila de cada filtro en rotación. */
    private static byte[] createPng() throws IOException {
        int width = 259, height = 131;
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] previous = new byte[width * 3];
        for (int y = 0; y < height; y++) {
            byte[] row = new byte[width * 3];
            for (int x = 0; x < row.length; x++) row[x] = (byte)(x * 17 + y * 31 + x * y);
            int filter = y % 5;
            raw.write(filter);
            for (int x = 0; x < row.length; x++) {
                int left = x >= 3 ? row[x - 3] & 255 : 0;
                int above = previous[x] & 255;
                int diagonal = x >= 3 ? previous[x - 3] & 255 : 0;
                int prediction = switch (filter) {
                    case 0 -> 0;
                    case 1 -> left;
                    case 2 -> above;
                    case 3 -> (left + above) / 2;
                    default -> paeth(left, above, diagonal);
                };
                // Codificar resta; el lector deberá recuperar los bytes sumando el predictor.
                raw.write((row[x] & 255) - prediction);
            }
            previous = row;
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(compressed)) { raw.writeTo(zlib); }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(result);
        output.writeLong(0x89504e470d0a1a0aL);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream fields = new DataOutputStream(header);
        fields.writeInt(width); fields.writeInt(height);
        fields.write(new byte[]{8, 2, 0, 0, 0}); // RGB8, compresión estándar, sin entrelazado.
        writeChunk(output, "IHDR", header.toByteArray());
        byte[] data = compressed.toByteArray();
        // Dividir zlib obliga al lector a continuar entre varios chunks IDAT.
        for (int i = 0; i < data.length; i += 113)
            writeChunk(output, "IDAT", Arrays.copyOfRange(data, i, Math.min(i + 113, data.length)));
        writeChunk(output, "IEND", new byte[0]);
        return result.toByteArray();
    }

    /** Predictor usado para fabricar el caso de prueba, independiente del lector. */
    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int da = Math.abs(p - a), db = Math.abs(p - b), dc = Math.abs(p - c);
        return da <= db && da <= dc ? a : db <= dc ? b : c;
    }

    /** Un chunk contiene longitud, tipo, datos y CRC del tipo junto con los datos. */
    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(data);
        out.writeInt(data.length); out.write(name); out.write(data); out.writeInt((int)crc.getValue());
    }
}
