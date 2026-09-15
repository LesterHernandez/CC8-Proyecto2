package servidor.image;

import java.io.File;

/**
 * Representa una imagen almacenada como una piramide
 * de diferentes niveles de resolucion.
 *
 * Cada nivel contiene teselas de 256x256 pixeles.
 *
 * Estructura:
 *
 * storage/tiles/
 *   imagen1/
 *     0/
 *       0_0.jpg
 *     1/
 *       0_0.jpg
 *       1_0.jpg
 *     2/
 *       ...
 */
public class ImagePyramid {

    public static final int TILE_SIZE = 256;

    private final String imageId;
    private final File pyramidDirectory;

    private final int originalWidth;
    private final int originalHeight;
    private final int maxZoom;

    /**
     * Crea una representacion de una piramide de imagen.
     *
     * @param imageId identificador de la imagen
     * @param pyramidDirectory directorio donde se almacenan las teselas
     * @param originalWidth ancho original de la imagen
     * @param originalHeight alto original de la imagen
     * @param maxZoom nivel maximo de zoom
     */
    public ImagePyramid(
            String imageId,
            File pyramidDirectory,
            int originalWidth,
            int originalHeight,
            int maxZoom) {

        if (imageId == null || imageId.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador de la imagen no puede estar vacio."
            );
        }

        if (pyramidDirectory == null) {
            throw new IllegalArgumentException(
                    "El directorio de la piramide no puede ser null."
            );
        }

        if (originalWidth <= 0 || originalHeight <= 0) {
            throw new IllegalArgumentException(
                    "Las dimensiones de la imagen deben ser mayores que cero."
            );
        }

        if (maxZoom < 0) {
            throw new IllegalArgumentException(
                    "El nivel maximo de zoom no puede ser negativo."
            );
        }

        this.imageId = imageId;
        this.pyramidDirectory = pyramidDirectory;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.maxZoom = maxZoom;
    }

    /**
     * Devuelve el identificador de la imagen.
     */
    public String getImageId() {
        return imageId;
    }

    /**
     * Devuelve el ancho de la imagen original.
     */
    public int getOriginalWidth() {
        return originalWidth;
    }

    /**
     * Devuelve el alto de la imagen original.
     */
    public int getOriginalHeight() {
        return originalHeight;
    }

    /**
     * Devuelve el nivel maximo de zoom.
     */
    public int getMaxZoom() {
        return maxZoom;
    }

    /**
     * Devuelve las dimensiones de la imagen
     * para un nivel determinado de zoom.
     *
     * El nivel maxZoom representa la resolucion original.
     *
     * Cada nivel inferior reduce la resolucion a la mitad.
     *
     * Ejemplo:
     *
     * maxZoom = 4
     *
     * Z=4 -> 4000x3000
     * Z=3 -> 2000x1500
     * Z=2 -> 1000x750
     * Z=1 -> 500x375
     * Z=0 -> 250x188
     */
    public Dimensions getDimensions(int zoom) {

        validateZoom(zoom);

        double scale = Math.pow(
                2,
                zoom - maxZoom
        );

        int width = Math.max(
                1,
                (int) Math.ceil(
                        originalWidth * scale
                )
        );

        int height = Math.max(
                1,
                (int) Math.ceil(
                        originalHeight * scale
                )
        );

        return new Dimensions(
                width,
                height
        );
    }

    /**
     * Calcula la cantidad de columnas de teselas
     * necesarias para un nivel de zoom.
     */
    public int getTileColumns(int zoom) {

        Dimensions dimensions =
                getDimensions(zoom);

        return (int) Math.ceil(
                dimensions.width / (double) TILE_SIZE
        );
    }

    /**
     * Calcula la cantidad de filas de teselas
     * necesarias para un nivel de zoom.
     */
    public int getTileRows(int zoom) {

        Dimensions dimensions =
                getDimensions(zoom);

        return (int) Math.ceil(
                dimensions.height / (double) TILE_SIZE
        );
    }

    /**
     * Comprueba si una coordenada de tesela
     * pertenece a un nivel valido de la piramide.
     */
    public boolean isValidTile(
            int zoom,
            int x,
            int y) {

        if (zoom < 0 || zoom > maxZoom) {
            return false;
        }

        if (x < 0 || y < 0) {
            return false;
        }

        return x < getTileColumns(zoom)
                && y < getTileRows(zoom);
    }

    /**
     * Devuelve el archivo correspondiente
     * a una tesela.
     *
     * Ejemplo:
     *
     * zoom = 3
     * x = 4
     * y = 2
     *
     * Resultado:
     *
     * storage/tiles/imagen1/3/4_2.jpg
     */
    public File getTileFile(
            int zoom,
            int x,
            int y) {

        if (!isValidTile(zoom, x, y)) {
            return null;
        }

        File zoomDirectory = new File(
                pyramidDirectory,
                String.valueOf(zoom)
        );

        return new File(
                zoomDirectory,
                x + "_" + y + ".jpg"
        );
    }

    /**
     * Comprueba si una tesela existe fisicamente
     * en el servidor.
     */
    public boolean tileExists(
            int zoom,
            int x,
            int y) {

        File tileFile = getTileFile(
                zoom,
                x,
                y
        );

        return tileFile != null
                && tileFile.isFile();
    }

    /**
     * Devuelve la ruta relativa que puede utilizar
     * el cliente para solicitar una tesela.
     *
     * Ejemplo:
     *
     * /tiles/imagen1/3/4_2.jpg
     */
    public String getTileUrl(
            int zoom,
            int x,
            int y) {

        if (!isValidTile(zoom, x, y)) {
            return null;
        }

        return "/tiles/"
                + imageId
                + "/"
                + zoom
                + "/"
                + x
                + "_"
                + y
                + ".jpg";
    }

    /**
     * Comprueba que el directorio principal
     * de la piramide exista.
     */
    public boolean exists() {

        return pyramidDirectory.isDirectory();
    }

    /**
     * Devuelve informacion general de la piramide.
     */
    public String getInfo() {

        return "ImagePyramid{"
                + "imageId='" + imageId + '\''
                + ", width=" + originalWidth
                + ", height=" + originalHeight
                + ", maxZoom=" + maxZoom
                + ", tileSize=" + TILE_SIZE
                + '}';
    }

    /**
     * Verifica que el nivel de zoom sea valido.
     */
    private void validateZoom(int zoom) {

        if (zoom < 0 || zoom > maxZoom) {

            throw new IllegalArgumentException(
                    "Nivel de zoom invalido: "
                            + zoom
                            + ". Rango permitido: 0-"
                            + maxZoom
            );
        }
    }

    /**
     * Clase auxiliar para representar
     * las dimensiones de un nivel.
     */
    public static class Dimensions {

        private final int width;
        private final int height;

        public Dimensions(
                int width,
                int height) {

            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        @Override
        public String toString() {

            return width
                    + "x"
                    + height;
        }
    }
}