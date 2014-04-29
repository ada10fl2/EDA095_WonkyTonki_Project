package com.wonkytonki.server;

import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.kryo.*;

import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Initializing...");
        Server server = new Server();
        server.start();
        server.bind(54555, 54777);
        
        Kryo k = server.getKryo();
        //k.register(byte[].class);
                
        System.out.println("Running...");
        final List<Connection> pool = Collections.synchronizedList(new ArrayList<Connection>());
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                System.out.println("Connected: "+connection.getRemoteAddressTCP().getHostString());
                pool.add(connection);
                System.out.println("Clients: "+pool.size()+"");
            }
            
            @Override
            public void received (Connection connection, Object object) {
                System.out.println("Message from: "+connection.getRemoteAddressTCP());
                if(object instanceof String) {
                    System.out.println("Data: "+ object);
                    for (Connection peer : pool) {
                        if (peer.getRemoteAddressTCP().equals(connection.getRemoteAddressTCP())) continue;
                        System.out.println("Sending reply to "+peer.getRemoteAddressTCP());
                        peer.sendTCP(object);
                    }
                }
            }

            @Override
            public void disconnected(Connection connection) {
                System.out.println("Disconnected: "+connection.toString());
                pool.remove(connection);
                System.out.println("Clients: "+pool.size()+"");
            }
        });
    }
}