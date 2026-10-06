package prib;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Usa un ZIP propio y destinos temporales; no toca las imágenes del equipo. */
public final class ImagePreparationTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build"), "preparation-test-").toAbsolutePath();
        Path archives = Files.createDirectory(root.resolve("imagenes")), data = Files.createDirectory(root.resolve("data"));
        BufferedImage image = new BufferedImage(259, 131, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<131; y++) for (int x=0; x<259; x++) image.setRGB(x,y,(x*199+y*1009)&0xffffff);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image,"png",png);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archives.resolve("prueba.zip")))) {
            zip.putNextEntry(new ZipEntry("carpeta/imagen.png")); zip.write(png.toByteArray()); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("incorrecta.png")); zip.write(new byte[]{1,2,3}); zip.closeEntry();
        }
        try (ImagePreparation preparation = new ImagePreparation(archives,data)) {
            check(preparation.archives().equals(List.of("prueba.zip")), "listar ZIP");
            check(preparation.entries("prueba.zip").size()==2, "listar PNG internos");
            try { preparation.entries("../prueba.zip"); throw new AssertionError("Aceptó ruta exterior"); } catch(IOException expected) { }
            try { preparation.start("prueba.zip","carpeta/imagen.png","../escape", p -> {}); throw new AssertionError("Aceptó destino exterior"); } catch(IllegalArgumentException expected) { }
            CountDownLatch published = new CountDownLatch(1);
            preparation.start("prueba.zip","carpeta/imagen.png","correcta",p -> published.countDown());
            try { preparation.start("prueba.zip","carpeta/imagen.png","otra", p -> {}); throw new AssertionError("Aceptó dos tareas"); } catch(IllegalArgumentException expected) { }
            waitFinished(preparation); check(published.await(1,TimeUnit.SECONDS),"publicación");
            check(preparation.status().get("state").equals("DONE"),"preparación correcta");
            ImageStore store = new ImageStore(data.resolve("correcta")); store.readBlock(0,2,1);
            byte[] manifest = Files.readAllBytes(data.resolve("correcta/image.properties"));
            preparation.start("prueba.zip","carpeta/imagen.png","correcta",p -> {throw new AssertionError();});
            waitFinished(preparation); check(preparation.status().get("state").equals("FAILED"),"rechazar destino existente");
            check(Arrays.equals(manifest,Files.readAllBytes(data.resolve("correcta/image.properties"))),"no sobrescribir");
            preparation.start("prueba.zip","incorrecta.png","fallida",p -> {throw new AssertionError();});
            waitFinished(preparation); check(preparation.status().get("state").equals("FAILED"),"rechazar PNG inválido");
            preparation.start("prueba.zip","carpeta/imagen.png","recuperada",p -> {});
            waitFinished(preparation); check(preparation.status().get("state").equals("DONE"),"reintentar tras error");
        }
        Files.writeString(Path.of("build/preparation-fixture.json"),Json.encode(Map.of("archives",archives.toString())));
        System.out.println("PASS preparación web: ZIP, rutas, tarea única, RGB, no sobrescritura y recuperación tras fallo");
    }
    private static void waitFinished(ImagePreparation preparation) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        while(preparation.status().get("state").equals("RUNNING") && System.nanoTime()<deadline) Thread.sleep(25);
        check(!preparation.status().get("state").equals("RUNNING"),"timeout de preparación");
        // DONE/FAILED puede preceder por un instante al finally que libera el trabajador.
        Thread.sleep(50);
    }
    private static void check(boolean value,String label) { if(!value)throw new AssertionError(label); }
}
