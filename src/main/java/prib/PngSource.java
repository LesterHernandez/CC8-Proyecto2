package prib;

import java.io.*;
import java.net.*;

/** URL directa, sin archivo temporal; redirecciones y esperas limitadas. */
final class PngSource {
    static URI validate(String text) throws IOException {
        try {
            URI uri = new URI(text);
            if (text.length() > 2048 || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getFragment() != null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
                throw new IOException("Usa una URL directa HTTP o HTTPS, sin credenciales ni fragmentos");
            return uri;
        } catch (URISyntaxException e) { throw new IOException("URL no válida", e); }
    }
    static InputStream open(String text) throws IOException {
        URI uri = validate(text);
        for (int redirects = 0; redirects <= 5; redirects++) {
            // No convertir el servidor local en acceso a servicios internos de la red.
            if (!Boolean.getBoolean("prib.allowLocalImageUrls")) for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                byte[] b = address.getAddress();
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || b.length == 16 && (b[0] & 0xfe) == 0xfc)
                    throw new IOException("La URL debe apuntar a un servidor público de imágenes");
            }
            HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(10000); connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "image/png, image/jpeg, image/gif, image/bmp");
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirección sin destino");
                    uri = validate(uri.resolve(location).toString()); connection.disconnect(); continue;
                }
                if (code != 200) throw new IOException("La URL respondió HTTP " + code);
                return new FilterInputStream(connection.getInputStream()) {
                    public void close() throws IOException { try { super.close(); } finally { connection.disconnect(); } }
                };
            } catch (IOException | IllegalArgumentException e) { connection.disconnect(); throw e; }
        }
        throw new IOException("Demasiadas redirecciones en la URL");
    }
}
