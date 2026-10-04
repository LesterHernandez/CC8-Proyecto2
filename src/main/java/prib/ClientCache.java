package prib;

import java.util.*;

/** Inventario declarado por un cliente; nunca contiene los píxeles de la imagen.
 * PUT/DROP ordenados evitan asumir que un ACK equivale a conservar el bloque.
 */
final class ClientCache {
    static final int MAX_ENTRIES = 1024, MAX_BYTES = 16 * 1024 * 1024;
    record Entry(String imageId, String blockId, int width, int height, String hash, String similarity) {
        Entry(String imageId, String blockId, int width, int height, String hash) { this(imageId, blockId, width, height, hash, ""); }
        String key() { return imageId + "/" + blockId; }
        int bytes() { return width * height * 3; }
    }
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<String>> contents = new HashMap<>();
    private SimilarityIndex similarity;
    private long sequence;
    private int bytes;

    void update(long next, String operation, Entry item) {
        if (next != sequence + 1) throw new IllegalArgumentException("Secuencia CACHE_STATE inválida");
        if (operation.equals("CLEAR")) { entries.clear(); contents.clear(); bytes = 0; }
        else if (operation.equals("DROP")) {
            Entry old = entries.remove(item.key()); if (old != null) { bytes -= old.bytes(); unindex(old); }
        } else if (operation.equals("PUT")) {
            if (item.width() < 1 || item.width() > 128 || item.height() < 1 || item.height() > 128
                    || !item.hash().matches("[0-9a-f]{64}") || !item.similarity().isEmpty() && !item.similarity().matches("[0-9a-f]{15}")) throw new IllegalArgumentException("Entrada de caché inválida");
            Entry old = entries.get(item.key());
            int total = bytes + item.bytes() - (old == null ? 0 : old.bytes());
            if (total > MAX_BYTES || old == null && entries.size() >= MAX_ENTRIES)
                throw new IllegalArgumentException("Inventario de caché excedido");
            if (old != null) unindex(old);
            entries.put(item.key(), item); bytes = total;
            contents.computeIfAbsent(signature(item), key -> new LinkedHashSet<>()).add(item.key());
        } else throw new IllegalArgumentException("Operación CACHE_STATE inválida");
        sequence = next; similarity = null;
    }
    Entry base(Entry target) {
        Entry exact = entries.get(target.key());
        if (matches(exact, target)) return exact;
        // Índice exacto por hash y geometría; no recorre todos los bloques pendientes.
        Set<String> candidates = contents.get(signature(target));
        return candidates == null ? null : entries.get(candidates.iterator().next());
    }
    private static String signature(Entry item) { return item.width() + ":" + item.height() + ":" + item.hash(); }
    private void unindex(Entry item) {
        String signature = signature(item); Set<String> bucket = contents.get(signature);
        bucket.remove(item.key()); if (bucket.isEmpty()) contents.remove(signature);
    }
    private static boolean matches(Entry a, Entry b) {
        return a != null && a.width() == b.width() && a.height() == b.height() && a.hash().equals(b.hash());
    }
    SimilarityIndex similarity() {
        if (similarity == null) similarity = new SimilarityIndex(entries.values());
        return similarity;
    }
    boolean contains(Entry entry) { return entry.equals(entries.get(entry.key())); }
    void forget(String imageId, String blockId) {
        Entry old = entries.remove(imageId + "/" + blockId);
        if (old != null) { bytes -= old.bytes(); unindex(old); similarity = null; }
    }
    int bytes() { return bytes; }
    int size() { return entries.size(); }
}
