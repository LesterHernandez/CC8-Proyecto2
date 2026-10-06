package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Firma real del archivo. PNG conserva su lector incremental para imágenes enormes.
 * JPEG/GIF/BMP usan ImageIO con límites previos y un temporal comprimido acotado.
 */
final class ImageRows implements AutoCloseable {
    static final long MAX_ENCODED = 64L * 1024 * 1024, MAX_PIXELS = 16_000_000;
    final int width, height;
    final String sourceFormat;
    private PngRows png;
    private BufferedImage image;
    private Path temporary;
    private int row;
    static boolean supportedName(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|bmp)");
    }
    ImageRows(InputStream input) throws IOException {
        PushbackInputStream source = new PushbackInputStream(input, 8);
        byte[] signature = source.readNBytes(8); source.unread(signature);
        if (Arrays.equals(signature, new byte[]{(byte)137,80,78,71,13,10,26,10})) {
            png = new PngRows(source); width = png.width; height = png.height; sourceFormat = "PNG"; return;
        }
        if (signature.length >= 3 && (signature[0]&255)==255 && (signature[1]&255)==216 && (signature[2]&255)==255) sourceFormat="JPEG";
        else if (signature.length >= 6 && signature[0]=='G' && signature[1]=='I' && signature[2]=='F' && signature[3]=='8' && (signature[4]=='7'||signature[4]=='9') && signature[5]=='a') sourceFormat="GIF";
        else if (signature.length >= 2 && signature[0]=='B' && signature[1]=='M') sourceFormat="BMP";
        else throw new IOException("Firma no admitida: usa PNG, JPEG, GIF o BMP");
        ImageReader reader = null;
        try {
            temporary = Files.createTempFile("prib-image-", ".encoded");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = source.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ENCODED) throw new IOException("JPEG/GIF/BMP: máximo 64 MiB de archivo comprimido; usa PNG RGB8 para imágenes grandes");
                    out.write(buffer,0,count);
                }
            }
            try (ImageInputStream stream = ImageIO.createImageInputStream(temporary.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IOException("Imagen " + sourceFormat + " no válida");
                reader = readers.next(); reader.setInput(stream, true, true);
                width = reader.getWidth(0); height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 32768 || height > 32768 || (long)width*height > MAX_PIXELS)
                    throw new IOException("JPEG/GIF/BMP: máximo 16 millones de píxeles y 32768 por lado; usa PNG RGB8 para imágenes grandes");
                boolean[] warning = {false}; reader.addIIOReadWarningListener((r,message)->warning[0]=true);
                image = reader.read(0);
                if (image == null || warning[0]) throw new IOException("Imagen " + sourceFormat + " truncada o dañada");
            }
        } catch (IOException | RuntimeException e) { close(); throw e; }
        finally { if (reader != null) reader.dispose(); if (temporary != null) { Files.deleteIfExists(temporary); temporary=null; } }
    }
    byte[] next() throws IOException {
        if (png != null) return png.next();
        if (row >= height) throw new EOFException("No quedan filas");
        byte[] rgb = new byte[width*3];
        for (int x=0; x<width; x++) {
            int color=image.getRGB(x,row), alpha=color>>>24;
            for (int channel=0; channel<3; channel++) {
                int component=(color>>>(16-channel*8))&255;
                rgb[x*3+channel]=(byte)((component*alpha+255*(255-alpha)+127)/255);
            }
        }
        row++; return rgb;
    }
    void finish() throws IOException { if (png != null) png.finish(); else if (row != height) throw new IOException("Filas incompletas"); }
    public void close() throws IOException {
        if (png != null) png.close();
        if (image != null) { image.flush(); image=null; }
        if (temporary != null) { Files.deleteIfExists(temporary); temporary=null; }
    }
}
