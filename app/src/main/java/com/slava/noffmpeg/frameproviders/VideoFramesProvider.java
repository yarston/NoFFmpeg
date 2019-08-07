package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.media.Image;

import com.slava.noffmpeg.mediaworkers.Decoder;

import static com.slava.noffmpeg.mediaworkers.VideoProcessor.cvtYUV_420_888_to_RGBA;

public class VideoFramesProvider extends FramesProvider {

    private Bitmap mSwapBitmap;
    private Bitmap mBufferBitmap;

    /**
     * Предпочтительно запускать конструктор в другом потоке
     * @param path путь к видео, которое будет рисоваться поверх целевого
     * @param width ширина видео, к которому будет применяться
     * @param height высота видео, к которому будет применяться
     * @param bpp Бит на пиксель - задаёт качество
     */

    public VideoFramesProvider(String path, int width, int height, float bpp) {
        Decoder decoder = new Decoder(path);
        decoder.prepare(null, () -> {
            Image picture = decoder.getOutputImage();

            if(mBufferBitmap == null) mBufferBitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
            Image.Plane[] planes = picture.getPlanes();
            cvtYUV_420_888_to_RGBA(mBufferBitmap, planes[0].getBuffer(), planes[1].getBuffer(), planes[2].getBuffer());
            mSwapBitmap = mBufferBitmap;
            getEncodedFrames(() -> {
                Bitmap ret = mSwapBitmap;
                mSwapBitmap = null;
                return ret;
            }, width, height, bpp);
        });
        while (decoder.haveFrame()) decoder.decodeFrame();
        decoder.release();
    }
}
