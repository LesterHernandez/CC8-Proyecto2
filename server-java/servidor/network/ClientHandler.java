package servidor.network;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {

        long threadId = Thread.currentThread().threadId();

        System.out.println(
                "[ClientHandler-" + threadId + "] "
                        + "Atendiendo cliente: "
                        + clientSocket.getRemoteSocketAddress()
        );

        try (
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input)
                )
        ) {

            HttpParser request = HttpParser.parse(reader);

            if (request == null) {
                sendErrorResponse(
                        output,
                        "400 Bad Request",
                        "Solicitud HTTP invalida."
                );
                return;
            }

            if (!request.getMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(
                        output,
                        "405 Method Not Allowed",
                        "Solo se permite el metodo GET."
                );
                return;
            }

            String path = request.getPath();

            System.out.println(
                    "[ClientHandler-" + threadId + "] "
                            + "GET " + path
            );

            File file = resolveFile(path);

            if (file == null) {
                sendErrorResponse(
                        output,
                        "403 Forbidden",
                        "Acceso al recurso no permitido."
                );
                return;
            }

            if (!file.isFile()) {
                send404Response(output);
                return;
            }

            sendFileResponse(
                    output,
                    file
            );

            System.out.println(
                    "[ClientHandler-" + threadId + "] "
                            + "Recurso enviado: "
                            + file.getPath()
            );

        } catch (IOException e) {

            System.err.println(
                    "[ClientHandler-" + threadId + "] "
                            + "Error: "
                            + e.getMessage()
            );

        } finally {

            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }

            System.out.println(
                    "[ClientHandler-" + threadId + "] "
                            + "Conexion cerrada."
            );
        }
    }

    /**
     * Determina que archivo corresponde a la ruta solicitada.
     *
     * "/"                  -> web-frontend/index.html
     * "/css/styles.css"    -> web-frontend/css/styles.css
     * "/js/app.js"         -> web-frontend/js/app.js
     * "/tiles/..."         -> storage/tiles/...
     */
    private File resolveFile(String requestPath) {

        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }

        /*
         * Eliminamos los parametros de consulta.
         *
         * Ejemplo:
         *
         * /index.html?x=10
         *
         * se convierte en:
         *
         * /index.html
         */
        String path = requestPath;

        int queryIndex = path.indexOf('?');

        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        /*
         * La ruta raiz carga el index.html.
         */
        if (path.equals("/")) {
            path = "/index.html";
        }

        /*
         * Evitar rutas que intenten salir
         * de los directorios permitidos.
         */
        if (path.contains("..")) {
            return null;
        }

        /*
         * Las teselas se almacenan dentro de:
         *
         * server-java/storage/tiles/
         */
        if (path.startsWith("/tiles/")) {

            String relativePath = path.substring(
                    "/tiles/".length()
            );

            return new File(
                    "storage/tiles",
                    relativePath
            );
        }

        /*
         * El resto de recursos pertenecen
         * al frontend.
         */
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return new File(
                "../web-frontend",
                path
        );
    }

    /**
     * Envia un archivo mediante HTTP.
     *
     * Se utiliza un buffer para no cargar
     * todo el archivo en memoria.
     */
    private void sendFileResponse(
            OutputStream output,
            File file) throws IOException {

        String contentType = getContentType(
                file.getName()
        );

        long fileSize = file.length();

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(output),
                false
        );

        writer.println(
                "HTTP/1.1 200 OK"
        );

        writer.println(
                "Content-Type: " + contentType
        );

        writer.println(
                "Content-Length: " + fileSize
        );

        writer.println(
                "Cache-Control: public, max-age=3600"
        );

        writer.println(
                "Connection: close"
        );

        writer.println();

        writer.flush();

        /*
         * Copiamos el archivo por bloques.
         *
         * Esto evita utilizar:
         *
         * Files.readAllBytes()
         *
         * para archivos grandes.
         */
        try (InputStream fileInput =
                     new BufferedInputStream(
                             new FileInputStream(file)
                     )) {

            byte[] buffer = new byte[8192];

            int bytesRead;

            while ((bytesRead =
                    fileInput.read(buffer)) != -1) {

                output.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            output.flush();
        }
    }

    /**
     * Determina el Content-Type del recurso.
     */
    private String getContentType(String fileName) {

        String lowerName =
                fileName.toLowerCase();

        if (lowerName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }

        if (lowerName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }

        if (lowerName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }

        if (lowerName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }

        if (lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")) {

            return "image/jpeg";
        }

        if (lowerName.endsWith(".png")) {
            return "image/png";
        }

        if (lowerName.endsWith(".gif")) {
            return "image/gif";
        }

        if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (lowerName.endsWith(".ico")) {
            return "image/x-icon";
        }

        return "application/octet-stream";
    }

    /**
     * Respuesta para recursos inexistentes.
     */
    private void send404Response(
            OutputStream output) throws IOException {

        String body =
                "<!DOCTYPE html>"
                        + "<html>"
                        + "<head>"
                        + "<meta charset=\"UTF-8\">"
                        + "<title>404 Not Found</title>"
                        + "</head>"
                        + "<body>"
                        + "<h1>404 Not Found</h1>"
                        + "<p>El recurso solicitado no existe.</p>"
                        + "</body>"
                        + "</html>";

        sendHttpResponse(
                output,
                "404 Not Found",
                "text/html; charset=UTF-8",
                body
        );
    }

    /**
     * Respuesta para solicitudes invalidas.
     */
    private void sendErrorResponse(
            OutputStream output,
            String status,
            String message) throws IOException {

        String body =
                "<!DOCTYPE html>"
                        + "<html>"
                        + "<head>"
                        + "<meta charset=\"UTF-8\">"
                        + "<title>" + status + "</title>"
                        + "</head>"
                        + "<body>"
                        + "<h1>" + status + "</h1>"
                        + "<p>" + message + "</p>"
                        + "</body>"
                        + "</html>";

        sendHttpResponse(
                output,
                status,
                "text/html; charset=UTF-8",
                body
        );
    }

    /**
     * Envia una respuesta HTTP con contenido de texto.
     */
    private void sendHttpResponse(
            OutputStream output,
            String status,
            String contentType,
            String body) throws IOException {

        byte[] bodyBytes =
                body.getBytes("UTF-8");

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(output),
                false
        );

        writer.println(
                "HTTP/1.1 " + status
        );

        writer.println(
                "Content-Type: " + contentType
        );

        writer.println(
                "Content-Length: " + bodyBytes.length
        );

        writer.println(
                "Connection: close"
        );

        writer.println();

        writer.flush();

        output.write(bodyBytes);
        output.flush();
    }
}