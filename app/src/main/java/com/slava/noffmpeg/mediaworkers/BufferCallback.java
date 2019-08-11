package com.slava.noffmpeg.mediaworkers;

import android.media.MediaCodec;

import java.nio.ByteBuffer;

public interface BufferCallback {
    public void onBufferReady(ByteBuffer buffer, MediaCodec.BufferInfo info);
}
