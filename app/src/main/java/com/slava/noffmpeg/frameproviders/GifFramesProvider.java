package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import java.io.FileDescriptor;

public class GifFramesProvider extends FramesProvider {

    private final int mHeight;
    private final int mWidth;
    private final boolean mEncoded;
    private final int mColorFormat;
    private final int mRotation;
    private long mGifptr = 0;
    private Bitmap mBufferBitmap;
    private boolean isFullyReadden = false;
    private BitmapEncoder mEncoder = null;

    public GifFramesProvider(String path, int width, int height, int colorFormat, float bpp, boolean encoded, int rotation) {
        mWidth = width;
        mHeight = height;
        mEncoded = encoded;
        mColorFormat = colorFormat;
        mRotation = rotation;
        mGifptr = openGifbyPath(path);
        if(mGifptr == 0) return;
        if(encoded) mEncoder = new BitmapEncoder(width, height, colorFormat, bpp, rotation);
    }

    @Nullable
    @Override
    public EncodedFrame first() {
        return this.next();
    }

    /**
     * Ленивое получение анимированных кадров с кэшированием
     * @return
     */

    @Nullable
    @Override
    public EncodedFrame next() {
        if(isFullyReadden) return super.next();
        if(mGifptr == 0) return null;
        if(mBufferBitmap == null) mBufferBitmap = Bitmap.createBitmap(getWidth(mGifptr), getHeight(mGifptr), Bitmap.Config.ARGB_8888);
        if(fillNextBitmap(mBufferBitmap, mGifptr) == 0) isFullyReadden = true;

        EncodedFrame frame = mEncoded ? mEncoder.encode(mBufferBitmap) : convertFrame(mBufferBitmap, mWidth, mHeight, mColorFormat, mRotation);

        mFrames.add(frame);
        if(isFullyReadden) {
            mBufferBitmap = null;
            finalize();
            mGifptr = 0;
            mEncoder = null;
        }
        return frame;
    }

    private static native long openGifbyPath(String path);

    private static native long openGifFd(FileDescriptor fd, long off, long len);

    private static native void closeGifFd(long gifptr);

    private static native int fillNextBitmap(Bitmap bitmap, long gifptr);

    private static native int getWidth(long gifptr);

    private static native int getHeight(long gifptr);

    @Override
    protected void finalize() {
        if(mGifptr != 0) closeGifFd(mGifptr);
        if(mEncoder != null) mEncoder.release();
    }

    static {
        System.loadLibrary("gifdec");
    }
}
