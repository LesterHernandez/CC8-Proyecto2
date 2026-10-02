package prib;

/** Ventana de bytes PRIB por sesión. No cuenta controles ni cabeceras WebSocket/TCP.
 * ACK y créditos son independientes: solo liberar capacidad repone esta ventana.
 */
final class CreditWindow {
    static final int MAX_PACKET = 4 + 4096 + 128 * 128 * 3;
    static final int MAX_CAPACITY = 1024 * 1024;
    // Enteros representables exactamente también en JavaScript.
    static final long MAX_COUNTER = 9_007_199_254_740_991L;
    private int capacity;
    private long sent, released, grantId;

    boolean initialized() { return capacity != 0; }
    int available() { return (int)(capacity + released - sent); }
    long outstanding() { return sent - released; }
    int capacity() { return capacity; }
    long released() { return released; }
    long grantId() { return grantId; }

    void initialize(int bytes) {
        if (bytes < MAX_PACKET || bytes > MAX_CAPACITY)
            throw new IllegalArgumentException("Capacidad fuera de límites: " + MAX_PACKET + " a " + MAX_CAPACITY);
        if (initialized() && bytes != capacity) throw new IllegalArgumentException("CREDIT_INIT distinto en la misma sesión");
        capacity = bytes; // Repetir el mismo INIT no reinicia los contadores.
    }
    boolean canSend(int bytes) { return initialized() && bytes > 0 && bytes <= available(); }
    void debit(int bytes) {
        if (!canSend(bytes) || bytes > MAX_PACKET || sent > MAX_COUNTER-bytes)
            throw new IllegalArgumentException("Crédito insuficiente o contador agotado; reconectar");
        sent += bytes;
    }
    void grant(long id, long total) {
        if (!initialized() || id <= 0 || id > MAX_COUNTER || total < 0 || total > sent)
            throw new IllegalArgumentException("Concesión de crédito inválida");
        // Una concesión acumulativa más reciente incluye todas las anteriores.
        if (id < grantId && total <= released) return;
        if (id == grantId && total == released) return;
        if (id <= grantId || total < released)
            throw new IllegalArgumentException("Concesión contradictoria");
        grantId = id; released = total;
    }
}
