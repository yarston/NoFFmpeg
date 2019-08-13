package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;

import com.slava.noffmpeg.frameproviders.EncodedFrame;
import com.slava.noffmpeg.frameproviders.FramesProvider;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {

    private final Size mSize;
    private final List<FramesProvider> mImageBuffersYuva = new ArrayList<>();
    private final int IMG_WIDTH = 100;
    private final int IMG_HEIGHT = 100;

    public VideoProcessor(List<String> imagePathes, Size size) {
        mSize = size;
        mImageBuffersYuva.clear();
        for (String path : imagePathes) {
            mImageBuffersYuva.add(FramesProvider.fromFile(path, IMG_WIDTH, IMG_HEIGHT, 0, false));
        }
    }

    public void process(ByteBuffer buff) {
        for (int i = 0; i < mImageBuffersYuva.size(); i++) {
            int x = (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH;
            int y = (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT;
            EncodedFrame frame = mImageBuffersYuva.get(i).next();
            if(frame != null) drawYUVAoverYUV(frame.data, buff, x, y, IMG_WIDTH, IMG_HEIGHT, mSize.width, mSize.height);
        }
    }

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
    public static native void drawYUVAoverYUV(ByteBuffer src, ByteBuffer dst, int imgx, int imgy, int imgw, int imgh, int bufw, int bufh);

    /**
     * Преобразование Bitmap в формате RGBA8888 в YUVA
     * @param bmp Исходное изображение
     * @param yuva Предварительно выделенная память, исходя из того, что ширина и высота дб чётные
     */
    public static native void bitmapRGBA8888toYUVA(Bitmap bmp, ByteBuffer yuva, int w, int h, boolean toRight, boolean toBottom);

    public static native void cvtYUV_420_888_to_RGBA(Bitmap  bitmap, ByteBuffer buff);

    static {
        System.loadLibrary("yuv2rgb");
    }
}
