package servidor.image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TileGenerator {

    private static final int TILE_SIZE = 256;

    /**
     * Genera una piramide de imagenes dividida en teselas.
     *
     * Estructura generada:
     *
     * outputDir/
     *   imageId/
     *     0/
     *       0_0.jpg
     *       0_1.jpg
     *       ...
     *     1/
     *       0_0.jpg
     *       0_1.jpg
     *       ...
     *     ...
     *     maxZoom/
     *       ...
     */
    public static void generatePyramid(
            String imageId,
            File inputFile,
            File outputDir) throws IOException {

        if (imageId == null || imageId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador de la imagen no puede estar vacio."
            );
        }

        if (inputFile == null || !inputFile.isFile()) {
            throw new IllegalArgumentException(
                    "El archivo de imagen no existe: " + inputFile
            );
        }

        if (outputDir == null) {
            throw new IllegalArgumentException(
                    "El directorio de salida no puede ser null."
            );
        }

        BufferedImage originalImage = ImageIO.read(inputFile);

        if (originalImage == null) {
            throw new IllegalArgumentException(
                    "El archivo no es una imagen compatible: "
                            + inputFile.getAbsolutePath()
            );
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        int maxDimension = Math.max(
                originalWidth,
                originalHeight
        );

        int maxZoom = calculateMaxZoom(maxDimension);

        System.out.println();
        System.out.println("==========================================");
        System.out.println("       GENERACION DE PIRAMIDE");
        System.out.println("==========================================");
        System.out.println("Imagen: " + imageId);
        System.out.println(
                "Dimensiones originales: "
                        + originalWidth
                        + "x"
                        + originalHeight
        );
        System.out.println("Tile size: " + TILE_SIZE + "x" + TILE_SIZE);
        System.out.println("Max zoom: " + maxZoom);
        System.out.println();

        File imageOutputDir = new File(
                outputDir,
                imageId
        );

        if (!imageOutputDir.exists()
                && !imageOutputDir.mkdirs()) {

            throw new IOException(
                    "No se pudo crear el directorio: "
                            + imageOutputDir.getAbsolutePath()
            );
        }

        /*
         * Generamos primero el nivel de mayor resolucion
         * y despues vamos reduciendo la imagen.
         */
        for (int z = maxZoom; z >= 0; z--) {

            double scale = Math.pow(
                    0.5,
                    maxZoom - z
            );

            int levelWidth = Math.max(
                    1,
                    (int) Math.ceil(originalWidth * scale)
            );

            int levelHeight = Math.max(
                    1,
                    (int) Math.ceil(originalHeight * scale)
            );

            System.out.println(
                    "[TileGenerator] Generando Z="
                            + z
                            + " -> "
                            + levelWidth
                            + "x"
                            + levelHeight
            );

            BufferedImage levelImage;

            if (levelWidth == originalWidth
                    && levelHeight == originalHeight) {

                levelImage = originalImage;

            } else {

                levelImage = resizeImage(
                        originalImage,
                        levelWidth,
                        levelHeight
                );
            }

            saveTilesForLevel(
                    imageId,
                    z,
                    levelImage,
                    imageOutputDir
            );

            System.out.println(
                    "[TileGenerator] Nivel Z="
                            + z
                            + " terminado."
            );
        }

        System.out.println();
        System.out.println(
                "[TileGenerator] Piramide generada correctamente."
        );
        System.out.println(
                "[TileGenerator] Directorio: "
                        + imageOutputDir.getAbsolutePath()
        );
        System.out.println();
    }

    /**
     * Calcula el nivel maximo de zoom necesario
     * para que la dimension mayor de la imagen
     * quede cubierta por teselas de 256 px.
     */
    private static int calculateMaxZoom(int maxDimension) {

        if (maxDimension <= TILE_SIZE) {
            return 0;
        }

        return (int) Math.ceil(
                Math.log(
                        maxDimension / (double) TILE_SIZE
                ) / Math.log(2)
        );
    }

    /**
     * Reduce o amplia una imagen al tamano indicado.
     */
    private static BufferedImage resizeImage(
            BufferedImage original,
            int targetWidth,
            int targetHeight) {

        BufferedImage resized = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = resized.createGraphics();

        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );

        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
        );

        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        graphics.drawImage(
                original,
                0,
                0,
                targetWidth,
                targetHeight,
                null
        );

        graphics.dispose();

        return resized;
    }

    /**
     * Divide un nivel de la piramide en teselas de 256x256.
     */
    private static void saveTilesForLevel(
            String imageId,
            int zoom,
            BufferedImage image,
            File imageOutputDir) throws IOException {

        int columns = (int) Math.ceil(
                image.getWidth() / (double) TILE_SIZE
        );

        int rows = (int) Math.ceil(
                image.getHeight() / (double) TILE_SIZE
        );

        File levelDir = new File(
                imageOutputDir,
                String.valueOf(zoom)
        );

        if (!levelDir.exists()
                && !levelDir.mkdirs()) {

            throw new IOException(
                    "No se pudo crear el directorio: "
                            + levelDir.getAbsolutePath()
            );
        }

        int generatedTiles = 0;

        for (int x = 0; x < columns; x++) {

            for (int y = 0; y < rows; y++) {

                int cropX = x * TILE_SIZE;
                int cropY = y * TILE_SIZE;

                int tileWidth = Math.min(
                        TILE_SIZE,
                        image.getWidth() - cropX
                );

                int tileHeight = Math.min(
                        TILE_SIZE,
                        image.getHeight() - cropY
                );

                BufferedImage tile = image.getSubimage(
                        cropX,
                        cropY,
                        tileWidth,
                        tileHeight
                );

                File tileFile = new File(
                        levelDir,
                        x + "_" + y + ".jpg"
                );

                ImageIO.write(
                        tile,
                        "jpg",
                        tileFile
                );

                generatedTiles++;
            }
        }

        System.out.println(
                "    Teselas generadas: "
                        + generatedTiles
                        + " ("
                        + columns
                        + " columnas x "
                        + rows
                        + " filas)"
        );
    }
}