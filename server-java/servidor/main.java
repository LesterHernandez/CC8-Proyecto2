package servidor;

import servidor.network.AsyncServer;

public class Main {
    public static void main(String[] args) {
        int port = 8080;
        AsyncServer server = new AsyncServer(port);
        server.start();
    }
}