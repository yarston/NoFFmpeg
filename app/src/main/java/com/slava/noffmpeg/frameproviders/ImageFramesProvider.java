package com.slava.noffmpeg.frameproviders;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

public class ImageFramesProvider extends FramesProvider {

    // Нужно 2 фрейма для статичной картинки: ключевой и промежуточный,
    // иначе пауза будет очень сильно раздувать размер видео
    private int nFrames = 2;

    /**
     * @return всегда промежуточный файл
     */

    @Nullable
    @Override
    public EncodedFrame next() {
        return mFrames.size() < 2 ? null : mFrames.get(1);
    }

    public ImageFramesProvider(String path, int width, int height, float bpp) {
        putBitmaps(BitmapFactory.decodeFile(path), width, height, bpp);
    }

    public ImageFramesProvider(Resources res, int resId, int width, int height, float bpp) {
        putBitmaps(BitmapFactory.decodeResource(res, resId), width, height, bpp);
    }

    private void putBitmaps(Bitmap bmp, int width, int height, float bpp) {
        getEncodedFrames(() -> nFrames-- == 0 ? null : bmp, width, height, bpp);
    }
}
