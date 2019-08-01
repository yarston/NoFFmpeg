package com.slava.noffmpeg.bitmapprovider;

import android.content.res.Resources;
import android.graphics.Bitmap;

public interface BitmapProvider {
    public Bitmap next();
    public BitmapProvider read(Resources res, int resId);
}
