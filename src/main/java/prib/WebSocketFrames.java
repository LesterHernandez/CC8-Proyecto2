package prib;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.function.BiConsumer;

/** Framing WebSocket RFC 6455. Independiente del significado de los comandos PRIB.
 * Entrada con máscara, fragmentación de texto y ping/pong; sin extensiones.
 */
public final class WebSocketFrames {
    public static final int MAX_CONTROL = 8192;
    private ByteArrayOutputStream fragments;

    public static String utf8(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException error) { throw new IllegalArgumentException("UTF-8 inválido"); }
    }

    /** consume() recibe el buffer en modo lectura y deja incompletas las tramas parciales. */
    public void consume(ByteBuffer in, BiConsumer<Integer, byte[]> receive) {
        while (in.remaining() >= 2) {
            in.mark(); int first = in.get() & 255, second = in.get() & 255;
            int opcode = first & 15; boolean fin = (first & 128) != 0;
            if ((first & 112) != 0 || (second & 128) == 0) fail();
            int code = second & 127; long length = code;
            if (code == 126) {
                if (in.remaining() < 2) { in.reset(); return; }
                length = in.getShort() & 65535; if (length < 126) fail();
            } else if (code == 127) {
                if (in.remaining() < 8) { in.reset(); return; }
                length = in.getLong(); if (length < 65536) fail();
            }
            if (length > MAX_CONTROL || (opcode >= 8 && (!fin || length > 125))) fail();
            if (in.remaining() < length + 4) { in.reset(); return; }
            byte[] mask = new byte[4]; in.get(mask);
            byte[] payload = new byte[(int)length]; in.get(payload);
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
            if (opcode >= 8) {
                if (opcode != 8 && opcode != 9 && opcode != 10) fail();
                if (opcode == 8) validateClose(payload);
                receive.accept(opcode, payload); if (opcode == 8) return;
            } else {
                // PRIB acepta controles de texto; los datos binarios van solo servidor → cliente.
                if (opcode == 1) {
                    if (fragments != null) fail();
                    if (fin) { receive.accept(1, payload); continue; }
                    fragments = new ByteArrayOutputStream();
                } else if (opcode != 0 || fragments == null) { fail(); }
                if (fragments.size() + payload.length > MAX_CONTROL) fail();
                fragments.writeBytes(payload);
                if (fin) { byte[] message = fragments.toByteArray(); fragments = null; receive.accept(1, message); }
            }
        }
    }
    private static void validateClose(byte[] payload) {
        if (payload.length == 1) fail();
        if (payload.length >= 2) {
            int code = ((payload[0]&255)<<8) | (payload[1]&255);
            if (code < 1000 || code >= 5000 || code == 1004 || code == 1005 || code == 1006
                    || (code >= 1015 && code < 3000)) fail();
            utf8(java.util.Arrays.copyOfRange(payload, 2, payload.length));
        }
    }
    private static void fail() { throw new IllegalArgumentException("Trama WebSocket inválida o demasiado grande"); }

    /** El servidor nunca aplica máscara. Incluye longitudes extendidas cuando hacen falta. */
    public static ByteBuffer frame(int opcode, byte[] payload) {
        int header = payload.length < 126 ? 2 : payload.length <= 65535 ? 4 : 10;
        ByteBuffer out = ByteBuffer.allocate(header + payload.length); out.put((byte)(128 | opcode));
        if (header == 2) out.put((byte)payload.length);
        else if (header == 4) { out.put((byte)126); out.putShort((short)payload.length); }
        else { out.put((byte)127); out.putLong(payload.length); }
        out.put(payload); return out.flip();
    }
}
