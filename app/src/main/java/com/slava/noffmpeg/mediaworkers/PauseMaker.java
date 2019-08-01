package com.slava.noffmpeg.mediaworkers;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Surface;

import com.slava.noffmpeg.bitmapprovider.BitmapProvider;
import com.slava.noffmpeg.bitmapprovider.GifBitmapProvider;
import com.slava.noffmpeg.bitmapprovider.ImageBitmapProvider;

public class PauseMaker {

    Paint mPaint = new Paint() {{this.setColor(Color.BLACK);}};

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

    public boolean changeStatus() {
        return mIsPaused = !mIsPaused;
    }

    public boolean process(Surface surface, Size size) {
        if(mIsPaused) {
            Rect area = new Rect(0, 0, size.width, size.height);
            Canvas canvas = surface.lockCanvas(area);
            Bitmap nextBitmap;
            if (mBitmapProvider == null || (nextBitmap = mBitmapProvider.next()) == null) canvas.drawPaint(mPaint);
            else canvas.drawBitmap(nextBitmap, null, area, mPaint);
            surface.unlockCanvasAndPost(canvas);
        }
        return mIsPaused;
    }

}