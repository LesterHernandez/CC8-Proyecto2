package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.http.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Cuatro modos reales por WebSocket y recuperación de objetivos individuales. */
public final class DeltaProtocolTest {
    private record Packet(byte[] bytes, Map<String,Object> header, byte[] payload) { }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of("build"),"delta-test-");Path data=Files.createDirectory(root.resolve("data"));
        BufferedImage image=new BufferedImage(512,128,BufferedImage.TYPE_INT_RGB);Random random=new Random(6106);
        int[] base=new int[128*128]; for(int i=0;i<base.length;i++)base[i]=random.nextInt(0x1000000);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
            int rgb=base[y*128+x];image.setRGB(x,y,rgb);image.setRGB(x+128,y,rgb);
            image.setRGB(x+256,y,rgb^((x==1&&y==1 || x==127&&y==127)?0x010203:0));
            image.setRGB(x+384,y,random.nextInt(0x1000000));
        }
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);Path archive=root.resolve("source.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive))){zip.putNextEntry(new ZipEntry("delta.png"));zip.write(png.toByteArray());zip.closeEntry();}
        PrepareImage.prepare(archive,"delta.png",data.resolve("fixture"));ImageStore store=new ImageStore(data.resolve("fixture"));
        List<String> hashes=new ArrayList<>();for(int col=0;col<4;col++)hashes.add(store.readBlock(0,col,0).hash());
        Files.writeString(Path.of("build/delta-expected.json"),Json.encode(hashes));
        try(PribServer server=new PribServer(data,Path.of("web"),0);HttpClient http=HttpClient.newHttpClient()) {
            Thread thread=new Thread(()->{try{server.run();}catch(IOException e){throw new UncheckedIOException(e);}});thread.start();
            try {
                try(var peer=new PribServerTest.Peer(http,server.port())) {
                    peer.hello();
                    check(request(peer,store,1,0).equals("FULL"),"Primera base FULL");
                    check(request(peer,store,2,1).equals("REF"),"Contenido idéntico REF");
                    peer.view(store,3,0,256,0,128,128);Packet delta=packet(peer,3,true);
                    check(mode(delta).equals("DELTA") && delta.bytes().length<1024,"DELTA con ahorro total");
                    verify(peer,store,delta,true);Map<String,Object> summary=done(peer,3);
                    check(Json.integer(summary,"deltaCandidates")<=4 && Json.integer(summary,"delta")==1,"Candidatos limitados y métrica DELTA");
                    System.out.println("DELTA fixture: "+delta.payload().length+" bytes de payload, "+delta.bytes().length+" bytes PRIB para 49152 RGB");
                    check(request(peer,store,4,0).equals("REUSE"),"Identidad igual REUSE");
                    check(request(peer,store,5,3).equals("FULL"),"Sin ahorro suficiente FULL");
                }
                for(String reason:List.of("BASE_MISSING","HASH_MISMATCH","DELTA_FAILED","CACHE_MISS")) {
                    try(var peer=new PribServerTest.Peer(http,server.port())) {
                        peer.hello();request(peer,store,1,0);peer.view(store,2,0,256,0,128,128);
                        Packet failed=packet(peer,2,true);check(mode(failed).equals("DELTA"),"Intento diferencial inicial");
                        peer.send("RECOVER",Map.of("viewId",2,"transferId",Json.integer(failed.header(),"transferId"),"reason",reason));
                        Packet fallback=packet(peer,2,true);check(mode(fallback).equals("FULL"),"Recuperación selectiva FULL");
                        verify(peer,store,fallback,true);Map<String,Object> summary=done(peer,2);
                        check(Json.integer(summary,"blocks")==1 && Json.integer(summary,"transmissions")==2 && Json.integer(summary,"recoveries")==1,"Un objetivo, dos intentos");
                        check(Json.integer(fallback.header(),"x")==256,"Recuperar únicamente el objetivo afectado");
                    }
                }
                pausedRecovery(http,server.port(),store);
                try(var peer=new PribServerTest.Peer(http,server.port())) {
                    peer.hello();peer.view(store,1,0,0,0,128,128);
                    for(int attempt=0;attempt<3;attempt++) {
                        Packet failed=packet(peer,1,true);check(mode(failed).equals("FULL"),"Reintento no depende de otra base");
                        peer.send("RECOVER",Map.of("viewId",1,"transferId",Json.integer(failed.header(),"transferId"),"reason","HASH_MISMATCH"));
                    }
                    check(control(peer,"ERROR",0).toString().contains("Límite"),"Reintentos limitados");
                }
                try(var peer=new PribServerTest.Peer(http,server.port())) {
                    peer.hello();request(peer,store,1,0);peer.view(store,2,0,256,0,128,128);Packet old=packet(peer,2,true);
                    peer.send("CANCEL",Map.of("viewId",2));
                    peer.view(store,3,0,384,0,128,128);
                    peer.send("RECOVER",Map.of("viewId",2,"transferId",Json.integer(old.header(),"transferId"),"reason","BASE_MISSING"));
                    Packet current=packet(peer,3,true);verify(peer,store,current,true);done(peer,3);
                    check(Json.integer(current.header(),"x")==384,"RECOVER obsoleto no resucita la vista anterior");
                }
                try(var peer=new PribServerTest.Peer(http,server.port())) {
                    peer.hello();peer.send("RECOVER",Map.of("viewId",1,"transferId",999,"reason","HASH_MISMATCH"));
                    control(peer,"ERROR",0);
                }
                System.out.println("PASS etapa 6: cuatro modos, recuperación selectiva de cuatro causas, créditos, generaciones y límite de reintentos");
                System.out.println("Fixture navegador: "+data.toAbsolutePath());
            } finally {server.close();thread.join(5000);check(!thread.isAlive(),"Cierre del servidor");}
        }
    }
    private static void pausedRecovery(HttpClient http,int port,ImageStore store)throws Exception {
        try(var peer=new PribServerTest.Peer(http,port)) {
            peer.hello(false);peer.send("CREDIT_INIT",Map.of("capacityBytes",CreditWindow.MAX_PACKET));
            peer.view(store,1,0,0,0,128,128);Packet base=packet(peer,1,false);verify(peer,store,base,true);done(peer,1);
            peer.view(store,2,0,256,0,128,128);Packet failed=packet(peer,2,false);
            check(mode(failed).equals("DELTA"),"DELTA cabe aunque no cabe FULL");
            peer.send("RECOVER",Map.of("viewId",2,"transferId",Json.integer(failed.header(),"transferId"),"reason","BASE_MISSING"));
            peer.send("CREDIT_STATUS",Map.of());Map<String,Object> state;
            do{state=control(peer,"CREDIT_STATUS",0);}while(!Json.text(state,"state").equals("WAIT_CREDIT"));
            check(Json.integer(state,"outstandingBytes")==base.bytes().length+failed.bytes().length,"RECOVER no devuelve créditos");
            Object extra=peer.received.poll(200,TimeUnit.MILLISECONDS);check(!(extra instanceof byte[]),"FULL pausado sin crédito");
            peer.released=base.bytes().length+failed.bytes().length;peer.grantId=1;
            peer.send("CREDIT_GRANT",Map.of("grantId",1,"releasedBytes",peer.released));
            Packet fallback=packet(peer,2,true);verify(peer,store,fallback,true);done(peer,2);
        }
    }
    private static String request(PribServerTest.Peer peer,ImageStore store,int view,int column)throws Exception {
        peer.view(store,view,0,column*128,0,128,128);Packet packet=packet(peer,view,true);verify(peer,store,packet,true);done(peer,view);return mode(packet);
    }
    private static String mode(Packet packet){return Json.text(packet.header(),"mode");}
    private static Packet packet(PribServerTest.Peer peer,int view,boolean release)throws Exception {
        while(true) {
            Object item=peer.next();
            if(item instanceof String text){check(!text.contains("ERROR"),text);continue;}
            byte[] bytes=(byte[])item;
            if(release){peer.released+=bytes.length;peer.send("CREDIT_GRANT",Map.of("grantId",++peer.grantId,"releasedBytes",peer.released));}
            ByteBuffer data=ByteBuffer.wrap(bytes);byte[] json=new byte[data.getInt()];data.get(json);
            Map<String,Object> header=Json.parse(WebSocketFrames.utf8(json));
            if(Json.integer(header,"viewId")!=view)continue;
            byte[] payload=new byte[data.remaining()];data.get(payload);return new Packet(bytes,header,payload);
        }
    }
    private static void verify(PribServerTest.Peer peer,ImageStore store,Packet packet,boolean advertise)throws Exception {
        Map<String,Object> h=packet.header();byte[] rgb=packet.payload();String mode=mode(packet);
        if(!mode.equals("FULL")) {
            byte[] base=peer.cached.get(Json.text(h,"baseImageId")+"/"+Json.text(h,"baseId"));check(base!=null,"Base materializada");
            rgb=mode.equals("DELTA")?DeltaCodec.decode(base,rgb):base;
        }
        ImageStore.Block target=store.readBlock(Json.integer(h,"level"),Json.integer(h,"x")/128,Json.integer(h,"y")/128);
        check(Arrays.equals(rgb,target.rgb()) && target.hash().equals(Json.text(h,"expectedHash")),"Reconstrucción exacta independiente");
        String block=Json.text(h,"blockId");
        if(advertise) {
            peer.cached.put(store.imageId+"/"+block,rgb);
            peer.send("CACHE_STATE",Map.of("cacheSeq",++peer.cacheSeq,"operation","PUT","imageId",store.imageId,"blockId",block,
                    "width",target.width(),"height",target.height(),"hash",target.hash(),"similarity",SimilarityIndex.signature(rgb,target.width(),target.height())));
        }
        peer.send("ACK",Map.of("viewId",Json.integer(h,"viewId"),"transferId",Json.integer(h,"transferId"),"hash",target.hash()));
    }
    private static Map<String,Object> done(PribServerTest.Peer peer,int view)throws Exception{return control(peer,"VIEW_DONE",view);}
    private static Map<String,Object> control(PribServerTest.Peer peer,String type,int view)throws Exception {
        while(true) {
            Object item=peer.next();check(item instanceof String,"Datos inesperados al esperar control");
            Map<String,Object> control=Json.parse((String)item);String got=Json.text(control,"type");
            if(got.equals("ERROR")&&!type.equals("ERROR"))throw new AssertionError(item.toString());
            if(got.equals(type)&&(view==0||Json.integer(control,"viewId")==view))return control;
        }
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
