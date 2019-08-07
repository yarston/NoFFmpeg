package com.slava.noffmpeg.mediaworkers;

public class EncodedFrame {

    byte[] data;
    int flags;

    EncodedFrame(byte[] data, int flags) {
        this.data = data;
        this.flags = flags;
    }
}
