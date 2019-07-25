package com.slava.noffmpeg.mediaworkers;

import android.view.Surface;

import static android.media.MediaCodec.MetricsConstants.MIME_TYPE;

public class Encoder {

    private final Size mSize;

    public Encoder(String path, Size size) {
        mSize = size;
    }

    public void commit() {

    }

    public Surface getSurface() {
        return null;
    }
}
