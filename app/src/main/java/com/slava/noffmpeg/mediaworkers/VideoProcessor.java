package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public class VideoProcessor {

    private List<Bitmap> mBitmaps = new ArrayList<>();
    private final int IMG_WIDTH = 100;
    private final int IMG_HEIGHT = 100;

    public VideoProcessor(List<String> imagePathes) {
        for (String path : imagePathes)
            mBitmaps.add(Bitmap.createBitmap(BitmapFactory.decodeFile(path), 0, 0, IMG_WIDTH, IMG_HEIGHT));
    }

    public void process(Surface surface) {

    }
}
