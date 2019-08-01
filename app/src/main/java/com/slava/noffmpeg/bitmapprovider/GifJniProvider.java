package com.slava.noffmpeg.bitmapprovider;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;

import com.slava.noffmpeg.R;

import java.io.FileDescriptor;

public class GifJniProvider implements BitmapProvider {

    Bitmap mBitmap = null;

    @Override
    public Bitmap next() {
        fillNextBitmap(mBitmap);
        return mBitmap;
    }

    @Override
    public BitmapProvider read(Resources res, int resId) {
        AssetFileDescriptor rawAssetsDescriptor = res.openRawResourceFd(R.raw.giphy);
        FileDescriptor fd = rawAssetsDescriptor.getFileDescriptor();
        long off = rawAssetsDescriptor.getStartOffset();
        long len = rawAssetsDescriptor.getLength();
        openGifFd(fd, off, len);
        mBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        return this;
    }

    private static native void openGifFd(FileDescriptor fd, long off, long len);

    private static native void fillNextBitmap(Bitmap bitmap);

    private static native int getWidth();

    private static native int getHeight();

    static {
        System.loadLibrary("gifdec");
    }
}
