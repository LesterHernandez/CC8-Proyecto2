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
    private final CreditWindow credit = new CreditWindow();
    private PendingBlock ready; // A lo sumo un bloque preparado esperando crédito.
    private String lastStatus = "";
    private record PendingBlock(int id, String hash, byte[] data) { }
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
            send("IMAGE_INFO", Map.of("images", catalog, "blockSize", ImageStore.BLOCK, "maxBlockBytes", CreditWindow.MAX_PACKET, "maxCreditBytes", CreditWindow.MAX_CAPACITY));
            return;
        }
        if (!hello || !sessionId.equals(Json.text(message, "sessionId")))
            throw new IllegalArgumentException("Sesión no válida");
        switch (type) {
            case "CREDIT_INIT" -> { credit.initialize(Json.integer(message, "capacityBytes")); status(); }
            case "CREDIT_GRANT" -> {
                credit.grant(counter(message, "grantId"), counter(message, "releasedBytes")); status();
            }
            case "CREDIT_STATUS" -> { lastStatus = ""; status(); }
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
        viewId = nextView; image = requested; pending.clear(); awaiting.clear(); delivered = 0; done = false; ready = null;
        for (int row = y / ImageStore.BLOCK; row <= (y + height-1) / ImageStore.BLOCK; row++)
            for (int col = x / ImageStore.BLOCK; col <= (x + width-1) / ImageStore.BLOCK; col++)
                pending.add(new Tile(col, row, level));
        send("VIEW_ACCEPTED", Map.of("viewId", viewId, "imageId", store.imageId, "level", level,
                "x", x, "y", y, "width", width, "height", height, "blocks", pending.size()));
    }

    /** Como máximo un bloque en preparación por sesión y una cola de red pequeña.
     * El bloque se descuenta al encolarlo; cambiar de vista no devuelve bytes en tránsito.
     */
    void pump() {
        if (!hello || working || client.closing || !client.output.isEmpty()) return;
        if (ready != null) {
            if (!credit.canSend(ready.data().length)) { status(); return; }
            try { credit.debit(ready.data().length); }
            catch (IllegalArgumentException invalid) { client.protocolError(invalid.getMessage()); return; }
            awaiting.put(ready.id(), ready.hash());
            client.enqueue(WebSocketFrames.frame(2, ready.data())); ready = null; delivered++;
            if (pending.isEmpty() && !done) {
                done = true; send("VIEW_DONE", Map.of("viewId", viewId, "blocks", delivered));
            }
            status(); return;
        }
        if (pending.isEmpty()) return;
        if (!credit.initialized()) { status(); return; }
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
                    int id = ++transferId;
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
                    if (json.length > 4096) { client.protocolError("Cabecera de bloque demasiado grande"); return; }
                    // Un mensaje binario PRIB = uint32 BE + cabecera JSON + RGB canónico.
                    ByteBuffer data = ByteBuffer.allocate(4 + json.length + block.rgb().length);
                    data.putInt(json.length).put(json).put(block.rgb());
                    // Conservar el mensaje exacto: 4 bytes + JSON UTF-8 + RGB.
                    // El siguiente ciclo solo lo encolará si alcanza el crédito.
                    ready = new PendingBlock(id, block.hash(), data.array());
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

    private static long counter(Map<String, Object> message, String field) {
        if (message.get(field) instanceof Long value) return value;
        throw new IllegalArgumentException("Falta contador: " + field);
    }

    /** Los controles siguen circulando aunque los datos estén detenidos.
     * Solo publicar cambios evita llenar la cola durante una espera larga.
     */
    private void status() {
        String state = !credit.initialized() ? "WAIT_INIT"
                : ready != null && !credit.canSend(ready.data().length) ? "WAIT_CREDIT"
                : pending.isEmpty() && ready == null && !working ? "IDLE" : "SENDING";
        Map<String, Object> fields = Map.of("capacityBytes", credit.capacity(), "availableBytes", credit.available(),
                "outstandingBytes", credit.outstanding(), "releasedBytes", credit.released(),
                "grantId", credit.grantId(), "state", state);
        String signature = Json.encode(fields);
        if (!signature.equals(lastStatus)) { lastStatus = signature; send("CREDIT_STATUS", fields); }
    }

    private void send(String type, Map<String, ?> fields) {
        Map<String, Object> data = new LinkedHashMap<>(fields);
        data.put("version", 1); data.put("type", type); data.put("sessionId", sessionId); client.text(data);
    }
}

