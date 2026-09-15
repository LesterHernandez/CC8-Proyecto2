package servidor.protocol;

import servidor.image.ImagePyramid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa la vista actual de un cliente sobre una imagen.
 *
 * Guarda:
 * - Nivel de zoom.
 * - Desplazamiento horizontal.
 * - Desplazamiento vertical.
 *
 * Tambien permite calcular las teselas visibles
 * para el area actual del navegador.
 */
public class Viewport {

    private final int tileSize;

    private int zoom;
    private int panX;
    private int panY;

    private int viewportWidth;
    private int viewportHeight;

    public Viewport() {

        this(ImagePyramid.TILE_SIZE);
    }

    public Viewport(int tileSize) {

        if (tileSize <= 0) {
            throw new IllegalArgumentException(
                    "El tamano de tesela debe ser mayor que cero."
            );
        }

        this.tileSize = tileSize;

        this.zoom = 0;
        this.panX = 0;
        this.panY = 0;

        this.viewportWidth = 0;
        this.viewportHeight = 0;
    }

    /**
     * Actualiza la posicion de la vista.
     */
    public void update(
            int zoom,
            int panX,
            int panY,
            int viewportWidth,
            int viewportHeight,
            ImagePyramid pyramid) {

        if (pyramid == null) {
            throw new IllegalArgumentException(
                    "La piramide de imagen no puede ser null."
            );
        }

        if (viewportWidth < 0
                || viewportHeight < 0) {

            throw new IllegalArgumentException(
                    "Las dimensiones del viewport no pueden ser negativas."
            );
        }

        if (zoom < 0
                || zoom > pyramid.getMaxZoom()) {

            throw new IllegalArgumentException(
                    "Nivel de zoom invalido: "
                            + zoom
                            + ". Rango permitido: 0-"
                            + pyramid.getMaxZoom()
            );
        }

        this.zoom = zoom;

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;

        this.panX = panX;
        this.panY = panY;

        limitPan(pyramid);
    }

    /**
     * Actualiza solamente zoom y desplazamiento.
     */
    public void update(
            int zoom,
            int panX,
            int panY,
            ImagePyramid pyramid) {

        update(
                zoom,
                panX,
                panY,
                viewportWidth,
                viewportHeight,
                pyramid
        );
    }

    /**
     * Actualiza las dimensiones del area visible.
     */
    public void setViewportSize(
            int width,
            int height,
            ImagePyramid pyramid) {

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "Las dimensiones no pueden ser negativas."
            );
        }

        this.viewportWidth = width;
        this.viewportHeight = height;

        if (pyramid != null) {
            limitPan(pyramid);
        }
    }

    /**
     * Limita el desplazamiento para evitar
     * salir completamente de la imagen.
     */
    public void limitPan(
            ImagePyramid pyramid) {

        if (pyramid == null) {
            return;
        }

        ImagePyramid.Dimensions dimensions =
                pyramid.getDimensions(zoom);

        int imageWidth =
                dimensions.getWidth();

        int imageHeight =
                dimensions.getHeight();

        /*
         * Si la imagen es mas grande que el viewport,
         * se permite desplazar hasta su borde.
         */
        if (viewportWidth > 0
                && imageWidth > viewportWidth) {

            int minPanX =
                    -(imageWidth - viewportWidth);

            panX = Math.min(
                    0,
                    Math.max(
                            panX,
                            minPanX
                    )
            );

        } else {

            /*
             * Si la imagen cabe completamente,
             * no permitimos desplazamiento horizontal.
             */
            panX = 0;
        }

        if (viewportHeight > 0
                && imageHeight > viewportHeight) {

            int minPanY =
                    -(imageHeight - viewportHeight);

            panY = Math.min(
                    0,
                    Math.max(
                            panY,
                            minPanY
                    )
            );

        } else {

            /*
             * Si la imagen cabe completamente,
             * no permitimos desplazamiento vertical.
             */
            panY = 0;
        }
    }

    /**
     * Obtiene las teselas visibles actualmente.
     */
    public List<TileCoordinate> getVisibleTiles(
            ImagePyramid pyramid) {

        if (pyramid == null) {
            return Collections.emptyList();
        }

        if (viewportWidth <= 0
                || viewportHeight <= 0) {

            return Collections.emptyList();
        }

        limitPan(pyramid);

        ImagePyramid.Dimensions dimensions =
                pyramid.getDimensions(zoom);

        int columns =
                pyramid.getTileColumns(zoom);

        int rows =
                pyramid.getTileRows(zoom);

        /*
         * Coordenada de la primera tesela visible.
         */
        int startX =
                floorDiv(
                        -panX,
                        tileSize
                );

        int startY =
                floorDiv(
                        -panY,
                        tileSize
                );

        /*
         * Coordenada de la ultima tesela visible.
         */
        int endX =
                floorDiv(
                        -panX + viewportWidth - 1,
                        tileSize
                );

        int endY =
                floorDiv(
                        -panY + viewportHeight - 1,
                        tileSize
                );

        startX = Math.max(
                0,
                startX
        );

        startY = Math.max(
                0,
                startY
        );

        endX = Math.min(
                columns - 1,
                endX
        );

        endY = Math.min(
                rows - 1,
                endY
        );

        if (startX > endX
                || startY > endY) {

            return Collections.emptyList();
        }

        List<TileCoordinate> visibleTiles =
                new ArrayList<>();

        for (
                int x = startX;
                x <= endX;
                x++
        ) {

            for (
                    int y = startY;
                    y <= endY;
                    y++
            ) {

                visibleTiles.add(
                        new TileCoordinate(
                                zoom,
                                x,
                                y
                        )
                );
            }
        }

        return visibleTiles;
    }

    /**
     * Obtiene las claves de las teselas visibles.
     *
     * Ejemplo:
     *
     * 3_2_1
     * 3_3_1
     * 3_2_2
     */
    public List<String> getVisibleTileKeys(
            ImagePyramid pyramid) {

        List<TileCoordinate> coordinates =
                getVisibleTiles(pyramid);

        List<String> keys =
                new ArrayList<>();

        for (TileCoordinate coordinate
                : coordinates) {

            keys.add(
                    coordinate.toKey()
            );
        }

        return keys;
    }

    /**
     * Comprueba si una tesela esta dentro
     * del viewport actual.
     */
    public boolean isTileVisible(
            int tileX,
            int tileY,
            ImagePyramid pyramid) {

        if (pyramid == null) {
            return false;
        }

        List<TileCoordinate> tiles =
                getVisibleTiles(pyramid);

        for (TileCoordinate tile : tiles) {

            if (tile.getX() == tileX
                    && tile.getY() == tileY) {

                return true;
            }
        }

        return false;
    }

    public int getZoom() {
        return zoom;
    }

    public int getPanX() {
        return panX;
    }

    public int getPanY() {
        return panY;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public int getTileSize() {
        return tileSize;
    }

    /**
     * Reinicia la vista.
     */
    public void reset(
            ImagePyramid pyramid) {

        zoom = 0;
        panX = 0;
        panY = 0;

        limitPan(pyramid);
    }

    /**
     * Division entera que funciona correctamente
     * tambien con valores negativos.
     */
    private int floorDiv(
            int value,
            int divisor) {

        return Math.floorDiv(
                value,
                divisor
        );
    }

    /**
     * Representa una coordenada de tesela.
     */
    public static class TileCoordinate {

        private final int zoom;
        private final int x;
        private final int y;

        public TileCoordinate(
                int zoom,
                int x,
                int y) {

            this.zoom = zoom;
            this.x = x;
            this.y = y;
        }

        public int getZoom() {
            return zoom;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        /**
         * Convierte la coordenada al formato
         * utilizado por el protocolo.
         *
         * Ejemplo:
         * 3_2_1
         */
        public String toKey() {

            return zoom
                    + "_"
                    + x
                    + "_"
                    + y;
        }

        /**
         * Obtiene la URL de la tesela.
         */
        public String toUrl(
                ImagePyramid pyramid) {

            if (pyramid == null) {
                return null;
            }

            return pyramid.getTileUrl(
                    zoom,
                    x,
                    y
            );
        }

        @Override
        public String toString() {

            return toKey();
        }
    }

    @Override
    public String toString() {

        return "Viewport{"
                + "zoom=" + zoom
                + ", panX=" + panX
                + ", panY=" + panY
                + ", viewportWidth="
                + viewportWidth
                + ", viewportHeight="
                + viewportHeight
                + ", tileSize="
                + tileSize
                + '}';
    }
}