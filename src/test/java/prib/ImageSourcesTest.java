package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;
import javax.imageio.ImageIO;

/** PNG subido por fragmentos y URL HTTP local de prueba, sin internet ni datos del curso. */
public final class ImageSourcesTest {
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
    static void finished(ImagePreparation p, String expected) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(p.status().get("state").equals("RUNNING")&&System.nanoTime()<deadline)Thread.sleep(20);
        check(p.status().get("state").equals(expected),p.status().toString());Thread.sleep(50);
    }
    static void invalid(Runnable r) {try{r.run();throw new AssertionError("Aceptó comando inválido");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of("build"),"sources-test-").toAbsolutePath();
        Path archives=Files.createDirectory(root.resolve("archives")),data=Files.createDirectory(root.resolve("data"));
        BufferedImage image=new BufferedImage(259,131,BufferedImage.TYPE_INT_RGB);Random random=new Random(5517);
        for(int y=0;y<131;y++)for(int x=0;x<259;x++)image.setRGB(x,y,random.nextInt(1<<24));
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();ImageIO.write(image,"png",encoded);byte[] png=encoded.toByteArray();
        byte[] rgb=new byte[128*128*3];
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){int c=image.getRGB(x,y),i=(y*128+x)*3;rgb[i]=(byte)(c>>16);rgb[i+1]=(byte)(c>>8);rgb[i+2]=(byte)c;}
        String hash=HexFormat.of().formatHex(ImageStore.sha256(rgb));
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/valid.png",e->{e.getResponseHeaders().add("Content-Type","image/png");e.sendResponseHeaders(200,png.length);try(var out=e.getResponseBody()){out.write(png);}});
        http.createContext("/redirect",e->{e.getResponseHeaders().add("Location","/valid.png");e.sendResponseHeaders(302,-1);e.close();});
        http.createContext("/bad",e->{byte[] html="<html>no PNG</html>".getBytes();e.sendResponseHeaders(200,html.length);try(var out=e.getResponseBody()){out.write(html);}});
        http.createContext("/loop",e->{e.getResponseHeaders().add("Location","/loop");e.sendResponseHeaders(302,-1);e.close();});
        http.start();String url="http://127.0.0.1:"+http.getAddress().getPort();
        try (ImagePreparation p=new ImagePreparation(archives,data)) {
            try {PngSource.open(url+"/valid.png");throw new AssertionError("Permitió red privada por defecto");}catch(IOException expected){}
            System.setProperty("prib.allowLocalImageUrls","true");
            invalid(()->p.startUrl("file:///C:/imagen.png","bad",v->{}));
            invalid(()->p.startUrl("https://user:password@example.com/image.png","bad",v->{}));
            p.startUrl(url+"/redirect","url",v->{});finished(p,"DONE");
            check(new ImageStore(data.resolve("url")).readBlock(0,0,0).hash().equals(hash),"RGB exacto desde URL y redirección");
            byte[] original=Files.readAllBytes(data.resolve("url/image.properties"));
            p.startUrl(url+"/valid.png","url",v->{throw new AssertionError();});finished(p,"FAILED");
            check(Arrays.equals(original,Files.readAllBytes(data.resolve("url/image.properties"))),"URL no sobrescribe");
            p.startUrl(url+"/bad","html",v->{throw new AssertionError();});finished(p,"FAILED");
            p.startUrl(url+"/loop","loop",v->{throw new AssertionError();});finished(p,"FAILED");
            CountDownLatch ready=new CountDownLatch(1);
            p.startUpload("owner",png.length,"local",v->{},n->ready.countDown());check(ready.await(10,TimeUnit.SECONDS),"inicio subida");
            invalid(()->p.chunk("other",0,"AA==",n->{}));
            invalid(()->p.chunk("owner",1,"AA==",n->{}));
            invalid(()->p.endUpload("owner"));
            invalid(()->p.startUrl(url+"/valid.png","concurrent",v->{}));
            for(int offset=0;offset<png.length;){
                int length=Math.min(4096,png.length-offset);CountDownLatch ack=new CountDownLatch(1);long next=offset+length;
                p.chunk("owner",offset,Base64.getEncoder().encodeToString(Arrays.copyOfRange(png,offset,offset+length)),n->{check(n==next,"offset confirmado");ack.countDown();});
                check(ack.await(10,TimeUnit.SECONDS),"confirmación fragmento");offset+=length;
            }
            p.endUpload("owner");finished(p,"DONE");
            check(new ImageStore(data.resolve("local")).readBlock(0,0,0).hash().equals(hash),"RGB exacto desde PNG subido");
            CountDownLatch abortReady=new CountDownLatch(1);
            p.startUpload("abort",png.length,"abortada",v->{throw new AssertionError();},n->abortReady.countDown());check(abortReady.await(10,TimeUnit.SECONDS),"inicio cancelación");
            CountDownLatch first=new CountDownLatch(1);p.chunk("abort",0,Base64.getEncoder().encodeToString(Arrays.copyOf(png,4096)),n->first.countDown());
            check(first.await(10,TimeUnit.SECONDS),"primer fragmento");p.abortUpload("other");p.abortUpload("abort");finished(p,"FAILED");
            check(!Files.exists(data.resolve("abortada/image.properties"))||Files.exists(data.resolve("abortada/INCOMPLETE")),"subida abortada nunca se publica");
            p.startUrl(url+"/valid.png","recuperada",v->{});finished(p,"DONE");
        } finally {System.clearProperty("prib.allowLocalImageUrls");http.stop(0);}
        Files.write(root.resolve("image.png"),png);
        Files.writeString(Path.of("build/sources-fixture.json"),Json.encode(Map.of("png",root.resolve("image.png").toString(),"hash",hash)));
        System.out.println("PASS fuentes PNG/URL: RGB exacto, fragmentos, sesión propietaria, abortar, HTTP, redirecciones, errores, no sobrescritura y reintento");
    }
}
