package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;
import android.media.MediaCodecInfo;
import android.util.Log;

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
    private final int mColorFormat;

    public VideoProcessor(List<String> imagePathes, Size size, int colorFormat) {
        mSize = size;
        mColorFormat = colorFormat;
        mImageBuffersYuva.clear();
        for (String path : imagePathes) {
            mImageBuffersYuva.add(FramesProvider.fromFile(path, IMG_WIDTH, IMG_HEIGHT, colorFormat, 0.0f, false));
        }
    }

    public void process(ByteBuffer buff) {
        for (int i = 0; i < mImageBuffersYuva.size(); i++) {
            int x = (i / 2 == 0) ? 0 : mSize.width - IMG_WIDTH;
            int y = (i % 2 == 0) ? 0 : mSize.height - IMG_HEIGHT;
            EncodedFrame frame = mImageBuffersYuva.get(i).next();
            if(frame != null)
                switch (mColorFormat) {
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                        //Log.v("VideoProcessor", "drawYUVAoverYUV420Planar");
                        drawYUVAoverYUV420Planar(frame.data, buff, x, y, IMG_WIDTH, IMG_HEIGHT, mSize.width, mSize.height);
                        break;
                    case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                        //Log.v("VideoProcessor", "drawYUVAoverYUV420Semilanar");
                        drawYUVAoverYUV420Semilanar(frame.data, buff, x, y, IMG_WIDTH, IMG_HEIGHT, mSize.width, mSize.height);

                }
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
