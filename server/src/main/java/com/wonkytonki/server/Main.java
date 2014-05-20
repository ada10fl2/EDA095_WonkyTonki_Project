package com.wonkytonki.server;

import com.esotericsoftware.kryonet.*;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.minlog.*;

import com.wonkytonki.common.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class Main {

    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54777;

    public static void main(String[] args) throws IOException {
        Log.set(Log.LEVEL_DEBUG);
        Log.setLogger(new MyLogger());

        final Server server = new Server(1024 * 1024, 1024 * 1024);
        server.start();
        server.bind(TCP_PORT, UDP_PORT);

        Kryo k = server.getKryo();
        k.register(AudioFrame.class);
        k.register(byte[].class);

        final List<Connection> pool = Collections.synchronizedList(new ArrayList<Connection>());

        server.addListener(new Listener() {
            @Override
            public void connected(Connection c) {
                super.connected(c);
                pool.add(c);
                System.out.println("Clients: "+pool.size()+"");
            }
            
            @Override
            public void received (Connection c, Object object) {
                super.received(c, object);
                if(object instanceof AudioFrame) {
                    AudioFrame af = (AudioFrame) object;
                    af.users = pool.size();
                    if (true || af.time > System.currentTimeMillis() - 1000) {
                        server.sendToAllExceptTCP(c.getID(), af);
                    } else {
                        System.out.println("Discarding old audio frame: "+af.time);
                    }
                }
            }

            @Override
            public void disconnected(Connection c) {
                super.disconnected(c);
                pool.remove(c);
                System.out.println("Clients: "+pool.size()+"");
            }
        });

        InetAddress local = InetAddress.getLocalHost();
        System.out.printf("Server running on %s(%s):%s%n", local.getCanonicalHostName(),
                local.getHostAddress() ,TCP_PORT);
    }

    public static class MyLogger extends Log.Logger {
        public void log (int level, String category, String message, Throwable ex) {
            StringBuilder builder = new StringBuilder(256);
            //builder.append(new Date());
            //builder.append(' ');
            //builder.append(level);
            builder.append('[');
            builder.append(category);
            builder.append("] ");
            builder.append(message);
            if (ex != null) {
                StringWriter writer = new StringWriter(256);
                ex.printStackTrace(new PrintWriter(writer));
                builder.append('\n');
                builder.append(writer.toString().trim());
            }
            System.out.println(builder);
        }
    }
}