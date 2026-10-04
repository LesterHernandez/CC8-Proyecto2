package prib;

import java.util.*;

/** Firma de medias RGB cuantizadas: global y cuatro cuadrantes (15 nibbles).
 * La firma solo elige candidatos. Nunca representa los píxeles finales.
 * Índice inmutable: puede compartirse con trabajadores sin modificar la sesión.
 */
final class SimilarityIndex {
    static final int MAX_CANDIDATES = 4, MAX_SCANNED = 64;
    private final Map<String, List<ClientCache.Entry>> buckets = new HashMap<>();
    SimilarityIndex(Collection<ClientCache.Entry> entries) {
        for (ClientCache.Entry entry : entries) if (!entry.similarity().isEmpty())
            buckets.computeIfAbsent(key(entry.width(), entry.height(), entry.similarity().substring(0,3)), unused -> new ArrayList<>()).add(entry);
    }
    boolean isEmpty() { return buckets.isEmpty(); }
    private static String key(int width, int height, String color) { return width + ":" + height + ":" + color; }
    static String signature(byte[] rgb, int width, int height) {
        if (width < 1 || width > 128 || height < 1 || height > 128 || rgb.length != width*height*3)
            throw new IllegalArgumentException("RGB para firma inválido");
        long[][] sums = new long[5][3]; int[] counts = new int[5];
        for (int y=0; y<height; y++) for (int x=0; x<width; x++) {
            int quadrant = 1 + (y*2/height)*2 + x*2/width, offset = (y*width+x)*3;
            counts[0]++; counts[quadrant]++;
            for (int c=0; c<3; c++) { int value = rgb[offset+c]&255; sums[0][c] += value; sums[quadrant][c] += value; }
        }
        StringBuilder out = new StringBuilder();
        for (int group=0; group<5; group++) for (int c=0; c<3; c++)
            out.append(Character.forDigit((int)(sums[group][c]/Math.max(1,counts[group]))/16, 16));
        return out.toString();
    }
    List<ClientCache.Entry> candidates(String signature, int width, int height) {
        int r = Character.digit(signature.charAt(0),16), g = Character.digit(signature.charAt(1),16), b = Character.digit(signature.charAt(2),16);
        ArrayList<ClientCache.Entry> found = new ArrayList<>(); int scanned = 0;
        // Bucket exacto primero, luego vecinos de color global; nunca más de 27 buckets.
        ArrayList<String> colors = new ArrayList<>(); colors.add(signature.substring(0,3));
        for (int dr=-1; dr<=1; dr++) for (int dg=-1; dg<=1; dg++) for (int db=-1; db<=1; db++) {
            int rr=r+dr, gg=g+dg, bb=b+db;
            if (rr<0 || rr>15 || gg<0 || gg>15 || bb<0 || bb>15 || dr==0 && dg==0 && db==0) continue;
            colors.add(""+Character.forDigit(rr,16)+Character.forDigit(gg,16)+Character.forDigit(bb,16));
        }
        outer: for (String color : colors) {
            List<ClientCache.Entry> bucket = buckets.getOrDefault(key(width,height,color), List.of());
            // Preferir declaraciones recientes sin depender de un historial ilimitado.
            for (int i=bucket.size()-1; i>=0; i--) {
                ClientCache.Entry entry = bucket.get(i);
                if (distance(signature, entry.similarity()) <= 24) found.add(entry);
                if (++scanned == MAX_SCANNED) break outer;
            }
        }
        found.sort(Comparator.comparingInt(entry -> distance(signature, entry.similarity())));
        return List.copyOf(found.subList(0, Math.min(found.size(), MAX_CANDIDATES)));
    }
    private static int distance(String a, String b) {
        int sum=0; for (int i=0; i<15; i++) sum += Math.abs(Character.digit(a.charAt(i),16)-Character.digit(b.charAt(i),16)); return sum;
    }
}
