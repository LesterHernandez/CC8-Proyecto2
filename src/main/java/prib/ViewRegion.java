package prib;

import java.nio.file.*;
import javax.imageio.ImageIO;

/** Exporta una región del almacén a PNG para inspección manual, sin abrir el ZIP. */
public final class ViewRegion {
    public static void main(String[] args) throws Exception {
        if (args.length != 7)
            throw new IllegalArgumentException("Uso: ViewRegion almacén nivel x y ancho alto salida.png");
        Path output = Path.of(args[6]);
        if (Files.exists(output)) throw new IllegalArgumentException("La salida debe ser nueva");
        ImageStore store = new ImageStore(Path.of(args[0]));
        int level = Integer.parseInt(args[1]);
        var image = store.region(level, Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]));
        Files.createDirectories(output.toAbsolutePath().getParent());
        ImageIO.write(image, "png", output.toFile());
        System.out.println("Región verificada y exportada: " + output);
    }
}
