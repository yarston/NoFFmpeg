package com.slava.noffmpeg.frameproviders;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class GifFramesProvider extends FramesProvider {

    public GifFramesProvider(Resources res, int resId, int width, int height, float bpp) {
        AssetFileDescriptor rawAssetsDescriptor = res.openRawResourceFd(resId);
        FileDescriptor fd = rawAssetsDescriptor.getFileDescriptor();
        long off = rawAssetsDescriptor.getStartOffset();
        long len = rawAssetsDescriptor.getLength();
        openGifFd(fd, off, len);
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        getEncodedFrames(() -> fillNextBitmap(bmp) == 1 ? bmp : null, width, height, bpp);
        closeGifFd();
    }

    public GifFramesProvider(String path, int width, int height, float bpp) {
        try(FileInputStream fos = new FileInputStream(path)) {
            FileDescriptor fd = fos.getFD();
            openGifFd(fd, 0, fos.available());
            Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            getEncodedFrames(() -> fillNextBitmap(bmp) == 1 ? bmp : null, width, height, bpp);
            closeGifFd();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void putBitmaps(FileDescriptor fd, int width, int height, float bpp) {

    }

    private static native void openGifFd(FileDescriptor fd, long off, long len);

    private static native void closeGifFd();

    private static native int fillNextBitmap(Bitmap bitmap);

    private static native int getWidth();

    private static native int getHeight();

    static {
        System.loadLibrary("gifdec");
    }
}
