package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

public class SkipFrameProvider extends FramesProvider {

    private EncodedFrame mFrame;

    public SkipFrameProvider(int w, int h) {
        FramesProvider.BitmapEncoder enc = new FramesProvider.BitmapEncoder(w, h, COLOR_FormatSurface, 1, 0);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for(int i = 0; i < 2; i++) {
            enc.encode(bmp);
        }
    }

    @Nullable
    @Override
    public EncodedFrame next() {
        return mFrame;
    }
}
