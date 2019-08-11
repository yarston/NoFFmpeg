package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {

    private final Size mSize;
    private final List<Bitmap> mBitmaps = new ArrayList<>();
    private final int IMG_WIDTH = 100;
    private final int IMG_HEIGHT = 100;
    private Bitmap mImageBuffer;
    private final Paint mPaint = new Paint();

    public VideoProcessor(List<String> imagePathes, Size size) {
        mSize = size;
        mBitmaps.clear();
        mImageBuffer = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
        for (String path : imagePathes)
            mBitmaps.add(Bitmap.createScaledBitmap(BitmapFactory.decodeFile(path), IMG_WIDTH, IMG_HEIGHT, true));
    }

    public void process(Surface surface, Image img) {
        Canvas canvas = surface.lockCanvas(new Rect(0, 0, mSize.width, mSize.height));
        if(img != null) {
            Image.Plane[] planes = img.getPlanes();
            cvtYUV_420_888_to_RGBA(mImageBuffer, planes[0].getBuffer(), planes[1].getBuffer(), planes[2].getBuffer());
            canvas.drawBitmap(mImageBuffer, 0, 0, mPaint);

            for (int i = 0; i < mBitmaps.size(); i++)
                canvas.drawBitmap(mBitmaps.get(i), (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH, (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT, mPaint);

            img.close();
        }
        surface.unlockCanvasAndPost(canvas);
    }

    public void process(ByteBuffer buff, int offset) {
        for (int i = 0; i < mBitmaps.size(); i++)
            drawYUV(mBitmaps.get(i), (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH, (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT, mSize.width, mSize.height, offset, buff);
    }

    public static native void drawYUV(Bitmap bitmap, int x, int y, int w, int h, int off, ByteBuffer buff);

    public static native void cvtYUV_420_888_to_RGBA(Bitmap  bitmap, ByteBuffer buff_y, ByteBuffer buff_u, ByteBuffer buff_v);

    static {
        System.loadLibrary("yuv2rgb");
    }
}
