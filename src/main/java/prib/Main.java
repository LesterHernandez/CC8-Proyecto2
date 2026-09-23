package prib;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import prib.image.PngRegionReader;
import prib.image.RgbBlock;

/** Entrada de diagnóstico de la etapa 1; aún no inicia un servidor de red. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        try {
            execute(args);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void execute(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("help")) {
            System.out.println("PRIB | etapa 1 | Java 21");
            System.out.println("inspect <imagen.png>");
            System.out.println("region <imagen.png> <x> <y> <ancho> <alto> <salida.png>");
            return;
        }
        var reader = new PngRegionReader();
        if (args[0].equals("inspect") && args.length == 2) {
            var info = reader.inspect(Path.of(args[1]));
            System.out.printf("PNG RGB8: %d x %d | RGB completo: %d bytes%n",
                    info.width(), info.height(), info.rgbBytes());
        } else if (args[0].equals("region") && args.length == 7) {
            Path source = Path.of(args[1]);
            Path output = Path.of(args[6]).toAbsolutePath().normalize();
            // La prueba nunca debe sobrescribir el original ni resultados anteriores.
            if (Files.exists(output)) {
                throw new IllegalArgumentException("La salida ya existe: " + output);
            }
            long start = System.nanoTime();
            BufferedImage block = reader.read(source, Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            Files.createDirectories(output.getParent());
            if (!ImageIO.write(block, "PNG", output.toFile())) {
                throw new IOException("No hay escritor PNG disponible.");
            }
            System.out.printf("Región: %d x %d | lectura: %.3f s%n",
                    block.getWidth(), block.getHeight(), seconds);
            System.out.println("SHA-256 PRIB: " + RgbBlock.hash(block));
            // Este valor es el límite del heap, no una medición de RAM total del proceso.
            System.out.println("Límite heap JVM: " + Runtime.getRuntime().maxMemory() + " bytes");
            System.out.println("Salida: " + output);
        } else {
            throw new IllegalArgumentException("Argumentos inválidos. Ejecuta help para ver el uso.");
        }
    }
}
