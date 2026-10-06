package prib;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

public final class ImageFormatsTest {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory(Path.of("build"),"formats-test-").toAbsolutePath();
        BufferedImage image=new BufferedImage(259,131,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<131;y++)for(int x=0;x<259;x++)image.setRGB(x,y,(x*37+y*71)&0xffffff);
        Path archives=Files.createDirectory(root.resolve("archives")),data=Files.createDirectory(root.resolve("data"));
        String jpegHash=null;Path jpeg=null;
        for(String format:List.of("jpeg","gif","bmp")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            ImageSourcesTest.check(ImageIO.write(image,format,out),"codificador "+format);byte[] encoded=out.toByteArray();
            BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(encoded));byte[] rgb=new byte[128*128*3];
            for(int y=0;y<128;y++)for(int x=0;x<128;x++){int c=decoded.getRGB(x,y),i=(y*128+x)*3;rgb[i]=(byte)(c>>16);rgb[i+1]=(byte)(c>>8);rgb[i+2]=(byte)c;}
            String hash=HexFormat.of().formatHex(ImageStore.sha256(rgb));
            // El nombre .png no altera la detección del contenido.
            PrepareImage.prepare(new ByteArrayInputStream(encoded),"extension-equivocada.png",root.resolve(format));
            ImageSourcesTest.check(new ImageStore(root.resolve(format)).readBlock(0,0,0).hash().equals(hash),"RGB decodificado exacto "+format);
            if(format.equals("jpeg")){jpegHash=hash;jpeg=root.resolve("image.jpg");Files.write(jpeg,encoded);}
            Path zip=archives.resolve(format+".zip");
            try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(zip))){z.putNextEntry(new ZipEntry("imagen."+format));z.write(encoded);z.closeEntry();}
            try(ImagePreparation p=new ImagePreparation(archives,data)){
                ImageSourcesTest.check(p.entries(format+".zip").contains("imagen."+format),"selector ZIP "+format);
                p.start(format+".zip","imagen."+format,format,v->{});ImageSourcesTest.finished(p,"DONE");
            }
            if(format.equals("bmp")){
                byte[] oversized=encoded.clone();ByteBuffer.wrap(oversized).order(ByteOrder.LITTLE_ENDIAN).putInt(18,100000);
                try{new ImageRows(new ByteArrayInputStream(oversized));throw new AssertionError("Aceptó dimensiones excesivas");}catch(IOException expected){}
            }
        }
        try(ImagePreparation p=new ImagePreparation(archives,data)){
            byte[] bytes=Files.readAllBytes(jpeg);CountDownLatch ready=new CountDownLatch(1);
            p.startUpload("jpeg",bytes.length,"subida-jpeg",v->{},n->ready.countDown());ImageSourcesTest.check(ready.await(10,TimeUnit.SECONDS),"inicio JPEG");
            for(int offset=0;offset<bytes.length;){int end=Math.min(offset+4096,bytes.length);CountDownLatch ack=new CountDownLatch(1);p.chunk("jpeg",offset,Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,end)),n->ack.countDown());ImageSourcesTest.check(ack.await(10,TimeUnit.SECONDS),"ACK JPEG");offset=end;}
            p.endUpload("jpeg");ImageSourcesTest.finished(p,"DONE");
            ImageSourcesTest.check(new ImageStore(data.resolve("subida-jpeg")).readBlock(0,0,0).hash().equals(jpegHash),"subida JPEG exacta");
        }
        BufferedImage transparent=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);ByteArrayOutputStream gif=new ByteArrayOutputStream();ImageIO.write(transparent,"gif",gif);
        try(ImageRows rows=new ImageRows(new ByteArrayInputStream(gif.toByteArray()))){for(byte b:rows.next())ImageSourcesTest.check((b&255)==255,"transparencia blanca");}
        try{new ImageRows(new ByteArrayInputStream("<html>no es imagen</html>".getBytes()));throw new AssertionError("Aceptó HTML");}catch(IOException expected){}
        Files.writeString(Path.of("build/formats-fixture.json"),Json.encode(Map.of("png",jpeg.toString(),"hash",jpegHash)));
        System.out.println("PASS firmas JPEG/GIF/BMP: píxeles decodificados exactos, nombre falso, ZIP, subida JPEG, transparencia, dimensiones y HTML");
    }
}
