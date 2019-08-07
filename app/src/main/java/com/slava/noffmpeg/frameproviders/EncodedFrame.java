package com.slava.noffmpeg.frameproviders;

public class EncodedFrame {

    public byte[] data;
    public int flags;

    public EncodedFrame(byte[] data, int flags) {
        this.data = data;
        this.flags = flags;
    }
}
