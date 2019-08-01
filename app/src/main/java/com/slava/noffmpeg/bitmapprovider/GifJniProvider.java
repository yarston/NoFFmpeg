package com.slava.noffmpeg.bitmapprovider;

import android.content.res.Resources;
import android.graphics.Bitmap;

public class GifJniProvider implements BitmapProvider {

    @Override
    public Bitmap next() {
        return null;
    }

    @Override
    public BitmapProvider read(Resources res, int resId) {
        return null;
    }

    static {
        System.loadLibrary("gifdec");
    }
}
