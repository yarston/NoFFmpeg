package com.slava.noffmpeg.mediaworkers;

import android.content.res.Resources;

import com.slava.noffmpeg.bitmapprovider.BitmapProvider;
import com.slava.noffmpeg.bitmapprovider.GifBitmapProvider;
import com.slava.noffmpeg.bitmapprovider.ImageBitmapProvider;

public class PauseMaker {

    BitmapProvider mBitmapProvider = null;
    boolean mIsPaused = false;

    public PauseMaker() {

    }

    public void setGif(Resources res, int resId) {
        mBitmapProvider = new GifBitmapProvider().read(res, resId);
    }

    public void setImage(Resources res, int resId) {
        mBitmapProvider = new ImageBitmapProvider().read(res, resId);
    }

    public void setVideo(Resources res, int resId) {
        //TODO
    }

    public void setStatus(boolean isPaused) {
        if(isPaused == mIsPaused) return;

        mIsPaused = isPaused;
    }

}