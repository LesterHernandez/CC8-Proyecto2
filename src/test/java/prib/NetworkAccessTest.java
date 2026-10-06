package prib;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Verifica las cabeceras y el acceso IPv4 sin depender de una IP fija o de Radmin. */
public final class NetworkAccessTest {
    public static void main(String[] args) throws Exception {
        String address = null;
        for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!adapter.isUp()) continue;
            for (InetAddress candidate : Collections.list(adapter.getInetAddresses()))
                if (candidate instanceof Inet4Address && !candidate.isLoopbackAddress())
                    address = candidate.getHostAddress();
        }
        for (boolean network : new boolean[]{false, true}) {
            Path root = Files.createTempDirectory(Path.of("build"), "network-test-");
            PribServer server = new PribServer(root.resolve("data"), Path.of("web"), 0, root.resolve("zip"), network);
            Thread thread = new Thread(() -> {
                try { server.run(); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            thread.start();
            try {
                String local = "localhost:" + server.port();
                String response = request("127.0.0.1", server.port(), local, null, false);
                require(response.startsWith("HTTP/1.1 200"), "localhost disponible");
                require(response.contains("ws://" + local), "CSP para el host validado");
                require(request("127.0.0.1", server.port(), "example.invalid:" + server.port(), null, false)
                        .startsWith("HTTP/1.1 400"), "Host ajeno rechazado");
                require(request("127.0.0.1", server.port(), "localhost:" + (server.port() + 1), null, false)
                        .startsWith("HTTP/1.1 400"), "puerto ajeno rechazado");
                require(request("127.0.0.1", server.port(), local, "http://example.invalid", true)
                        .startsWith("HTTP/1.1 400"), "Origin ajeno rechazado");
                require(request("127.0.0.1", server.port(), local, "http://" + local, true)
                        .startsWith("HTTP/1.1 101"), "WebSocket local");
                if (address != null) {
                    String host = address + ":" + server.port();
                    response = request(network ? address : "127.0.0.1", server.port(), host, null, false);
                    require(response.startsWith("HTTP/1.1 " + (network ? "200" : "400")), "Host IPv4 según modo");
                    if (network) {
                        require(response.contains("ws://" + host), "CSP IPv4 dinámica");
                        require(request(address, server.port(), host, "http://" + host, true)
                                .startsWith("HTTP/1.1 101"), "WebSocket por interfaz IPv4");
                    }
                }
            } finally { server.close(); thread.join(5000); }
            require(!thread.isAlive(), "servidor cerrado");
        }
        System.out.println("PASS acceso local/red, Host, Origin y CSP" + (address == null ? " (sin interfaz externa disponible)" : " con IPv4 propia"));
    }
    private static String request(String address, int port, String host, String origin, boolean websocket) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 3000); socket.setSoTimeout(3000);
            String headers = "GET " + (websocket ? "/ws" : "/") + " HTTP/1.1\r\nHost: " + host + "\r\n";
            if (origin != null) headers += "Origin: " + origin + "\r\n";
            if (websocket) headers += "Connection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n";
            socket.getOutputStream().write((headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) result.append(line).append('\n');
            return result.toString();
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
