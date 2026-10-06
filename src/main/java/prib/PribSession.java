package prib;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Estado por navegador. Solo el Selector modifica sesiones e inventarios.
 * Disco/hash se ejecutan en trabajadores con cancelación cooperativa.
 */
final class PribSession {
    private final PribServer server;
    private final PribServer.Client client;
    private final String sessionId = UUID.randomUUID().toString();
    private final ArrayList<Tile> pending = new ArrayList<>();
    private final Map<Integer, Sent> awaiting = new HashMap<>();
    private final CreditWindow credit = new CreditWindow();
    private final ClientCache cache = new ClientCache();
    private PendingBlock ready;
    private String lastStatus = "";
    private boolean hello, working, planning, planned, done = true, waitingCredit;
    private int viewId, transferId, delivered, vx, vy, vw, vh;
    private int full, reuse, ref, delta, required, recoveries, deltaCandidates;
    private long deltaNanos;
    private final Map<String, Integer> retries = new HashMap<>();
    private final Set<String> forceFull = new HashSet<>();
    private static final int MAX_RECOVERIES = 2;
    private long packetBytes, avoidedBytes;
    private PribServer.Prepared image;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private record Tile(int column, int row, int level, ImageStore.Descriptor info) { }
    private record PendingBlock(int id, String hash, byte[] data, String mode, int rawBytes, int payloadBytes, Tile tile, ClientCache.Entry base) { }
    private record Sent(Tile tile, ClientCache.Entry base, String mode) { }
    private record Difference(ClientCache.Entry base, byte[] payload) { }
    private record Choice(Tile tile, ClientCache.Entry base, PendingBlock packet, double score) { }

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
            send("IMAGE_INFO", Map.of("images", catalog, "blockSize", ImageStore.BLOCK,
                    "maxBlockBytes", CreditWindow.MAX_PACKET, "maxCreditBytes", CreditWindow.MAX_CAPACITY,
                    "maxCacheBytes", ClientCache.MAX_BYTES, "maxCacheEntries", ClientCache.MAX_ENTRIES,
                    "modes", "FULL,REUSE,REF,DELTA", "deltaCodec", "XOR_RUNS_1", "maxRecoveries", MAX_RECOVERIES));
            return;
        }
        if (!hello || !sessionId.equals(Json.text(message, "sessionId"))) throw new IllegalArgumentException("Sesión no válida");
        switch (type) {
            case "PREPARE_LIST", "PREPARE_ENTRIES" -> preparationList(type, message);
            case "PREPARE_STATUS" -> send("PREPARE_STATUS", server.preparation.status());
            case "PREPARE_START" -> {
                try {
                    if (server.images.size() >= 100) throw new IllegalArgumentException("Máximo 100 imágenes preparadas");
                    server.preparation.start(Json.text(message, "zip"), Json.text(message, "entry"), Json.text(message, "name"), server::publish);
                    send("PREPARE_STATUS", server.preparation.status());
                } catch (IllegalArgumentException error) { send("PREPARE_ERROR", Map.of("message", error.getMessage())); }
            }
            case "CREDIT_INIT" -> { credit.initialize(Json.integer(message, "capacityBytes")); status(); }
            case "CREDIT_GRANT" -> { credit.grant(counter(message, "grantId"), counter(message, "releasedBytes")); waitingCredit = false; status(); }
            case "CREDIT_STATUS" -> { lastStatus = ""; status(); }
            case "CACHE_STATE" -> cacheState(message);
            case "VIEW", "VIEW_UPDATE" -> view(message);
            case "CANCEL" -> cancel(Json.integer(message, "viewId"));
            case "RECOVER" -> recover(message);
            case "ACK" -> {
                int generation = Json.integer(message, "viewId");
                if (generation < viewId && generation > 0 || generation == viewId && cancelled.get()) return;
                if (generation != viewId) throw new IllegalArgumentException("ACK de vista desconocida");
                Sent expected = awaiting.remove(Json.integer(message, "transferId"));
                if (expected == null || !expected.tile().info().hash().equals(Json.text(message, "hash"))) throw new IllegalArgumentException("ACK desconocido o hash distinto");
                finish();
            }
            case "CLOSE" -> client.closeFrame(1000);
            default -> throw new IllegalArgumentException("Comando no implementado: " + type);
        }
    }

    // Listar ZIP puede implicar disco lento: nunca hacerlo dentro del Selector.
    private boolean listing;
    private void preparationList(String type, Map<String, Object> message) {
        if (listing) { send("PREPARE_ERROR", Map.of("message", "Espera a que termine la consulta de ZIP")); return; }
        String zip = type.equals("PREPARE_ENTRIES") ? Json.text(message, "zip") : "";
        listing = true;
        if (!server.submit(() -> {
            Map<String, Object> response;
            try { response = Map.of("zip", zip, "items", zip.isEmpty() ? server.preparation.archives() : server.preparation.entries(zip)); }
            catch (Exception error) { response = Map.of("message", error.getMessage() == null ? "No se pudo leer el ZIP" : error.getMessage()); }
            Map<String, Object> result = response;
            server.complete(() -> { listing = false; send(result.containsKey("message") ? "PREPARE_ERROR" : type, result); });
        })) { listing = false; send("PREPARE_ERROR", Map.of("message", "Servidor ocupado; intenta otra vez")); }
    }
    void catalogUpdated() {
        if (!hello) return;
        send("CATALOG_UPDATE", Map.of("images", server.images.values().stream().map(item -> Map.of(
                "imageId", item.store().imageId, "name", item.name(), "width", item.store().width,
                "height", item.store().height, "levels", item.store().levels)).toList()));
    }

    private void cacheState(Map<String, Object> message) {
        String operation = Json.text(message, "operation");
        ClientCache.Entry entry = null;
        if (!operation.equals("CLEAR")) {
            String imageId = Json.text(message, "imageId"), blockId = Json.text(message, "blockId");
            PribServer.Prepared selected = server.images.get(imageId);
            if (selected == null) throw new IllegalArgumentException("Imagen de caché desconocida");
            String[] parts = blockId.split(":", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Identificador de bloque inválido");
            int level = Integer.parseInt(parts[0]), col = Integer.parseInt(parts[1]), row = Integer.parseInt(parts[2]);
            int w = selected.store().width(level), h = selected.store().height(level);
            if (col < 0 || row < 0 || col >= (w+127)/128 || row >= (h+127)/128
                    || !blockId.equals(level + ":" + col + ":" + row)) throw new IllegalArgumentException("Bloque de caché inválido");
            int bw = Math.min(128, w-col*128), bh = Math.min(128, h-row*128);
            if (operation.equals("PUT") && (Json.integer(message, "width") != bw || Json.integer(message, "height") != bh))
                throw new IllegalArgumentException("Geometría de caché inválida");
            entry = new ClientCache.Entry(imageId, blockId, bw, bh, operation.equals("PUT") ? Json.text(message, "hash") : "",
                    operation.equals("PUT") && message.containsKey("similarity") ? Json.text(message, "similarity") : "");
        }
        cache.update(counter(message, "cacheSeq"), operation, entry);
        waitingCredit = false;
    }

    private void recover(Map<String, Object> message) {
        int generation = Json.integer(message, "viewId");
        if (generation > 0 && generation < viewId || generation == viewId && cancelled.get()) return;
        if (generation != viewId) throw new IllegalArgumentException("RECOVER de vista desconocida");
        int id = Json.integer(message, "transferId"); String reason = Json.text(message, "reason");
        if (!Set.of("BASE_MISSING", "HASH_MISMATCH", "DELTA_FAILED", "CACHE_MISS").contains(reason))
            throw new IllegalArgumentException("Motivo RECOVER inválido");
        Sent sent = awaiting.get(id);
        if (sent == null || reason.equals("DELTA_FAILED") && !sent.mode().equals("DELTA")
                || !reason.equals("HASH_MISMATCH") && sent.mode().equals("FULL"))
            throw new IllegalArgumentException("RECOVER no corresponde a una transferencia pendiente");
        String key = target(sent.tile()).key(); int count = retries.getOrDefault(key, 0)+1;
        if (count > MAX_RECOVERIES) throw new IllegalArgumentException("Límite de recuperación agotado");
        retries.put(key, count); awaiting.remove(id); recoveries++;
        if (sent.base() != null) cache.forget(sent.base().imageId(), sent.base().blockId());
        cache.forget(image.store().imageId, target(sent.tile()).blockId());
        forceFull.add(key); pending.add(sent.tile()); done = false; waitingCredit = false;
        send("RECOVERY_ACCEPTED", Map.of("viewId", viewId, "transferId", id, "blockId", target(sent.tile()).blockId(),
                "reason", reason, "attempt", count, "mode", "FULL"));
    }

    private void cancel(int generation) {
        if (generation < 1 || generation > viewId) throw new IllegalArgumentException("CANCEL de vista desconocida");
        int tasks = 0, bytes = 0;
        if (generation == viewId && !cancelled.get()) {
            tasks = pending.size() + (working ? 1 : 0); bytes = ready == null ? 0 : ready.data().length;
            cancelled.set(true); pending.clear(); ready = null; awaiting.clear(); retries.clear(); forceFull.clear(); planning = false;
            planned = false; done = true; waitingCredit = false;
        }
        // Se encola detrás de mensajes antiguos: al recibirlo ya no quedan bases antiguas en tránsito.
        send("CANCELLED", Map.of("viewId", generation, "cancelledTasks", tasks, "unsentBytes", bytes,
                "outstandingBytes", credit.outstanding()));
    }

    private void view(Map<String, Object> message) {
        PribServer.Prepared requested = server.images.get(Json.text(message, "imageId"));
        if (requested == null) throw new IllegalArgumentException("Imagen no encontrada");
        int next = Json.integer(message, "viewId"), level = Json.integer(message, "level");
        int x = Json.integer(message, "x"), y = Json.integer(message, "y");
        int width = Json.integer(message, "width"), height = Json.integer(message, "height");
        ImageStore store = requested.store();
        if (next <= viewId || x < 0 || y < 0 || width <= 0 || height <= 0 || width > 3840 || height > 2160
                || (long)x + width > store.width(level) || (long)y + height > store.height(level))
            throw new IllegalArgumentException("Vista fuera de límites (máximo 3840 x 2160)");
        // Conservar descriptores del solapamiento; la nueva generación recalcula modo y prioridad.
        Map<String, ImageStore.Descriptor> overlap = new HashMap<>();
        if (requested == image) for (Tile tile : pending) if (tile.info() != null)
            overlap.put(tile.level() + ":" + tile.column() + ":" + tile.row(), tile.info());
        if (viewId > 0 && !cancelled.get()) cancel(viewId);
        viewId = next; image = requested; vx = x; vy = y; vw = width; vh = height;
        pending.clear(); awaiting.clear(); ready = null; delivered = full = reuse = ref = delta = recoveries = deltaCandidates = 0;
        deltaNanos = 0; retries.clear(); forceFull.clear();
        packetBytes = avoidedBytes = 0; done = false; waitingCredit = false;
        cancelled = new AtomicBoolean(); planning = true; planned = false;
        // El plan solo guarda coordenadas y un registro por bloque visible (máximo 558).
        for (int row = y/128; row <= (y+height-1)/128; row++)
            for (int col = x/128; col <= (x+width-1)/128; col++) pending.add(new Tile(col, row, level, overlap.get(level + ":" + col + ":" + row)));
        send("VIEW_ACCEPTED", Map.of("viewId", viewId, "imageId", store.imageId, "level", level,
                "x", x, "y", y, "width", width, "height", height, "blocks", required = pending.size()));
    }

    void pump() {
        if (!hello || working || client.closing || !client.output.isEmpty() || cancelled.get()) return;
        if (planning) { plan(); return; }
        if (!planned || done) return;
        if (ready != null) {
            if (ready.base() != null && !cache.contains(ready.base())) {
                forceFull.add(target(ready.tile()).key()); pending.add(ready.tile()); ready = null;
                waitingCredit = false; return; // Se invalidó la base durante la preparación/espera.
            }
            // Una referencia elegible puede avanzar mientras el bloque preparado espera crédito.
            Choice reference = referenceChoice();
            double preparedScore = priority(ready.tile(), ready.data().length);
            if (reference != null && (!credit.canSend(ready.data().length) || reference.score() > preparedScore)) {
                pending.remove(reference.tile()); transferId++; transmit(reference.packet()); finish(); return;
            }
            if (!credit.canSend(ready.data().length)) { waitingCredit = true; status(); return; }
            transmit(ready); ready = null; finish(); return;
        }
        if (pending.isEmpty()) { finish(); return; }
        if (!credit.initialized()) { status(); return; }
        if (awaiting.size() >= 1024) { client.protocolError("Demasiados bloques sin ACK"); return; }
        Choice best = null;
        for (Tile tile : pending) {
            ClientCache.Entry target = target(tile), base = forceFull.contains(target.key()) ? null : cache.base(target);
            PendingBlock packet = packet(tile, base, null, transferId+1);
            int cost = packet.data().length + (base == null ? packet.rawBytes() : 0);
            if (!credit.canSend(cost) && (base != null || forceFull.contains(target.key()) || cache.similarity().isEmpty())) continue;
            double score = priority(tile, cost);
            if (best == null || score > best.score()) best = new Choice(tile, base, packet, score);
        }
        if (best == null) { waitingCredit = true; status(); return; }
        waitingCredit = false;
        Tile tile = best.tile();
        if (best.base() != null) {
            pending.remove(tile); transferId++; transmit(best.packet()); finish(); return;
        }
        int generation = viewId; PribServer.Prepared selected = image; AtomicBoolean token = cancelled;
        SimilarityIndex index = forceFull.contains(target(tile).key()) ? new SimilarityIndex(List.of()) : cache.similarity();
        working = true;
        boolean accepted = server.submit(() -> {
            try {
                if (token.get()) { server.complete(() -> working = false); return; }
                ImageStore.Block block = selected.store().readBlock(tile.level(), tile.column(), tile.row());
                long started = System.nanoTime();
                String signature = SimilarityIndex.signature(block.rgb(), block.width(), block.height());
                List<Difference> differences = new ArrayList<>(); int evaluated = 0;
                for (ClientCache.Entry base : index.candidates(signature, block.width(), block.height())) {
                    if (token.get()) break;
                    evaluated++;
                    PribServer.Prepared baseImage = server.images.get(base.imageId());
                    String[] parts = base.blockId().split(":");
                    ImageStore.Block source;
                    try { source = baseImage.store().readBlock(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
                    catch (Exception invalidBase) { continue; } // Una base dañada nunca se usa para reconstruir.
                    if (!source.hash().equals(base.hash())) continue;
                    byte[] difference = DeltaCodec.encode(source.rgb(), block.rgb());
                    if (difference != null) differences.add(new Difference(base, difference));
                }
                long elapsed = System.nanoTime()-started; int candidates = evaluated;
                server.complete(() -> {
                    working = false;
                    if (!client.key.isValid() || client.closing || token.get() || generation != viewId) return;
                    if (!block.hash().equals(tile.info().hash())) { client.protocolError("Almacén modificado durante la vista"); return; }
                    int id = ++transferId;
                    PendingBlock complete = packet(tile, null, block.rgb(), id), chosen = complete;
                    for (Difference difference : differences) {
                        if (!cache.contains(difference.base())) continue;
                        PendingBlock alternative = packet(tile, difference.base(), difference.payload(), id);
                        if (alternative.data().length < chosen.data().length && DeltaCodec.worthwhile(complete.data().length, alternative.data().length)) chosen = alternative;
                    }
                    deltaCandidates += candidates; deltaNanos += elapsed; ready = chosen;
                });
            } catch (Exception error) { failed(generation, error); }
        });
        if (accepted) pending.remove(tile); else working = false;
    }

    private void plan() {
        List<Tile> tiles = List.copyOf(pending); int generation = viewId;
        PribServer.Prepared selected = image; AtomicBoolean token = cancelled;
        working = true;
        boolean accepted = server.submit(() -> {
            try {
                List<Tile> result = new ArrayList<>();
                for (Tile tile : tiles) {
                    if (token.get()) break;
                    result.add(new Tile(tile.column(), tile.row(), tile.level(), tile.info() == null ? selected.store().describe(tile.level(), tile.column(), tile.row()) : tile.info()));
                }
                server.complete(() -> {
                    working = false;
                    if (!client.key.isValid() || client.closing || token.get() || generation != viewId) return;
                    pending.clear(); pending.addAll(result); planning = false; planned = true;
                });
            } catch (Exception error) { failed(generation, error); }
        });
        if (!accepted) working = false;
    }
    private void failed(int generation, Exception error) {
        server.complete(() -> {
            working = false;
            if (client.key.isValid() && !client.closing && generation == viewId && !cancelled.get())
                client.protocolError("No se pudo leer o verificar el bloque: " + error.getMessage());
        });
    }
    private double priority(Tile tile, int bytes) {
        return BlockPriority.score(tile.column()*128, tile.row()*128, tile.info().width(), tile.info().height(), vx, vy, vw, vh, bytes);
    }
    private Choice referenceChoice() {
        Choice best = null;
        for (Tile tile : pending) {
            if (forceFull.contains(target(tile).key())) continue;
            ClientCache.Entry base = cache.base(target(tile)); if (base == null) continue;
            PendingBlock packet = packet(tile, base, null, transferId+1);
            if (!credit.canSend(packet.data().length)) continue;
            double score = priority(tile, packet.data().length);
            if (best == null || score > best.score()) best = new Choice(tile, base, packet, score);
        }
        return best;
    }
    private ClientCache.Entry target(Tile tile) {
        return new ClientCache.Entry(image.store().imageId, tile.level()+":"+tile.column()+":"+tile.row(),
                tile.info().width(), tile.info().height(), tile.info().hash());
    }
    private PendingBlock packet(Tile tile, ClientCache.Entry base, byte[] rgb, int id) {
        ClientCache.Entry target = target(tile);
        String mode = base == null ? "FULL" : rgb != null ? "DELTA" : base.key().equals(target.key()) ? "REUSE" : "REF";
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("version", 1); header.put("type", base == null ? "BLOCK_FULL" : mode.equals("DELTA") ? "BLOCK_DELTA" : "BLOCK_REF");
        header.put("sessionId", sessionId); header.put("viewId", viewId); header.put("transferId", id);
        header.put("imageId", target.imageId()); header.put("level", tile.level()); header.put("blockId", target.blockId());
        header.put("x", tile.column()*128); header.put("y", tile.row()*128);
        header.put("width", target.width()); header.put("height", target.height());
        header.put("format", "RGB8"); header.put("codec", base == null ? "RAW" : mode.equals("DELTA") ? "XOR_RUNS_1" : "CACHE"); header.put("mode", mode);
        header.put("payloadLength", rgb != null ? rgb.length : base == null ? target.bytes() : 0); header.put("expectedHash", target.hash());
        if (base != null) { header.put("baseImageId", base.imageId()); header.put("baseId", base.blockId()); header.put("baseHash", base.hash()); }
        byte[] json = Json.encode(header).getBytes(StandardCharsets.UTF_8);
        if (json.length > 4096) throw new IllegalArgumentException("Cabecera de bloque demasiado grande");
        ByteBuffer data = ByteBuffer.allocate(4 + json.length + (rgb == null ? 0 : rgb.length));
        data.putInt(json.length).put(json); if (rgb != null) data.put(rgb);
        return new PendingBlock(id, target.hash(), data.array(), mode, target.bytes(), rgb == null ? 0 : rgb.length, tile, base);
    }
    private void transmit(PendingBlock block) {
        credit.debit(block.data().length); awaiting.put(block.id(), new Sent(block.tile(), block.base(), block.mode()));
        client.enqueue(WebSocketFrames.frame(2, block.data())); delivered++; packetBytes += block.data().length;
        switch (block.mode()) { case "FULL" -> full++; case "REUSE" -> reuse++; case "REF" -> ref++; case "DELTA" -> delta++; }
        if (!block.mode().equals("FULL")) avoidedBytes += block.rawBytes()-block.payloadBytes();
    }
    private void finish() {
        if (planned && pending.isEmpty() && ready == null && !working && awaiting.isEmpty() && !done) {
            done = true;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("viewId", viewId); summary.put("blocks", required); summary.put("transmissions", delivered);
            summary.put("full", full); summary.put("reuse", reuse); summary.put("ref", ref); summary.put("delta", delta);
            summary.put("pribBytes", packetBytes); summary.put("avoidedRgbBytes", avoidedBytes); summary.put("recoveries", recoveries);
            summary.put("deltaCandidates", deltaCandidates); summary.put("deltaNanos", deltaNanos); send("VIEW_DONE", summary);
        }
        status();
    }
    private static long counter(Map<String, Object> message, String field) {
        if (message.get(field) instanceof Long value) return value;
        throw new IllegalArgumentException("Falta contador: " + field);
    }
    private void status() {
        String state = !credit.initialized() ? "WAIT_INIT"
                : waitingCredit || ready != null && !credit.canSend(ready.data().length) ? "WAIT_CREDIT"
                : done || pending.isEmpty() && ready == null && !working ? "IDLE" : "SENDING";
        Map<String, Object> fields = Map.of("capacityBytes", credit.capacity(), "availableBytes", credit.available(),
                "outstandingBytes", credit.outstanding(), "releasedBytes", credit.released(), "grantId", credit.grantId(), "state", state);
        String signature = Json.encode(fields);
        if (!signature.equals(lastStatus)) { lastStatus = signature; send("CREDIT_STATUS", fields); }
    }
    private void send(String type, Map<String, ?> fields) {
        Map<String, Object> data = new LinkedHashMap<>(fields);
        data.put("version", 1); data.put("type", type); data.put("sessionId", sessionId); client.text(data);
    }
}
