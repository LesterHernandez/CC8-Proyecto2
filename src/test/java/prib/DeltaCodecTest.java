package prib;

import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Exactitud, formatos hostiles, límites y vectores compartidos con JavaScript. */
public final class DeltaCodecTest {
    public static void main(String[] args) throws Exception {
        Random random = new Random(6006); List<Map<String, Object>> vectors = new ArrayList<>();
        for (int[] geometry : List.of(new int[]{128,128},new int[]{64,4},new int[]{1,128},new int[]{128,1})) {
            int w=geometry[0],h=geometry[1]; byte[] base=new byte[w*h*3]; random.nextBytes(base);
            byte[] target=base.clone(); target[0]^=1; target[target.length-1]^=127;
            for(int i=10;i<Math.min(80,target.length);i+=7)target[i]^=42;
            byte[] payload=DeltaCodec.encode(base,target);
            check(payload!=null && Arrays.equals(target,DeltaCodec.decode(base,payload)),"DELTA exacto y bordes");
            vectors.add(Map.of("width",w,"height",h,"base",HexFormat.of().formatHex(base),"target",HexFormat.of().formatHex(target),
                    "payload",HexFormat.of().formatHex(payload),"similarity",SimilarityIndex.signature(target,w,h)));
            byte[] trailing=Arrays.copyOf(payload,payload.length+1); reject(()->DeltaCodec.decode(base,trailing));
            reject(()->DeltaCodec.decode(base,Arrays.copyOf(payload,payload.length-1)));
            byte[] huge=payload.clone(); ByteBuffer.wrap(huge).putInt(8,Integer.MAX_VALUE); reject(()->DeltaCodec.decode(base,huge));
            byte[] negative=payload.clone(); ByteBuffer.wrap(negative).putInt(12,-1); reject(()->DeltaCodec.decode(base,negative));
        }
        for(int trial=0;trial<100;trial++) {
            byte[] base=new byte[49152];random.nextBytes(base);byte[] target=base.clone();
            for(int i=0;i<trial+1;i++)target[random.nextInt(target.length)]^=1;
            byte[] payload=DeltaCodec.encode(base,target);check(Arrays.equals(target,DeltaCodec.decode(base,payload)),"Cambios dispersos exactos");
        }
        byte[] a=new byte[49152],b=new byte[49152];random.nextBytes(a);random.nextBytes(b);
        check(DeltaCodec.encode(a,b)==null,"Diferencia sin ahorro usa FULL");
        check(!DeltaCodec.worthwhile(50000,49000) && DeltaCodec.worthwhile(50000,1000),"Ahorro de coste completo");
        check(DeltaCodec.encode(new byte[3],new byte[3])==null,"Bloque diminuto no usa DELTA");
        reject(()->DeltaCodec.decode(new byte[10],ByteBuffer.allocate(8).putInt(9).putInt(0).array()));
        byte[] overlap=ByteBuffer.allocate(26).putInt(10).putInt(2).putInt(2).putInt(1).put((byte)1).putInt(2).putInt(1).put((byte)2).array();
        reject(()->DeltaCodec.decode(new byte[10],overlap));
        ArrayList<ClientCache.Entry> entries=new ArrayList<>();String signature=SimilarityIndex.signature(a,128,128);
        for(int i=0;i<100;i++)entries.add(new ClientCache.Entry("i","0:"+i+":0",128,128,"a".repeat(64),signature));
        SimilarityIndex index=new SimilarityIndex(entries);
        check(index.candidates(signature,128,128).size()==4 && index.candidates(signature,64,128).isEmpty(),"Índice limita candidatos y geometría");
        ClientCache inventory=new ClientCache(); inventory.update(1,"PUT",entries.get(0));
        SimilarityIndex snapshot=inventory.similarity();
        check(!snapshot.isEmpty() && inventory.contains(entries.get(0)),"PUT indexa bases presentes");
        inventory.forget("i","0:0:0");check(inventory.similarity().isEmpty(),"Invalidar elimina candidato");
        check(snapshot.candidates(signature,128,128).size()==1 && !inventory.contains(entries.get(0)),"Instantánea antigua no autoriza una base eliminada");
        reject(()->inventory.update(2,"PUT",new ClientCache.Entry("i","b",128,128,"a".repeat(64),"firma-mala")));
        Files.writeString(Path.of("build/delta-vectors.json"),Json.encode(vectors));
        System.out.println("PASS DELTA: XOR exacto, 100 casos, bordes, formatos inválidos, ahorro, firmas e índice de cuatro candidatos");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void reject(Runnable task){try{task.run();throw new AssertionError("Se esperaba rechazo");}catch(IllegalArgumentException expected){}}
}
