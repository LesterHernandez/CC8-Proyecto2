package servidor.image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administra una cache de teselas de imagen.
 *
 * La cache evita leer del disco una misma tesela
 * cada vez que un cliente la solicita.
 *
 * Se utiliza una politica LRU:
 *
 * Least Recently Used
 *
 * Cuando la cache llega a su limite, se elimina
 * la tesela que lleva mas tiempo sin utilizarse.
 */
public class TileCacheManager {

    /*
     * Cantidad maxima de teselas almacenadas
     * simultaneamente en memoria.
     */
    private final int maxTiles;

    /*
     * LinkedHashMap con accessOrder=true permite
     * mantener las entradas ordenadas por uso.
     *
     * La tesela utilizada mas recientemente queda
     * al final.
     *
     * La menos utilizada queda al inicio.
     */
    private final Map<String, byte[]> cache;

    /*
     * Estadisticas de funcionamiento.
     */
    private long cacheHits;
    private long cacheMisses;

    /**
     * Crea un administrador de cache.
     *
     * @param maxTiles cantidad maxima de teselas en memoria
     */
    public TileCacheManager(int maxTiles) {

        if (maxTiles <= 0) {
            throw new IllegalArgumentException(
                    "El limite de teselas debe ser mayor que cero."
            );
        }

        this.maxTiles = maxTiles;

        this.cache = new LinkedHashMap<>(
                16,
                0.75f,
                true
        );

        this.cacheHits = 0;
        this.cacheMisses = 0;
    }

    /**
     * Obtiene una tesela.
     *
     * Primero se busca en memoria.
     *
     * Si no existe, se lee desde el archivo,
     * se almacena en la cache y se devuelve.
     *
     * @param pyramid piramide de imagen
     * @param zoom nivel de zoom
     * @param x coordenada X de la tesela
     * @param y coordenada Y de la tesela
     * @return bytes de la tesela o null si no existe
     */
    public synchronized byte[] getTile(
            ImagePyramid pyramid,
            int zoom,
            int x,
            int y) throws IOException {

        if (pyramid == null) {
            throw new IllegalArgumentException(
                    "La piramide no puede ser null."
            );
        }

        /*
         * Primero verificamos que la coordenada
         * sea valida dentro de la piramide.
         */
        if (!pyramid.isValidTile(zoom, x, y)) {

            return null;
        }

        String key = createKey(
                pyramid.getImageId(),
                zoom,
                x,
                y
        );

        /*
         * Buscar primero en memoria.
         */
        byte[] cachedTile = cache.get(key);

        if (cachedTile != null) {

            cacheHits++;

            System.out.println(
                    "[TileCacheManager] HIT: "
                            + key
            );

            return cachedTile;
        }

        /*
         * La tesela no estaba en memoria.
         */
        cacheMisses++;

        System.out.println(
                "[TileCacheManager] MISS: "
                        + key
        );

        File tileFile = pyramid.getTileFile(
                zoom,
                x,
                y
        );

        if (tileFile == null || !tileFile.isFile()) {

            System.out.println(
                    "[TileCacheManager] Tile no encontrado: "
                            + key
            );

            return null;
        }

        /*
         * Leemos la tesela desde disco.
         */
        byte[] tileData = Files.readAllBytes(
                tileFile.toPath()
        );

        /*
         * Agregamos la tesela a la cache.
         */
        put(key, tileData);

        return tileData;
    }

    /**
     * Agrega una tesela directamente a la cache.
     */
    public synchronized void put(
            String key,
            byte[] tileData) {

        if (key == null || key.isBlank()) {
            return;
        }

        if (tileData == null) {
            return;
        }

        /*
         * Si la tesela ya existe, se reemplaza.
         *
         * Al utilizar LinkedHashMap con accessOrder=true,
         * la entrada pasa a ser considerada recientemente usada.
         */
        cache.put(
                key,
                tileData
        );

        /*
         * Si superamos el limite, eliminamos
         * las entradas mas antiguas.
         */
        while (cache.size() > maxTiles) {

            Iterator<Map.Entry<String, byte[]>> iterator =
                    cache.entrySet().iterator();

            if (!iterator.hasNext()) {
                break;
            }

            Map.Entry<String, byte[]> oldest =
                    iterator.next();

            String removedKey =
                    oldest.getKey();

            iterator.remove();

            System.out.println(
                    "[TileCacheManager] Evict: "
                            + removedKey
            );
        }
    }

    /**
     * Busca una tesela solamente en la cache.
     *
     * No accede al disco.
     */
    public synchronized byte[] getCached(
            String key) {

        if (key == null || key.isBlank()) {
            return null;
        }

        byte[] tile = cache.get(key);

        if (tile != null) {
            cacheHits++;
        }

        return tile;
    }

    /**
     * Comprueba si una tesela esta actualmente
     * almacenada en memoria.
     */
    public synchronized boolean contains(
            String key) {

        return key != null
                && cache.containsKey(key);
    }

    /**
     * Elimina una tesela especifica de la cache.
     */
    public synchronized void remove(
            String key) {

        if (key == null) {
            return;
        }

        byte[] removed = cache.remove(key);

        if (removed != null) {

            System.out.println(
                    "[TileCacheManager] Remove: "
                            + key
            );
        }
    }

    /**
     * Elimina todas las teselas de la cache.
     */
    public synchronized void clear() {

        cache.clear();

        System.out.println(
                "[TileCacheManager] Cache limpiada."
        );
    }

    /**
     * Devuelve la cantidad actual de teselas
     * almacenadas en memoria.
     */
    public synchronized int size() {

        return cache.size();
    }

    /**
     * Devuelve la capacidad maxima de la cache.
     */
    public int getMaxTiles() {

        return maxTiles;
    }

    /**
     * Devuelve la cantidad de accesos que encontraron
     * la tesela directamente en memoria.
     */
    public synchronized long getCacheHits() {

        return cacheHits;
    }

    /**
     * Devuelve la cantidad de solicitudes que tuvieron
     * que buscar la tesela fuera de la cache.
     */
    public synchronized long getCacheMisses() {

        return cacheMisses;
    }

    /**
     * Devuelve el porcentaje de aciertos de la cache.
     */
    public synchronized double getHitRate() {

        long total =
                cacheHits + cacheMisses;

        if (total == 0) {
            return 0.0;
        }

        return (cacheHits * 100.0) / total;
    }

    /**
     * Reinicia las estadisticas de la cache.
     */
    public synchronized void resetStatistics() {

        cacheHits = 0;
        cacheMisses = 0;
    }

    /**
     * Crea una clave unica para una tesela.
     *
     * Ejemplo:
     *
     * imagen1 + Z3 + X4 + Y2
     *
     * produce:
     *
     * imagen1_3_4_2
     */
    public String createKey(
            String imageId,
            int zoom,
            int x,
            int y) {

        return imageId
                + "_"
                + zoom
                + "_"
                + x
                + "_"
                + y;
    }

    /**
     * Devuelve informacion de la cache.
     */
    public synchronized String getInfo() {

        return "TileCacheManager{"
                + "size=" + cache.size()
                + ", maxTiles=" + maxTiles
                + ", hits=" + cacheHits
                + ", misses=" + cacheMisses
                + ", hitRate="
                + String.format(
                        "%.2f",
                        getHitRate()
                )
                + "%}";
    }
}