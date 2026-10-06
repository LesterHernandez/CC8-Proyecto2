package prib;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resúmenes de demostración: solo el Selector escribe; nunca registra payloads.
 * Una ráfaga conserva cantidad y último detalle por evento, sin una cola creciente.
 */
final class SessionLog {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final String id;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, String> latest = new LinkedHashMap<>();
    private long window = System.nanoTime();
    private int lines;
    SessionLog(String id) { this.id = id.substring(0, 8); }
    void event(String type, String detail) {
        flush(false);
        if (lines++ < 6) important(type, detail);
        else { counts.merge(type, 1, Integer::sum); latest.put(type, detail); }
    }
    void important(String type, String detail) {
        // Los nombres y errores pueden incluir texto recibido: mantener una sola línea acotada.
        String safe = detail.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
        if (safe.length() > 400) safe = safe.substring(0, 400);
        System.out.println(LocalTime.now().format(CLOCK) + " [" + id + "] " + type + " " + safe);
    }
    void flush(boolean force) {
        long now = System.nanoTime();
        if (!force && now - window < 1_000_000_000L) return;
        counts.forEach((type, count) -> important(type, "agrupados=" + count + " último: " + latest.get(type)));
        counts.clear(); latest.clear(); lines = 0; window = now;
    }
}
