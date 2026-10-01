package prib;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

/** Lector incremental para el formato observado: PNG RGB8, sin entrelazado.
 * Conserva dos filas; nunca crea una imagen completa en memoria.
 * Los cinco filtros PNG se revierten antes de entregar píxeles RGB canónicos.
 */
public final class PngRows implements AutoCloseable {
    private final InflaterInputStream pixels;
    private final Chunks chunks;
    public final int width, height;
    private byte[] previous, current;
    private int row;

    public PngRows(InputStream source) throws IOException {
        // Firma e IHDR describen el formato antes de reservar memoria.
        DataInputStream input = new DataInputStream(new BufferedInputStream(source, 65536));
        if (input.readLong() != 0x89504e470d0a1a0aL) throw new IOException("Firma PNG incorrecta");
        if (input.readInt() != 13 || input.readInt() != 0x49484452) throw new IOException("IHDR inválido");
        byte[] header = input.readNBytes(13);
        if (header.length != 13) throw new EOFException();
        // PNG calcula CRC sobre el nombre del chunk y sus datos, no sobre la longitud.
        CRC32 crc = new CRC32(); crc.update("IHDR".getBytes(StandardCharsets.US_ASCII)); crc.update(header);
        if (input.readInt() != (int)crc.getValue()) throw new IOException("CRC de IHDR incorrecto");
        DataInputStream h = new DataInputStream(new ByteArrayInputStream(header));
        width = h.readInt(); height = h.readInt();
        if (width <= 0 || height <= 0 || width > 200000) throw new IOException("Dimensiones fuera del límite de esta prueba");
        if (h.readUnsignedByte()!=8 || h.readUnsignedByte()!=2 || h.readUnsignedByte()!=0 || h.readUnsignedByte()!=0 || h.readUnsignedByte()!=0)
            throw new IOException("Se requiere PNG RGB de 8 bits, sin entrelazado");
        // Tres canales por píxel. La fila anterior empieza en cero para los filtros.
        previous = new byte[Math.multiplyExact(width,3)]; current = new byte[previous.length];
        // IDAT contiene el flujo zlib; InflaterInputStream lo descomprime a demanda.
        chunks = new Chunks(input); pixels = new InflaterInputStream(chunks);
    }

    /** El arreglo devuelto se reutiliza: el consumidor debe copiarlo antes de next(). */
    public byte[] next() throws IOException {
        if (row == height) return null;
        int filter = pixels.read();
        if (filter < 0 || filter > 4) throw new IOException("Filtro PNG inválido o datos truncados");
        // Cada fila comienza con un filtro, seguido por exactamente width * 3 bytes.
        new DataInputStream(pixels).readFully(current);
        // None ya contiene RGB original: evita recorrer otra vez filas grandes sin filtro.
        for (int x=0; filter != 0 && x<current.length; x++) {
            // Vecinos del mismo canal: izquierda (a), arriba (b), diagonal (c).
            int a = x>=3 ? current[x-3]&255 : 0;
            int b = previous[x]&255;
            int c = x>=3 ? previous[x-3]&255 : 0;
            int prediction = switch(filter) {
                case 0 -> 0;           // None: dato original.
                case 1 -> a;           // Sub: diferencia respecto a la izquierda.
                case 2 -> b;           // Up: diferencia respecto a la fila anterior.
                case 3 -> (a+b)/2;     // Average: promedio de los dos vecinos.
                default -> paeth(a,b,c); // Paeth: selecciona el vecino más cercano.
            };
            current[x] = (byte)((current[x]&255)+prediction);
        }
        // Intercambiamos buffers en lugar de reservar una fila nueva en cada llamada.
        byte[] result=current; current=previous; previous=result; row++;
        return result;
    }

    /** Predictor PNG: escoge el vecino más cercano a a + b - c, con desempate a, b, c. */
    private static int paeth(int a,int b,int c) {
        int p=a+b-c, pa=Math.abs(p-a), pb=Math.abs(p-b), pc=Math.abs(p-c);
        return pa<=pb && pa<=pc ? a : pb<=pc ? b : c;
    }

    /** Verifica fin de zlib, CRC de los chunks restantes e IEND. */
    public void finish() throws IOException {
        if (row!=height) throw new IOException("Faltan filas por consumir");
        if (pixels.read()!=-1) throw new IOException("Sobran píxeles descomprimidos");
        // El inflador puede terminar antes de consumir IEND; completar valida su CRC.
        while(chunks.read()!=-1) { }
    }
    public void close() throws IOException { pixels.close(); }

    /** Concatena IDAT sin retener sus contenidos. Cada chunk se valida con CRC. */
    private static final class Chunks extends InputStream {
        final DataInputStream in;
        final CRC32 crc=new CRC32();
        int remaining; // Bytes de datos que faltan en el chunk actual.
        boolean active, end, seenData, afterData; // Estado del recorrido, no de la imagen.
        String type;
        final byte[] discard=new byte[8192];
        Chunks(DataInputStream in) { this.in=in; }
        private boolean prepare() throws IOException {
            while(!end) {
                // Al terminar datos, el siguiente entero es su CRC. IEND cierra el PNG.
                if(active && remaining==0) {
                    if(in.readInt()!=(int)crc.getValue()) throw new IOException("CRC incorrecto: "+type);
                    active=false;
                    if(type.equals("IEND")) { end=true; return false; }
                }
                // Abrir el siguiente chunk sin cargar su contenido completo.
                if(!active) {
                    remaining=in.readInt();
                    if(remaining<0) throw new IOException("Chunk demasiado grande");
                    byte[] name=in.readNBytes(4);
                    if(name.length!=4) throw new EOFException();
                    type=new String(name,StandardCharsets.US_ASCII); crc.reset(); crc.update(name); active=true;
                    if(type.equals("IDAT")) {
                        // Los IDAT deben ser consecutivos para formar un único flujo.
                        if(afterData) throw new IOException("IDAT no consecutivos");
                        seenData=true;
                    } else {
                        if(seenData) afterData=true;
                        if(type.equals("IEND") && (remaining!=0 || !seenData)) throw new IOException("IEND inválido");
                        if(!type.equals("IEND") && !type.equals("PLTE") && Character.isUpperCase(type.charAt(0)))
                            throw new IOException("Chunk crítico no soportado: "+type);
                    }
                }
                if(remaining==0) continue;
                if(type.equals("IDAT")) return true;
                // Saltar metadatos en fragmentos pequeños, conservando la validación CRC.
                int n=in.read(discard,0,Math.min(discard.length,remaining));
                if(n<0) throw new EOFException(); crc.update(discard,0,n); remaining-=n;
            }
            return false;
        }
        // Entregar como máximo los bytes restantes de este IDAT al inflador.
        public int read(byte[] b,int off,int len) throws IOException {
            if(len==0) return 0;
            if(!prepare()) return -1;
            int n=in.read(b,off,Math.min(len,remaining));
            if(n<0) throw new EOFException(); crc.update(b,off,n); remaining-=n; return n;
        }
        public int read() throws IOException { byte[] b=new byte[1]; return read(b,0,1)<0?-1:b[0]&255; }
        public void close() throws IOException { in.close(); }
    }
}
