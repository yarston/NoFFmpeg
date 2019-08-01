package com.slava.noffmpeg.bitmapprovider;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageProvider implements BitmapProvider {

    Bitmap mBitmap = null;

    @Override
    public Bitmap next() {
        return mBitmap;
    }

    @Override
    public BitmapProvider read(Resources res, int resId) {
        mBitmap = BitmapFactory.decodeResource(res, resId);
        return this;
    }
}
