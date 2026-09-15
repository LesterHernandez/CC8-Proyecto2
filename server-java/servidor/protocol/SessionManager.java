package servidor.protocol;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    /*
     * Representa el estado de un cliente conectado.
     *
     * Cada cliente tiene su propia instancia de ClientState.
     */
    public static class ClientState {

        private final String sessionId;

        private int zoom;
        private int panX;
        private int panY;

        /*
         * Contiene las teselas que el cliente
         * considera actualmente necesarias.
         */
        private final Set<String> activeTiles =
                ConcurrentHashMap.newKeySet();

        public ClientState(String sessionId) {

            this.sessionId = sessionId;

            this.zoom = 0;
            this.panX = 0;
            this.panY = 0;
        }

        /**
         * Actualiza la posicion y nivel de zoom
         * del cliente.
         */
        public synchronized void updateViewport(
                int zoom,
                int panX,
                int panY) {

            this.zoom = zoom;
            this.panX = panX;
            this.panY = panY;
        }

        /**
         * Reemplaza el conjunto de teselas activas
         * del cliente.
         */
        public void setActiveTiles(Set<String> tiles) {

            activeTiles.clear();

            if (tiles != null) {
                activeTiles.addAll(tiles);
            }
        }

        /**
         * Agrega una tesela al conjunto de teselas activas.
         */
        public void addActiveTile(String tileKey) {

            if (tileKey != null && !tileKey.isBlank()) {
                activeTiles.add(tileKey);
            }
        }

        /**
         * Elimina una tesela del conjunto de teselas activas.
         */
        public void removeActiveTile(String tileKey) {

            if (tileKey != null) {
                activeTiles.remove(tileKey);
            }
        }

        /**
         * Elimina todas las teselas activas.
         */
        public void clearActiveTiles() {

            activeTiles.clear();
        }

        public String getSessionId() {
            return sessionId;
        }

        public synchronized int getZoom() {
            return zoom;
        }

        public synchronized int getPanX() {
            return panX;
        }

        public synchronized int getPanY() {
            return panY;
        }

        /**
         * Devuelve una vista de solo lectura
         * de las teselas activas.
         */
        public Set<String> getActiveTiles() {

            return Collections.unmodifiableSet(
                    activeTiles
            );
        }
    }

    /*
     * Todas las sesiones activas del servidor.
     *
     * La clave es el identificador de sesion.
     */
    private final Map<String, ClientState> sessions =
            new ConcurrentHashMap<>();

    /**
     * Crea una nueva sesion para un cliente.
     */
    public ClientState createSession() {

        String sessionId =
                UUID.randomUUID().toString();

        ClientState state =
                new ClientState(sessionId);

        sessions.put(
                sessionId,
                state
        );

        System.out.println(
                "[SessionManager] Nueva sesion: "
                        + sessionId
        );

        return state;
    }

    /**
     * Busca una sesion existente.
     */
    public ClientState getSession(
            String sessionId) {

        if (sessionId == null) {
            return null;
        }

        return sessions.get(sessionId);
    }

    /**
     * Elimina una sesion.
     */
    public void removeSession(
            String sessionId) {

        if (sessionId == null) {
            return;
        }

        ClientState removed =
                sessions.remove(sessionId);

        if (removed != null) {

            System.out.println(
                    "[SessionManager] Sesion eliminada: "
                            + sessionId
            );
        }
    }

    /**
     * Devuelve la cantidad de clientes conectados.
     */
    public int getSessionCount() {

        return sessions.size();
    }

    /**
     * Comprueba si existe una sesion.
     */
    public boolean hasSession(
            String sessionId) {

        return sessionId != null
                && sessions.containsKey(sessionId);
    }
}