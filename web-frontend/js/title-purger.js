class TilePurger {


constructor(container) {

    this.container = container;

    // Teselas actualmente cargadas.
    // La clave tiene el formato: z_x_y
    this.loadedTiles = new Map();

}

/*
 * Sincroniza las teselas necesarias con las teselas
 * actualmente presentes en el navegador.
 */
syncAndPurge(requiredTiles) {

    const requiredKeys = new Set();

    // Cargar o actualizar las teselas necesarias.
    requiredTiles.forEach(tile => {

        const key = `${tile.z}_${tile.x}_${tile.y}`;

        requiredKeys.add(key);

        if (!this.loadedTiles.has(key)) {

            this.loadTile(tile, key);

        } else {

            this.updateTilePosition(tile, key);

        }

    });

    // Eliminar las teselas que ya no son necesarias.
    for (const [key, imgElement] of this.loadedTiles.entries()) {

        if (!requiredKeys.has(key)) {

            this.purgeTile(key, imgElement);

        }

    }

}

/*
 * Crea y carga una nueva tesela.
 */
loadTile(tile, key) {

    const img = document.createElement('img');

    img.src = tile.url;

    img.className = 'tile';

    img.draggable = false;

    img.style.left = `${tile.left}px`;
    img.style.top = `${tile.top}px`;

    img.dataset.tileKey = key;

    img.onload = () => {

        console.log(
            `[TilePurger] Tesela cargada: ${key}`
        );

    };

    img.onerror = () => {

        console.error(
            `[TilePurger] No se pudo cargar la tesela: ${key}`
        );

        this.removeTile(key);

    };

    this.container.appendChild(img);

    this.loadedTiles.set(key, img);

}

/*
 * Actualiza la posicion de una tesela
 * que ya esta cargada.
 */
updateTilePosition(tile, key) {

    const img = this.loadedTiles.get(key);

    if (!img) {
        return;
    }

    img.style.left = `${tile.left}px`;
    img.style.top = `${tile.top}px`;

}

/*
 * Elimina una tesela que ya no pertenece
 * al viewport actual.
 */
purgeTile(key, imgElement) {

    if (!imgElement) {
        return;
    }

    imgElement.onload = null;
    imgElement.onerror = null;

    // Liberar la referencia al recurso.
    imgElement.src = '';

    // Eliminar el elemento del DOM.
    if (imgElement.parentNode) {

        imgElement.parentNode.removeChild(imgElement);

    }

    // Eliminar la referencia almacenada.
    this.loadedTiles.delete(key);

    console.log(
        `[TilePurger] Tesela eliminada: ${key}`
    );

}

/*
 * Elimina una tesela especifica.
 */
removeTile(key) {

    const imgElement = this.loadedTiles.get(key);

    if (!imgElement) {
        return;
    }

    this.purgeTile(key, imgElement);

}

/*
 * Elimina todas las teselas actualmente cargadas.
 */
clear() {

    for (const [key, imgElement] of this.loadedTiles.entries()) {

        this.purgeTile(key, imgElement);

    }

}

/*
 * Devuelve la cantidad de teselas actualmente
 * administradas por el cliente.
 */
getLoadedCount() {

    return this.loadedTiles.size;

}


}
