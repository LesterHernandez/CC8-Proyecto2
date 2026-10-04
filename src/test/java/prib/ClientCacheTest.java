package prib;

/** Inventario acotado, referencias exactas y prioridad independiente de créditos. */
public final class ClientCacheTest {
    public static void main(String[] args) {
        ClientCache cache = new ClientCache();
        String hash = "a".repeat(64);
        ClientCache.Entry a = new ClientCache.Entry("image", "0:0:0", 128, 128, hash);
        ClientCache.Entry b = new ClientCache.Entry("image", "0:1:0", 128, 128, hash);
        cache.update(1, "PUT", a);
        check(cache.base(a).equals(a) && cache.base(b).equals(a), "REUSE y REF por igualdad exacta");
        check(cache.base(new ClientCache.Entry("image", "0:2:0", 64, 128, hash)) == null, "No referenciar otra geometría");
        check(cache.base(new ClientCache.Entry("image", "0:2:0", 128, 128, "b".repeat(64))) == null, "No referenciar contenido diferente");
        reject(() -> cache.update(3, "DROP", a));
        reject(() -> cache.update(2, "PUT", new ClientCache.Entry("i", "b", 128, 128, "incorrecto")));
        cache.update(2, "DROP", a); check(cache.bytes() == 0 && cache.base(b) == null, "DROP elimina suposición de caché");
        cache.update(3, "CLEAR", null);
        for (int i=0; i<341; i++) cache.update(4+i, "PUT", new ClientCache.Entry("i", ""+i, 128, 128, hash));
        reject(() -> cache.update(345, "PUT", new ClientCache.Entry("i", "otro", 128, 128, hash)));
        check(cache.bytes() <= ClientCache.MAX_BYTES, "Presupuesto RGB");
        cache.update(345, "CLEAR", null);
        for (int i=0; i<1024; i++) cache.update(346+i, "PUT", new ClientCache.Entry("i", ""+i, 1, 1, hash));
        reject(() -> cache.update(1370, "PUT", new ClientCache.Entry("i", "otro", 1, 1, hash)));
        check(cache.size() == 1024, "Límite de entradas pequeñas");
        double full = BlockPriority.score(0,0,128,128,0,0,384,128,50000);
        double ref = BlockPriority.score(0,0,128,128,0,0,384,128,600);
        double center = BlockPriority.score(128,0,128,128,0,0,384,128,50000);
        check(ref > full && center > full, "Prioridad por coste y proximidad");
        System.out.println("PASS caché: REUSE/REF exactos, geometría, secuencia, DROP, límites y prioridad");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    private static void reject(Runnable task) {
        try { task.run(); throw new AssertionError("Se esperaba rechazo"); }
        catch (IllegalArgumentException expected) { }
    }
}
