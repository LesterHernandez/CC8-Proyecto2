package prib.image;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Representación canónica: filas de arriba abajo y canales R, G, B sin relleno. */
public final class RgbBlock {
    private RgbBlock() { }

    public static byte[] pixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgb = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 3)];
        int offset = 0;
        // Leemos muestras, no el arreglo interno: el raster puede almacenar BGR.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int channel = 0; channel < 3; channel++) {
                    rgb[offset++] = (byte) image.getRaster().getSample(x, y, channel);
                }
            }
        }
        return rgb;
    }

    /**
     * Incluir versión, formato y dimensiones impide confundir bloques con los
     * mismos bytes pero distinta geometría. Coordenadas e imageId no participan:
     * contenido idéntico en otra ubicación debe poder reutilizarse mediante REF.
     */
    public static String hash(BufferedImage image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(new byte[] {'P', 'R', 'I', 'B', 1, 3});
            digest.update(ByteBuffer.allocate(8)
                    .putInt(image.getWidth()).putInt(image.getHeight()).array());
            return HexFormat.of().formatHex(digest.digest(pixels(image)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El JDK debe proporcionar SHA-256.", e);
        }
    }
}
