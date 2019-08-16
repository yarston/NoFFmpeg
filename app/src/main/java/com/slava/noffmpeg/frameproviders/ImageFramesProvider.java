package com.slava.noffmpeg.frameproviders;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

public class ImageFramesProvider extends FramesProvider {

    private static final int KEYFRAME_INTERVAL = 60;
    private int nFrames = 0;
    private boolean mEncoded;

    @Nullable
    @Override
    public EncodedFrame next() {
        if(!mEncoded) return mFrames.get(0);
        if(nFrames == KEYFRAME_INTERVAL) nFrames = 0;
        return mFrames.get(nFrames++);
    }

    ImageFramesProvider(String path, int width, int height, int colorFormat, float bpp, boolean encoded) {
        putBitmaps(BitmapFactory.decodeFile(path), width, height, colorFormat, bpp, encoded);
    }

    public ImageFramesProvider(Resources res, int resId, int width, int height, int colorFormat, float bpp, boolean encoded) {
        putBitmaps(BitmapFactory.decodeResource(res, resId), width, height, colorFormat, bpp, encoded);
    }

    private void putBitmaps(Bitmap bmp, int width, int height, int colorFormat, float bpp, boolean encoded) {
        mEncoded = encoded;
        if(encoded) {
            BitmapEncoder encoder = new BitmapEncoder(width, height, colorFormat, bpp);
            for(int i = 0; i < KEYFRAME_INTERVAL; i++) mFrames.add(encoder.encode(bmp));
            encoder.release();
        } else {
            mFrames.add(convertFrame(bmp, width, height, colorFormat));
        }
    }
}
