package com.slava.noffmpeg.frameproviders;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import java.util.List;

public class ImageFrameProvider extends FrameProvider {

    EncodedFrame mFrame;

    public ImageFrameProvider(Resources res, int resId, int width, int height) {
        List<Bitmap> in = new ArrayList<>();
        List<EncodedFrame> out = new ArrayList<>();
        in.add(BitmapFactory.decodeResource(res, resId));
        getEncodedFrames(in, out, width, height, 1);
        mFrame = out.isEmpty() ? null : out.get(0);
    }

    @Override
    public EncodedFrame next() {
        return mFrame;
    }
}
