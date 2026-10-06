package prib;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Una ráfaga no pierde su cantidad ni vuelca cada solicitud en la terminal. */
public final class SessionLogTest {
    public static void main(String[] args) {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            SessionLog log = new SessionLog("12345678-resto");
            for (int i = 1; i <= 100; i++) log.event("VISTA", "vista=" + i);
            log.flush(true);
            log.important("ERROR", "mensaje\ninyectado\u001b");
        } finally { System.setOut(original); }
        String text = output.toString(StandardCharsets.UTF_8);
        if (text.lines().count() != 8 || !text.contains("agrupados=94 último: vista=100")
                || !text.contains("[12345678]") || !text.contains("ERROR mensaje inyectado "))
            throw new AssertionError("Registro sin agrupación o sanitización correcta: " + text);
        System.out.println("PASS logs: ráfaga agrupada, último detalle, sesión corta y una línea por evento");
    }
}
