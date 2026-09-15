class ViewportManager {


constructor(tileSize = 256) {

    this.tileSize = tileSize;

    // Nivel de zoom actual.
    this.zoom = 0;

    // Se actualizara cuando el servidor
    // proporcione la informacion de la imagen.
    this.maxZoom = 0;

    // Desplazamiento de la imagen.
    this.panX = 0;
    this.panY = 0;

    // Dimensiones de la imagen en el nivel
    // de mayor resolucion.
    this.imageWidth = 0;
    this.imageHeight = 0;

}

/*
 * Configura la informacion de la imagen.
 */
setImageInfo(width, height, maxZoom) {

    this.imageWidth = width;
    this.imageHeight = height;

    this.maxZoom = Math.max(0, maxZoom);

    // Asegurar que el zoom actual este
    // dentro del rango permitido.
    if (this.zoom > this.maxZoom) {

        this.zoom = this.maxZoom;

    }

    this.limitPan();

}

/*
 * Cambia el nivel de zoom.
 */
setZoom(zoom) {

    this.zoom = Math.max(
        0,
        Math.min(zoom, this.maxZoom)
    );

    this.limitPan();

}

/*
 * Cambia la posicion de desplazamiento.
 */
setPan(panX, panY) {

    this.panX = panX;
    this.panY = panY;

    this.limitPan();

}

/*
 * Obtiene las dimensiones de la imagen
 * para el nivel de zoom actual.
 */
getLevelDimensions() {

    if (this.imageWidth <= 0 || this.imageHeight <= 0) {

        return {
            width: 0,
            height: 0
        };

    }

    const scale = Math.pow(
        2,
        this.zoom - this.maxZoom
    );

    return {
        width: Math.max(
            1,
            Math.ceil(this.imageWidth * scale)
        ),

        height: Math.max(
            1,
            Math.ceil(this.imageHeight * scale)
        )
    };

}

/*
 * Calcula la cantidad de teselas necesarias
 * para cubrir la imagen en el nivel actual.
 */
getTileCount() {

    const dimensions = this.getLevelDimensions();

    return {
        columns: Math.ceil(
            dimensions.width / this.tileSize
        ),

        rows: Math.ceil(
            dimensions.height / this.tileSize
        )
    };

}

/*
 * Calcula las teselas que actualmente
 * son visibles dentro del viewport.
 */
getVisibleTiles(viewportWidth, viewportHeight) {

    const tileCount = this.getTileCount();

    if (
        tileCount.columns <= 0 ||
        tileCount.rows <= 0
    ) {

        return [];

    }

    const startX = Math.floor(
        -this.panX / this.tileSize
    );

    const startY = Math.floor(
        -this.panY / this.tileSize
    );

    const endX = Math.floor(
        (-this.panX + viewportWidth) /
        this.tileSize
    );

    const endY = Math.floor(
        (-this.panY + viewportHeight) /
        this.tileSize
    );

    const firstX = Math.max(0, startX);
    const firstY = Math.max(0, startY);

    const lastX = Math.min(
        tileCount.columns - 1,
        endX
    );

    const lastY = Math.min(
        tileCount.rows - 1,
        endY
    );

    const tiles = [];

    for (
        let x = firstX;
        x <= lastX;
        x++
    ) {

        for (
            let y = firstY;
            y <= lastY;
            y++
        ) {

            tiles.push({
                z: this.zoom,
                x: x,
                y: y
            });

        }

    }

    return tiles;

}

/*
 * Evita que el usuario desplace la imagen
 * fuera de los limites razonables.
 */
limitPan() {

    if (
        this.imageWidth <= 0 ||
        this.imageHeight <= 0
    ) {

        return;

    }

    const dimensions = this.getLevelDimensions();

    // El limite exacto del viewport se calcula
    // posteriormente cuando conocemos su tamano.
    // Por ahora evitamos desplazar la imagen
    // demasiado hacia la izquierda o arriba.

    const maxNegativeX = -Math.max(
        0,
        dimensions.width - this.tileSize
    );

    const maxNegativeY = -Math.max(
        0,
        dimensions.height - this.tileSize
    );

    this.panX = Math.min(
        0,
        Math.max(this.panX, maxNegativeX)
    );

    this.panY = Math.min(
        0,
        Math.max(this.panY, maxNegativeY)
    );

}

/*
 * Reinicia la posicion y el nivel de zoom.
 */
reset() {

    this.zoom = 0;

    this.panX = 0;
    this.panY = 0;

    this.limitPan();

}


}
