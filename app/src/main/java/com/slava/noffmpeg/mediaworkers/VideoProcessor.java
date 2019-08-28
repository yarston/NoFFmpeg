package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodecInfo;
import android.util.Log;

import com.slava.noffmpeg.frameproviders.EncodedFrame;
import com.slava.noffmpeg.frameproviders.FramesProvider;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {

    private final static boolean DEBUG = false;
    private final Size mSize;
    private final List<FramesProvider> mImageBuffersYuva = new ArrayList<>();
    private final int IMG_WIDTH = 200;
    private final int IMG_HEIGHT = 200;
    private final int mColorFormat;

    public VideoProcessor(List<String> imagePathes, Size size, int colorFormat, int rotation) {
        mSize = size;
        mColorFormat = colorFormat;
        mImageBuffersYuva.clear();
        for (String path : imagePathes) {
            mImageBuffersYuva.add(FramesProvider.fromFile(path, IMG_WIDTH, IMG_HEIGHT, colorFormat, 0.0f, false, rotation));
        }
    }

    public void process(Image in) {
        for (int i = 0; i < mImageBuffersYuva.size(); i++) {
            int x = (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH;
            int y = (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT;
            EncodedFrame frame = mImageBuffersYuva.get(i).next();
            if (frame == null) return;
            Image.Plane[] inPlanes = in.getPlanes();
            if(inPlanes.length < 3) return;

            Image.Plane pi0 = inPlanes[0];
            Image.Plane pi1 = inPlanes[1];
            Image.Plane pi2 = inPlanes[2];

           drawPicture(frame.data, x, y, IMG_WIDTH, IMG_HEIGHT, mSize.width, mSize.height,
                    pi0.getBuffer(), pi0.getRowStride(), pi0.getPixelStride(),
                    pi1.getBuffer(), pi1.getRowStride(), pi1.getPixelStride(),
                    pi2.getBuffer(), pi2.getRowStride(), pi2.getPixelStride());

            if(DEBUG && pi0.getBuffer() != null) Log.v("VideoProcessor", "buff0 size:" + pi0.getBuffer().capacity() + " row stride:" + pi0.getRowStride() + " pixel stride:" + pi0.getPixelStride());
            if(DEBUG && pi0.getBuffer() != null) Log.v("VideoProcessor", "buff1 size:" + pi1.getBuffer().capacity() + " row stride:" + pi1.getRowStride() + " pixel stride:" + pi1.getPixelStride());
            if(DEBUG && pi0.getBuffer() != null) Log.v("VideoProcessor", "buff2 size:" + pi2.getBuffer().capacity() + " row stride:" + pi2.getRowStride() + " pixel stride:" + pi2.getPixelStride());

        }
    }

    public static native void drawPicture(ByteBuffer src, int imgx, int imgy, int imgw, int imgh, int bufw, int bufh,
                                          ByteBuffer ib0, int irs0, int ips0,
                                          ByteBuffer ib1, int irs1, int ips1,
                                          ByteBuffer ib2, int irs2, int ips2);

    public static native void copyPicture(int bufw, int bufh,
                                          ByteBuffer ib0, int irs0, int ips0,
                                          ByteBuffer ib1, int irs1, int ips1,
                                          ByteBuffer ib2, int irs2, int ips2,
                                          ByteBuffer ob0, int ors0, int ops0,
                                          ByteBuffer ob1, int ors1, int ops1,
                                          ByteBuffer ob2, int ors2, int ops2);



    public static native void drawRGBoverYUV(Bitmap bitmap, int x, int y, int w, int h, int off, ByteBuffer buff);

    /**
     * Рисование картинки поверх буффера видео
     * @param src Подготовленная картинка в YUV420 с прозрачностью, y - полное разрешение, u,v,a - четверть
     * @param dst Видеобуффер в YUV420
     * @param imgx Левый край картинки, кратно 2
     * @param imgy Верхний край картинки, кратно 2
     * @param imgw Ширина картинки, кратно 2
     * @param imgh Высота картинки, кратно 2
     * @param bufw Ширина видео, кратно 2
     * @param bufh Высота видео, кратно 2
     */
    public static native void drawYUVAoverYUV420Planar(ByteBuffer src, ByteBuffer dst, int imgx, int imgy, int imgw, int imgh, int bufw, int bufh);

    public static native void drawYUVAoverYUV420Semilanar(ByteBuffer src, ByteBuffer dst, int imgx, int imgy, int imgw, int imgh, int bufw, int bufh);


    /**
     * Преобразование Bitmap в формате RGBA8888 в YUVA
     * @param bmp Исходное изображение
     * @param yuva Предварительно выделенная память, исходя из того, что ширина и высота дб чётные
     */
    public static native void bitmapRGBA8888toYUV420Planar(Bitmap bmp, ByteBuffer yuva, int w, int h, boolean toRight, boolean toBottom);

    public static native void bitmapRGBA8888toYUV420SemiPlanar(Bitmap bmp, ByteBuffer yuva, int w, int h, boolean toRight, boolean toBottom);


    public static native void convert_YUV420Planar_to_RGBA(Bitmap  bitmap, ByteBuffer buff);

    public static native void convert_YUV420SemiPlanar_to_RGBA(Bitmap  bitmap, ByteBuffer buff);

    static {
        System.loadLibrary("yuv2rgb");
    }
}
