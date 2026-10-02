package prib;

/** Casos de frontera de la ventana; la integración de red está en PribServerTest. */
public final class CreditWindowTest {
    public static void main(String[] args) {
        CreditWindow window = new CreditWindow();
        check(!window.canSend(1), "Sin INIT no hay capacidad");
        reject(() -> window.initialize(CreditWindow.MAX_PACKET-1));
        reject(() -> window.initialize(CreditWindow.MAX_CAPACITY+1));
        window.initialize(CreditWindow.MAX_PACKET);
        // Una unidad máxima cabe exactamente; al agotarse, ni un byte adicional puede salir.
        window.debit(CreditWindow.MAX_PACKET);
        check(window.available()==0 && !window.canSend(1), "Agotamiento exacto");
        reject(() -> window.debit(1));
        window.initialize(CreditWindow.MAX_PACKET);
        check(window.available()==0, "INIT repetido no reinicia crédito");
        reject(() -> window.initialize(CreditWindow.MAX_PACKET+1));
        window.grant(1, CreditWindow.MAX_PACKET);
        window.debit(CreditWindow.MAX_PACKET);
        window.grant(1, CreditWindow.MAX_PACKET);
        check(window.available()==0, "Duplicado no duplica capacidad");
        reject(() -> window.grant(1, CreditWindow.MAX_PACKET-1));
        reject(() -> window.grant(2, 2L*CreditWindow.MAX_PACKET+1));
        reject(() -> window.grant(-1, 0));
        window.grant(3, 2L*CreditWindow.MAX_PACKET); // Una acumulativa puede saltar una concesión perdida.
        window.grant(2, CreditWindow.MAX_PACKET);
        check(window.available()==CreditWindow.MAX_PACKET, "Concesión atrasada no cambia estado");
        // Superar 2 GiB comprueba que los acumulados no desbordan un int.
        long total = 2L*CreditWindow.MAX_PACKET;
        for (int i=4; i<50000; i++) {
            window.debit(CreditWindow.MAX_PACKET); total += CreditWindow.MAX_PACKET;
            window.grant(i, total);
            check(window.available()==CreditWindow.MAX_PACKET && window.outstanding()==0, "Conservación de capacidad");
        }
        System.out.println("PASS ventana: unidad máxima, cero, límites, duplicados y acumulados superiores a 2 GiB");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Aceptó operación inválida"); }
        catch (IllegalArgumentException expected) { }
    }
}
