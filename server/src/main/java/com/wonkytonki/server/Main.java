package com.wonkytonki.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Initializing...");
        Server server = new Server();
        server.start();
        server.bind(1337, 1338);
        
        System.out.println("Running...");
        server.addListener(new Listener() {
            public void received (Connection connection, Object object) {
                connection.sendTCP(connection.getRemoteAddressTCP().toString());
            }
        });
    }
}