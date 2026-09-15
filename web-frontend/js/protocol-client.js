javascript
class ProtocolClient {

    constructor() {

        this.socket = null;

        this.connected = false;

        this.sessionId = null;

        /*
         * El servidor utilizara WebSocket para transportar
         * los mensajes del protocolo de control.
         */
        this.serverUrl =
            `ws://${window.location.host}/control`;

        /*
         * Funcion que puede ser asignada por app.js
         * para recibir mensajes del servidor.
         */
        this.onMessage = null;

        /*
         * Funcion que puede ser asignada para detectar
         * la conexion con el servidor.
         */
        this.onConnect = null;

        /*
         * Funcion que puede ser asignada para detectar
         * el cierre de la conexion.
         */
        this.onDisconnect = null;

        /*
         * Funcion para manejar errores.
         */
        this.onError = null;
    }

    /**
     * Abre la conexion con el servidor.
     */
    connect() {

        return new Promise((resolve, reject) => {

            if (this.connected) {

                resolve();

                return;
            }

            try {

                console.log(
                    '[ProtocolClient] Conectando a: '
                    + this.serverUrl
                );

                this.socket =
                    new WebSocket(
                        this.serverUrl
                    );

                this.socket.onopen = () => {

                    this.connected = true;

                    console.log(
                        '[ProtocolClient] Conexion establecida.'
                    );

                    if (this.onConnect) {
                        this.onConnect();
                    }

                    resolve();
                };

                this.socket.onmessage =
                    (event) => {

                        this.handleMessage(
                            event.data
                        );
                    };

                this.socket.onerror =
                    (error) => {

                        console.error(
                            '[ProtocolClient] Error de comunicacion:',
                            error
                        );

                        if (this.onError) {
                            this.onError(error);
                        }

                        if (!this.connected) {
                            reject(error);
                        }
                    };

                this.socket.onclose = () => {

                    this.connected = false;

                    this.sessionId = null;

                    console.log(
                        '[ProtocolClient] Conexion cerrada.'
                    );

                    if (this.onDisconnect) {
                        this.onDisconnect();
                    }
                };

            } catch (error) {

                console.error(
                    '[ProtocolClient] No se pudo crear WebSocket:',
                    error
                );

                reject(error);
            }
        });
    }

    /**
     * Procesa los mensajes recibidos desde Java.
     */
    handleMessage(data) {

        try {

            const message =
                JSON.parse(data);

            console.log(
                '[ProtocolClient] Mensaje recibido:',
                message
            );

            switch (message.type) {

                case 'SESSION':

                    this.sessionId =
                        message.sessionId;

                    console.log(
                        '[ProtocolClient] Sesion asignada: '
                        + this.sessionId
                    );

                    break;

                case 'VIEWPORT_ACK':

                    console.log(
                        '[ProtocolClient] '
                        + 'Viewport aceptado por el servidor.'
                    );

                    break;

                case 'ERROR':

                    console.error(
                        '[ProtocolClient] Error del servidor: '
                        + message.message
                    );

                    break;

                default:

                    console.warn(
                        '[ProtocolClient] Tipo de mensaje desconocido: '
                        + message.type
                    );

                    break;
            }

            /*
             * Permite que app.js reciba tambien
             * el mensaje completo.
             */
            if (this.onMessage) {

                this.onMessage(
                    message
                );
            }

        } catch (error) {

            console.error(
                '[ProtocolClient] Mensaje JSON invalido:',
                data
            );
        }
    }

    /**
     * Envia un paquete del protocolo al servidor.
     */
    send(message) {

        if (!this.connected
                || !this.socket) {

            console.warn(
                '[ProtocolClient] '
                + 'No existe una conexion activa.'
            );

            return false;
        }

        try {

            const json =
                JSON.stringify(message);

            console.log(
                '[ProtocolClient] Enviando:',
                message
            );

            this.socket.send(
                json
            );

            return true;

        } catch (error) {

            console.error(
                '[ProtocolClient] '
                + 'No se pudo enviar el mensaje:',
                error
            );

            return false;
        }
    }

    /**
     * Informa al servidor que cambio el viewport.
     *
     * El servidor utilizara estos datos para determinar
     * que teselas necesita el cliente.
     */
    updateViewport(
            zoom,
            panX,
            panY,
            tiles) {

        return this.send({

            type: 'VIEWPORT_UPDATE',

            sessionId:
                this.sessionId,

            zoom: zoom,

            panX: panX,

            panY: panY,

            tiles: tiles || []
        });
    }

    /**
     * Solicita una tesela especifica.
     */
    requestTile(
            imageId,
            zoom,
            x,
            y) {

        return this.send({

            type: 'TILE_REQUEST',

            sessionId:
                this.sessionId,

            imageId:
                imageId,

            z: zoom,

            x: x,

            y: y
        });
    }

    /**
     * Informa al servidor que una tesela
     * ya no es necesaria.
     */
    releaseTile(
            imageId,
            zoom,
            x,
            y) {

        return this.send({

            type: 'TILE_RELEASE',

            sessionId:
                this.sessionId,

            imageId:
                imageId,

            z: zoom,

            x: x,

            y: y
        });
    }

    /**
     * Solicita varias teselas.
     *
     * Se utiliza cuando el viewport cambia
     * y se necesitan varias teselas nuevas.
     */
    requestTiles(
            imageId,
            tiles) {

        if (!tiles || tiles.length === 0) {
            return true;
        }

        let success = true;

        for (const tile of tiles) {

            const result =
                this.requestTile(
                    imageId,
                    tile.z,
                    tile.x,
                    tile.y
                );

            if (!result) {
                success = false;
            }
        }

        return success;
    }

    /**
     * Libera varias teselas.
     */
    releaseTiles(
            imageId,
            tiles) {

        if (!tiles || tiles.length === 0) {
            return true;
        }

        let success = true;

        for (const tile of tiles) {

            const result =
                this.releaseTile(
                    imageId,
                    tile.z,
                    tile.x,
                    tile.y
                );

            if (!result) {
                success = false;
            }
        }

        return success;
    }

    /**
     * Indica si existe una conexion activa.
     */
    isConnected() {

        return this.connected
                && this.socket !== null
                && this.socket.readyState
                    === WebSocket.OPEN;
    }

    /**
     * Obtiene el identificador de sesion.
     */
    getSessionId() {

        return this.sessionId;
    }

    /**
     * Cierra la conexion.
     */
    disconnect() {

        if (this.socket) {

            console.log(
                '[ProtocolClient] Cerrando conexion...'
            );

            this.socket.close();
        }

        this.socket = null;

        this.connected = false;

        this.sessionId = null;
    }
}


/*
 * Instancia global utilizada por app.js.
 */
const protocolClient =
    new ProtocolClient();

