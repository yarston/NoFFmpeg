package com.slava.noffmpeg.frameproviders;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageFramesProvider extends FramesProvider {

    private Bitmap mBmp;

    public ImageFramesProvider(String path, int width, int height, float bpp) {
        mBmp = BitmapFactory.decodeFile(path);
        putBitmap(width, height, bpp);
    }

    public ImageFramesProvider(Resources res, int resId, int width, int height, float bpp) {
        mBmp = BitmapFactory.decodeResource(res, resId);
        putBitmap(width, height, bpp);
    }

    private void putBitmap(int width, int height, float bpp) {
        getEncodedFrames(() -> {
            Bitmap ret = mBmp;
            mBmp = null;
            return ret;
        }, width, height, bpp);
    }
}
