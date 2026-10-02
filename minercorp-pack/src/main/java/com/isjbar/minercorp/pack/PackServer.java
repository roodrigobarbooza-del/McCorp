package com.isjbar.minercorp.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Mini servidor web que solo entrega el zip del pack. Corre en sus propios
 * hilos, fuera del hilo principal del server.
 */
final class PackServer {

    static final String RUTA = "/mccorp-pack.zip";

    private final HttpServer http;
    private volatile byte[] zip;

    PackServer(int puerto, byte[] zip) throws IOException {
        this.zip = zip;
        this.http = HttpServer.create(new InetSocketAddress(puerto), 0);
        http.createContext(RUTA, exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                byte[] datos = this.zip;
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                boolean head = "HEAD".equals(exchange.getRequestMethod());
                exchange.sendResponseHeaders(200, head ? -1 : datos.length);
                if (!head) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(datos);
                    }
                }
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MinerCorp-Pack-http");
            t.setDaemon(true);
            return t;
        }));
        http.start();
    }

    void actualizar(byte[] zip) {
        this.zip = zip;
    }

    int puerto() {
        return http.getAddress().getPort();
    }

    void parar() {
        http.stop(0);
    }
}
