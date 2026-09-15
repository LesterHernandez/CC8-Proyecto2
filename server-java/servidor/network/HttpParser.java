package servidor.network;

import java.io.BufferedReader;
import java.io.IOException;

public class HttpParser {

    private String method;
    private String path;
    private String version;

    private HttpParser(
            String method,
            String path,
            String version) {

        this.method = method;
        this.path = path;
        this.version = version;
    }

    /**
     * Lee y analiza una peticion HTTP.
     *
     * Ejemplo:
     *
     * GET /index.html HTTP/1.1
     *
     * Se obtiene:
     *
     * method  = GET
     * path    = /index.html
     * version = HTTP/1.1
     */
    public static HttpParser parse(
            BufferedReader reader) throws IOException {

        String requestLine = reader.readLine();

        if (requestLine == null
                || requestLine.isBlank()) {

            return null;
        }

        String[] parts = requestLine.trim().split("\\s+");

        /*
         * Una linea HTTP valida debe tener:
         *
         * METODO RUTA VERSION
         */
        if (parts.length != 3) {
            return null;
        }

        String method = parts[0];
        String path = parts[1];
        String version = parts[2];

        /*
         * Actualmente nuestro servidor trabaja
         * con HTTP/1.0 y HTTP/1.1.
         */
        if (!version.equalsIgnoreCase("HTTP/1.0")
                && !version.equalsIgnoreCase("HTTP/1.1")) {

            return null;
        }

        /*
         * La ruta debe comenzar con "/".
         */
        if (!path.startsWith("/")) {
            return null;
        }

        /*
         * Consumimos los headers HTTP.
         *
         * Esto es importante porque el navegador
         * envia varias lineas despues de la primera.
         */
        String headerLine;

        while ((headerLine = reader.readLine()) != null) {

            if (headerLine.isEmpty()) {
                break;
            }
        }

        return new HttpParser(
                method,
                path,
                version
        );
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }
}