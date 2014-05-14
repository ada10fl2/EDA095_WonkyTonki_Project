package com.wonkytonki.app;

/**
 * Created by Filip on 2014-04-29.
 */
public class AudioFrame {
    public int users;
    public long time;
    public byte[] bytes;

    public AudioFrame(int users, long time, byte[] bytes) {
        this.users = users;
        this.time = time;
        this.bytes = bytes;
    }

    public AudioFrame() {}

    public void set(int users, long time, byte[] bytes) {
        this.users = users;
        this.time = time;
        this.bytes = bytes;
    }
}
