 package servidor.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representa un paquete del protocolo de control
 * utilizado por el visor de imagenes.
 *
 * Los mensajes se intercambian como JSON.
 *
 * Ejemplo:
 *
 * {
 *   "type": "VIEWPORT_UPDATE",
 *   "zoom": 3,
 *   "panX": -200,
 *   "panY": -100,
 *   "tiles": ["3_2_1", "3_3_1"]
 * }
 */
public class ControlPacket {

    /*
     * Tipos de mensajes soportados por el protocolo.
     */
    public enum Type {

        VIEWPORT_UPDATE,

        TILE_REQUEST,

        TILE_RELEASE,

        SESSION,

        VIEWPORT_ACK,

        ERROR
    }

    /*
     * Tipo de mensaje.
     */
    private Type type;

    /*
     * Identificador de la imagen.
     */
    private String imageId;

    /*
     * Identificador de la sesion del cliente.
     */
    private String sessionId;

    /*
     * Nivel de zoom solicitado.
     */
    private int zoom;

    /*
     * Desplazamiento horizontal.
     */
    private int panX;

    /*
     * Desplazamiento vertical.
     */
    private int panY;

    /*
     * Coordenadas de las teselas necesarias.
     *
     * Ejemplo:
     *
     * 3_2_1
     * 3_3_1
     * 3_2_2
     */
    private final List<String> tiles;

    /*
     * Coordenadas individuales de una tesela.
     *
     * Se utilizan principalmente para
     * TILE_REQUEST y TILE_RELEASE.
     */
    private int tileX;
    private int tileY;

    /*
     * Mensaje adicional utilizado para errores.
     */
    private String message;

    /**
     * Constructor vacio.
     *
     * Se utiliza para crear un paquete y luego
     * configurar sus propiedades.
     */
    public ControlPacket() {

        this.tiles = new ArrayList<>();
    }

    /**
     * Constructor para crear un paquete
     * indicando su tipo.
     */
    public ControlPacket(Type type) {

        this();

        this.type = type;
    }

    /**
     * Crea un paquete VIEWPORT_UPDATE.
     */
    public static ControlPacket viewportUpdate(
            int zoom,
            int panX,
            int panY,
            List<String> tiles) {

        ControlPacket packet =
                new ControlPacket(
                        Type.VIEWPORT_UPDATE
                );

        packet.zoom = zoom;
        packet.panX = panX;
        packet.panY = panY;

        packet.setTiles(tiles);

        return packet;
    }

    /**
     * Crea un paquete TILE_REQUEST.
     */
    public static ControlPacket tileRequest(
            String imageId,
            int zoom,
            int tileX,
            int tileY) {

        ControlPacket packet =
                new ControlPacket(
                        Type.TILE_REQUEST
                );

        packet.imageId = imageId;
        packet.zoom = zoom;
        packet.tileX = tileX;
        packet.tileY = tileY;

        return packet;
    }

    /**
     * Crea un paquete TILE_RELEASE.
     */
    public static ControlPacket tileRelease(
            String imageId,
            int zoom,
            int tileX,
            int tileY) {

        ControlPacket packet =
                new ControlPacket(
                        Type.TILE_RELEASE
                );

        packet.imageId = imageId;
        packet.zoom = zoom;
        packet.tileX = tileX;
        packet.tileY = tileY;

        return packet;
    }

    /**
     * Crea un paquete SESSION.
     */
    public static ControlPacket session(
            String sessionId) {

        ControlPacket packet =
                new ControlPacket(
                        Type.SESSION
                );

        packet.sessionId = sessionId;

        return packet;
    }

    /**
     * Crea un paquete VIEWPORT_ACK.
     */
    public static ControlPacket viewportAck(
            String sessionId) {

        ControlPacket packet =
                new ControlPacket(
                        Type.VIEWPORT_ACK
                );

        packet.sessionId = sessionId;

        return packet;
    }

    /**
     * Crea un paquete ERROR.
     */
    public static ControlPacket error(
            String message) {

        ControlPacket packet =
                new ControlPacket(
                        Type.ERROR
                );

        packet.message = message;

        return packet;
    }

    /**
     * Devuelve el tipo de mensaje.
     */
    public Type getType() {
        return type;
    }

    /**
     * Establece el tipo de mensaje.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Devuelve el identificador de imagen.
     */
    public String getImageId() {
        return imageId;
    }

    /**
     * Establece el identificador de imagen.
     */
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    /**
     * Devuelve el identificador de sesion.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Establece el identificador de sesion.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Devuelve el nivel de zoom.
     */
    public int getZoom() {
        return zoom;
    }

    /**
     * Establece el nivel de zoom.
     */
    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    /**
     * Devuelve el desplazamiento horizontal.
     */
    public int getPanX() {
        return panX;
    }

    /**
     * Establece el desplazamiento horizontal.
     */
    public void setPanX(int panX) {
        this.panX = panX;
    }

    /**
     * Devuelve el desplazamiento vertical.
     */
    public int getPanY() {
        return panY;
    }

    /**
     * Establece el desplazamiento vertical.
     */
    public void setPanY(int panY) {
        this.panY = panY;
    }

    /**
     * Devuelve una lista de teselas.
     *
     * Se devuelve una vista no modificable para
     * evitar modificaciones externas accidentales.
     */
    public List<String> getTiles() {

        return Collections.unmodifiableList(
                tiles
        );
    }

    /**
     * Reemplaza la lista de teselas.
     */
    public void setTiles(
            List<String> tiles) {

        this.tiles.clear();

        if (tiles != null) {
            this.tiles.addAll(tiles);
        }
    }

    /**
     * Agrega una tesela a la lista.
     */
    public void addTile(String tileKey) {

        if (tileKey == null
                || tileKey.isBlank()) {

            return;
        }

        tiles.add(tileKey);
    }

    /**
     * Devuelve la coordenada X de la tesela.
     */
    public int getTileX() {
        return tileX;
    }

    /**
     * Establece la coordenada X de la tesela.
     */
    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    /**
     * Devuelve la coordenada Y de la tesela.
     */
    public int getTileY() {
        return tileY;
    }

    /**
     * Establece la coordenada Y de la tesela.
     */
    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    /**
     * Devuelve el mensaje adicional.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Establece el mensaje adicional.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Comprueba que el paquete tenga un tipo valido.
     */
    public boolean isValid() {

        if (type == null) {
            return false;
        }

        switch (type) {

            case VIEWPORT_UPDATE:

                return zoom >= 0;

            case TILE_REQUEST:
            case TILE_RELEASE:

                return imageId != null
                        && !imageId.isBlank()
                        && zoom >= 0
                        && tileX >= 0
                        && tileY >= 0;

            case SESSION:

                return sessionId != null
                        && !sessionId.isBlank();

            case VIEWPORT_ACK:

                return sessionId != null
                        && !sessionId.isBlank();

            case ERROR:

                return message != null
                        && !message.isBlank();

            default:

                return false;
        }
    }

    /**
     * Convierte el paquete a una representacion JSON.
     *
     * Esta implementacion no necesita librerias externas.
     */
    public String toJson() {

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        boolean first = true;

        if (type != null) {

            appendField(
                    json,
                    "type",
                    type.name(),
                    first
            );

            first = false;
        }

        if (imageId != null) {

            appendField(
                    json,
                    "imageId",
                    imageId,
                    first
            );

            first = false;
        }

        if (sessionId != null) {

            appendField(
                    json,
                    "sessionId",
                    sessionId,
                    first
            );

            first = false;
        }

        if (type == Type.VIEWPORT_UPDATE) {

            appendNumberField(
                    json,
                    "zoom",
                    zoom,
                    first
            );

            first = false;

            appendNumberField(
                    json,
                    "panX",
                    panX,
                    first
            );

            first = false;

            appendNumberField(
                    json,
                    "panY",
                    panY,
                    first
            );

            first = false;

            appendTilesField(
                    json,
                    first
            );

            first = false;
        }

        if (type == Type.TILE_REQUEST
                || type == Type.TILE_RELEASE) {

            appendNumberField(
                    json,
                    "z",
                    zoom,
                    first
            );

            first = false;

            appendNumberField(
                    json,
                    "x",
                    tileX,
                    first
            );

            first = false;

            appendNumberField(
                    json,
                    "y",
                    tileY,
                    first
            );

            first = false;
        }

        if (message != null) {

            appendField(
                    json,
                    "message",
                    message,
                    first
            );
        }

        json.append("}");

        return json.toString();
    }

    /**
     * Convierte un paquete JSON sencillo
     * a un ControlPacket.
     *
     * Esta implementacion esta pensada para
     * los mensajes definidos por nuestro protocolo.
     */
    public static ControlPacket fromJson(
            String json) {

        if (json == null
                || json.isBlank()) {

            return null;
        }

        String typeValue =
                getStringValue(
                        json,
                        "type"
                );

        if (typeValue == null) {
            return null;
        }

        Type type;

        try {

            type = Type.valueOf(
                    typeValue.toUpperCase()
            );

        } catch (IllegalArgumentException e) {

            return null;
        }

        ControlPacket packet =
                new ControlPacket(type);

        packet.imageId =
                getStringValue(
                        json,
                        "imageId"
                );

        packet.sessionId =
                getStringValue(
                        json,
                        "sessionId"
                );

        packet.message =
                getStringValue(
                        json,
                        "message"
                );

        packet.zoom =
                getIntValue(
                        json,
                        "zoom",
                        0
                );

        packet.panX =
                getIntValue(
                        json,
                        "panX",
                        0
                );

        packet.panY =
                getIntValue(
                        json,
                        "panY",
                        0
                );

        packet.tileX =
                getIntValue(
                        json,
                        "x",
                        0
                );

        packet.tileY =
                getIntValue(
                        json,
                        "y",
                        0
                );

        List<String> parsedTiles =
                getTilesValue(json);

        packet.setTiles(parsedTiles);

        return packet;
    }

    /**
     * Agrega un campo de texto al JSON.
     */
    private void appendField(
            StringBuilder json,
            String name,
            String value,
            boolean first) {

        if (!first) {
            json.append(",");
        }

        json.append("\"")
                .append(escapeJson(name))
                .append("\":\"")
                .append(escapeJson(value))
                .append("\"");
    }

    /**
     * Agrega un campo numerico al JSON.
     */
    private void appendNumberField(
            StringBuilder json,
            String name,
            int value,
            boolean first) {

        if (!first) {
            json.append(",");
        }

        json.append("\"")
                .append(name)
                .append("\":")
                .append(value);
    }

    /**
     * Agrega la lista de teselas al JSON.
     */
    private void appendTilesField(
            StringBuilder json,
            boolean first) {

        if (!first) {
            json.append(",");
        }

        json.append("\"tiles\":[");

        for (int i = 0; i < tiles.size(); i++) {

            if (i > 0) {
                json.append(",");
            }

            json.append("\"")
                    .append(
                            escapeJson(
                                    tiles.get(i)
                            )
                    )
                    .append("\"");
        }

        json.append("]");
    }

    /**
     * Escapa caracteres especiales para JSON.
     */
    private String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Obtiene un campo de texto sencillo
     * desde un JSON.
     */
    private static String getStringValue(
            String json,
            String field) {

        String search =
                "\"" + field + "\"";

        int fieldIndex =
                json.indexOf(search);

        if (fieldIndex < 0) {
            return null;
        }

        int colonIndex =
                json.indexOf(
                        ":",
                        fieldIndex
                );

        if (colonIndex < 0) {
            return null;
        }

        int firstQuote =
                json.indexOf(
                        "\"",
                        colonIndex + 1
                );

        if (firstQuote < 0) {
            return null;
        }

        int secondQuote =
                findClosingQuote(
                        json,
                        firstQuote + 1
                );

        if (secondQuote < 0) {
            return null;
        }

        return json.substring(
                firstQuote + 1,
                secondQuote
        );
    }

    /**
     * Obtiene un entero desde un JSON.
     */
    private static int getIntValue(
            String json,
            String field,
            int defaultValue) {

        String search =
                "\"" + field + "\"";

        int fieldIndex =
                json.indexOf(search);

        if (fieldIndex < 0) {
            return defaultValue;
        }

        int colonIndex =
                json.indexOf(
                        ":",
                        fieldIndex
                );

        if (colonIndex < 0) {
            return defaultValue;
        }

        int start =
                colonIndex + 1;

        while (
                start < json.length()
                        && Character.isWhitespace(
                        json.charAt(start))
        ) {

            start++;
        }

        int end = start;

        if (
                end < json.length()
                        && (
                        json.charAt(end) == '-'
                                || json.charAt(end) == '+'
                )
        ) {

            end++;
        }

        while (
                end < json.length()
                        && Character.isDigit(
                        json.charAt(end))
        ) {

            end++;
        }

        if (end == start) {
            return defaultValue;
        }

        try {

            return Integer.parseInt(
                    json.substring(
                            start,
                            end
                    )
            );

        } catch (NumberFormatException e) {

            return defaultValue;
        }
    }

    /**
     * Obtiene la lista de teselas desde JSON.
     */
    private static List<String> getTilesValue(
            String json) {

        List<String> result =
                new ArrayList<>();

        String search = "\"tiles\"";

        int fieldIndex =
                json.indexOf(search);

        if (fieldIndex < 0) {
            return result;
        }

        int openBracket =
                json.indexOf(
                        "[",
                        fieldIndex
                );

        int closeBracket =
                json.indexOf(
                        "]",
                        openBracket
                );

        if (
                openBracket < 0
                        || closeBracket < 0
        ) {

            return result;
        }

        String content =
                json.substring(
                        openBracket + 1,
                        closeBracket
                ).trim();

        if (content.isEmpty()) {
            return result;
        }

        String[] values =
                content.split(",");

        for (String value : values) {

            value = value.trim();

            if (
                    value.startsWith("\"")
                            && value.endsWith("\"")
                            && value.length() >= 2
            ) {

                value = value.substring(
                        1,
                        value.length() - 1
                );

                result.add(value);
            }
        }

        return result;
    }

    /**
     * Busca la comilla de cierre de un string JSON.
     */
    private static int findClosingQuote(
            String json,
            int start) {

        boolean escaped = false;

        for (
                int i = start;
                i < json.length();
                i++
        ) {

            char current =
                    json.charAt(i);

            if (escaped) {

                escaped = false;
                continue;
            }

            if (current == '\\') {

                escaped = true;
                continue;
            }

            if (current == '"') {
                return i;
            }
        }

        return -1;
    }

    @Override
    public String toString() {

        return toJson();
    }
}