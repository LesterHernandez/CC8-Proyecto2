package prib;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Estado PRIB de un solo navegador. Solo el hilo Selector modifica esta clase.
 * Los trabajadores reciben valores copiados y devuelven resultados al Selector.
 */
final class PribSession {
    private final PribServer server;
    private final PribServer.Client client;
    private final String sessionId = UUID.randomUUID().toString();
    private final ArrayDeque<Tile> pending = new ArrayDeque<>();
    private final Map<Integer, String> awaiting = new HashMap<>();
    private boolean hello, working, done;
    private int viewId, transferId, delivered;
    private PribServer.Prepared image;
    private record Tile(int column, int row, int level) { }

    PribSession(PribServer server, PribServer.Client client) { this.server = server; this.client = client; }

    void receive(String text) {
        Map<String, Object> message = Json.parse(text);
        if (Json.integer(message, "version") != 1) throw new IllegalArgumentException("Versión PRIB no soportada");
        String type = Json.text(message, "type");
        if (type.equals("HELLO")) {
            if (hello) throw new IllegalArgumentException("HELLO repetido");
            hello = true;
            var catalog = server.images.values().stream().map(item -> Map.of(
                    "imageId", item.store().imageId, "name", item.name(), "width", item.store().width,
                    "height", item.store().height, "levels", item.store().levels)).toList();
            send("IMAGE_INFO", Map.of("images", catalog, "blockSize", ImageStore.BLOCK));
            return;
        }
        if (!hello || !sessionId.equals(Json.text(message, "sessionId")))
            throw new IllegalArgumentException("Sesión no válida");
        switch (type) {
            case "VIEW" -> view(message);
            case "ACK" -> {
                int generation = Json.integer(message, "viewId");
                if (generation < viewId && generation > 0) return; // ACK de una vista sustituida.
                if (generation != viewId) throw new IllegalArgumentException("ACK de vista desconocida");
                int id = Json.integer(message, "transferId");
                String expected = awaiting.remove(id);
                if (expected == null || !expected.equals(Json.text(message, "hash")))
                    throw new IllegalArgumentException("ACK desconocido o hash distinto");
            }
            case "CLOSE" -> client.closeFrame(1000);
            default -> throw new IllegalArgumentException("Comando no implementado: " + type);
        }
    }

    private void view(Map<String, Object> message) {
        PribServer.Prepared requested = server.images.get(Json.text(message, "imageId"));
        if (requested == null) throw new IllegalArgumentException("Imagen no encontrada");
        int nextView = Json.integer(message, "viewId"), level = Json.integer(message, "level");
        int x = Json.integer(message, "x"), y = Json.integer(message, "y");
        int width = Json.integer(message, "width"), height = Json.integer(message, "height");
        ImageStore store = requested.store();
        if (nextView <= viewId || x < 0 || y < 0 || width <= 0 || height <= 0 || width > 3840 || height > 2160
                || (long)x + width > store.width(level) || (long)y + height > store.height(level))
            throw new IllegalArgumentException("Vista fuera de límites (máximo 3840 x 2160)");
        // Protección mínima de etapa 3: una vista nueva sustituye la lista pendiente.
        // La reutilización, prioridad avanzada y CANCEL explícito pertenecen a etapa 5.
        viewId = nextView; image = requested; pending.clear(); awaiting.clear(); delivered = 0; done = false;
        for (int row = y / ImageStore.BLOCK; row <= (y + height-1) / ImageStore.BLOCK; row++)
            for (int col = x / ImageStore.BLOCK; col <= (x + width-1) / ImageStore.BLOCK; col++)
                pending.add(new Tile(col, row, level));
        send("VIEW_ACCEPTED", Map.of("viewId", viewId, "imageId", store.imageId, "level", level,
                "x", x, "y", y, "width", width, "height", height, "blocks", pending.size()));
    }

    /** Como máximo un bloque en preparación por sesión y una cola de red pequeña.
     * Este límite fijo protege recursos; no implementa los créditos de etapa 4.
     */
    void pump() {
        if (!hello || working || client.closing || !client.output.isEmpty() || pending.isEmpty()) return;
        if (awaiting.size() >= 1024) { client.protocolError("Demasiados bloques sin ACK"); return; }
        Tile tile = pending.peek(); int generation = viewId;
        PribServer.Prepared selected = image;
        working = true;
        boolean accepted = server.submit(() -> {
            try {
                ImageStore.Block block = selected.store().readBlock(tile.level, tile.column, tile.row);
                server.complete(() -> {
                    working = false;
                    if (!client.key.isValid() || client.closing || generation != viewId) return;
                    int id = ++transferId; delivered++;
                    Map<String, Object> header = new LinkedHashMap<>();
                    header.put("version", 1); header.put("type", "BLOCK_FULL"); header.put("sessionId", sessionId);
                    header.put("viewId", generation); header.put("transferId", id);
                    header.put("imageId", selected.store().imageId); header.put("level", tile.level);
                    header.put("blockId", tile.level + ":" + tile.column + ":" + tile.row);
                    header.put("x", tile.column * ImageStore.BLOCK); header.put("y", tile.row * ImageStore.BLOCK);
                    header.put("width", block.width()); header.put("height", block.height());
                    header.put("format", "RGB8"); header.put("codec", "RAW");
                    header.put("payloadLength", block.rgb().length); header.put("expectedHash", block.hash());
                    byte[] json = Json.encode(header).getBytes(StandardCharsets.UTF_8);
                    // Un mensaje binario PRIB = uint32 BE + cabecera JSON + RGB canónico.
                    ByteBuffer data = ByteBuffer.allocate(4 + json.length + block.rgb().length);
                    data.putInt(json.length).put(json).put(block.rgb());
                    awaiting.put(id, block.hash()); client.enqueue(WebSocketFrames.frame(2, data.array()));
                    if (pending.isEmpty() && !done) {
                        done = true; send("VIEW_DONE", Map.of("viewId", viewId, "blocks", delivered));
                    }
                });
            } catch (Exception error) {
                server.complete(() -> {
                    working = false;
                    if (client.key.isValid() && !client.closing && generation == viewId)
                        client.protocolError("No se pudo leer o verificar el bloque");
                });
            }
        });
        if (accepted) pending.remove(); else working = false; // Reintentar en el siguiente ciclo si el pool está lleno.
    }

    private void send(String type, Map<String, ?> fields) {
        Map<String, Object> data = new LinkedHashMap<>(fields);
        data.put("version", 1); data.put("type", type); data.put("sessionId", sessionId); client.text(data);
    }
}

