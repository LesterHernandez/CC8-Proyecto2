package prib;

import java.io.*;
import java.nio.*;

/** PRIB XOR_RUNS_1: longitud RGB, número de tramos y (offset, longitud, XOR).
 * Todos los enteros son uint32 big-endian; no hay cadenas de diferencias.
 */
final class DeltaCodec {
    static final int MAX_RGB = 128*128*3;
    static final double MIN_SAVING = Double.parseDouble(System.getProperty("prib.delta.minSaving", "0.15"));
    static final int MIN_BYTES = Integer.parseInt(System.getProperty("prib.delta.minBytes", "256"));
    static {
        if (!Double.isFinite(MIN_SAVING) || MIN_SAVING < 0 || MIN_SAVING >= 1 || MIN_BYTES < 0)
            throw new IllegalArgumentException("Política DELTA inválida");
    }
    static boolean worthwhile(int full, int delta) {
        return delta < full && full-delta >= MIN_BYTES && delta <= Math.floor(full*(1-MIN_SAVING));
    }
    static byte[] encode(byte[] base, byte[] target) {
        if (base.length != target.length || target.length < 1 || target.length > MAX_RGB)
            throw new IllegalArgumentException("Geometría DELTA incompatible");
        if (target.length <= 8) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            DataOutputStream data = new DataOutputStream(out); data.writeInt(target.length); data.writeInt(0);
            int runs = 0, i = 0;
            while (i < target.length) {
                while (i < target.length && base[i] == target[i]) i++;
                if (i == target.length) break;
                int start = i, last = i;
                // Un hueco de hasta 8 bytes cuesta menos que otra cabecera de tramo.
                for (i++; i < target.length; i++) {
                    if (base[i] != target[i]) last = i;
                    else if (i-last > 8) break;
                }
                int end = last+1;
                if (out.size()+8+end-start >= target.length) return null; // No genera temporales mayores que FULL.
                data.writeInt(start); data.writeInt(end-start);
                for (int at=start; at<end; at++) data.writeByte(base[at]^target[at]);
                runs++;
            }
            byte[] result = out.toByteArray(); ByteBuffer.wrap(result).putInt(4, runs); return result;
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    static byte[] decode(byte[] base, byte[] payload) {
        if (payload.length < 8 || payload.length > MAX_RGB) throw new IllegalArgumentException("DELTA truncado o excesivo");
        ByteBuffer data = ByteBuffer.wrap(payload); int length = data.getInt(), runs = data.getInt();
        if (length != base.length || length < 1 || length > MAX_RGB || runs < 0 || runs > length)
            throw new IllegalArgumentException("Cabecera DELTA inválida");
        byte[] rgb = base.clone(); int previous = 0;
        for (int run=0; run<runs; run++) {
            if (data.remaining() < 8) throw new IllegalArgumentException("Tramo DELTA truncado");
            int offset = data.getInt(), count = data.getInt();
            if (offset < previous || count <= 0 || offset < 0 || count > length-offset || count > data.remaining())
                throw new IllegalArgumentException("Tramo DELTA fuera de límites o superpuesto");
            for (int i=0; i<count; i++) rgb[offset+i] ^= data.get();
            previous = offset+count;
        }
        if (data.hasRemaining()) throw new IllegalArgumentException("Sobran bytes DELTA");
        return rgb;
    }
}
