package com.slava.noffmpeg.frameproviders;

import java.nio.ByteBuffer;

public class EncodedFrame {

    public ByteBuffer data;
    public int flags;

    public EncodedFrame(ByteBuffer data, int flags) {
        this.data = data;
        this.flags = flags;
    }
}
