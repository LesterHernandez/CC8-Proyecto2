package servidor.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncServer {

    private final int port;

    public AsyncServer(int port) {
        this.port = port;
    }

    public void start() {

        System.out.println();
        System.out.println("==========================================");
        System.out.println("       SERVIDOR ASINCRONO DE IMAGENES");
        System.out.println("==========================================");
        System.out.println();

        /*
         * Java 21 permite utilizar Virtual Threads.
         *
         * Cada cliente puede ser atendido por su propio
         * Virtual Thread sin tener que crear manualmente
         * un hilo del sistema operativo.
         */
        try (
            ServerSocket serverSocket = new ServerSocket(port);
            ExecutorService executor =
                    Executors.newVirtualThreadPerTaskExecutor()
        ) {

            System.out.println(
                    "[AsyncServer] Servidor iniciado."
            );

            System.out.println(
                    "[AsyncServer] Puerto: " + port
            );

            System.out.println(
                    "[AsyncServer] URL: http://localhost:" + port
            );

            System.out.println(
                    "[AsyncServer] Esperando clientes..."
            );

            System.out.println();

            while (!serverSocket.isClosed()) {

                Socket clientSocket = serverSocket.accept();

                System.out.println(
                        "[AsyncServer] Cliente conectado: "
                                + clientSocket.getRemoteSocketAddress()
                );

                /*
                 * Cada conexion se entrega a un Virtual Thread.
                 */
                executor.submit(
                        new ClientHandler(clientSocket)
                );
            }

        } catch (IOException e) {

            System.err.println(
                    "[AsyncServer] Error del servidor: "
                            + e.getMessage()
            );
        }
    }
}