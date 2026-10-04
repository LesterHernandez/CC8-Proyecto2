package prib;

/** Prioridad por área visible, centro de interés y coste PRIB exacto.
 * Todos los candidatos son visibles y del mismo nivel: detalle no discrimina.
 * La cola es finita y cada candidato enviado se retira, evitando inanición.
 */
final class BlockPriority {
    private static double weight(String name, double fallback) {
        double value = Double.parseDouble(System.getProperty("prib.priority." + name, "" + fallback));
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Peso de prioridad inválido");
        return value;
    }
    static final double VISIBILITY = weight("visibility", 4), PROXIMITY = weight("proximity", 2), COST = weight("cost", 3);
    static double score(int bx, int by, int bw, int bh, int x, int y, int width, int height, int packetBytes) {
        double visible = (double)(Math.min(bx+bw, x+width)-Math.max(bx,x))
                * (Math.min(by+bh, y+height)-Math.max(by,y)) / (bw*bh);
        double dx = (bx+bw/2.0 - (x+width/2.0)) / Math.max(128, width);
        double dy = (by+bh/2.0 - (y+height/2.0)) / Math.max(128, height);
        double proximity = 1 / (1 + Math.hypot(dx, dy));
        return VISIBILITY * visible + PROXIMITY * proximity - COST * packetBytes / CreditWindow.MAX_PACKET;
    }
}
