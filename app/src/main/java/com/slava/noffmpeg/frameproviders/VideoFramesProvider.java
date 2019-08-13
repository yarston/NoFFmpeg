package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.util.Log;

import androidx.annotation.Nullable;

import com.slava.noffmpeg.mediaworkers.Decoder;

import static com.slava.noffmpeg.mediaworkers.VideoProcessor.cvtYUV_420_888_to_RGBA;

public class VideoFramesProvider extends FramesProvider {

    private Bitmap mBufferBitmap;
    private boolean isFullyReadden = false;
    private final int mHeight;
    private final int mWidth;
    private final boolean mEncoded;
    private Decoder mDecoder;
    private BitmapEncoder mEncoder = null;

    /**
     * Предпочтительно запускать конструктор в другом потоке
     * @param path путь к видео, которое будет рисоваться поверх целевого
     * @param width ширина видео, к которому будет применяться
     * @param height высота видео, к которому будет применяться
     * @param bpp Бит на пиксель - задаёт качество
     */

    public VideoFramesProvider(String path, int width, int height, float bpp, boolean encoded) {
        mWidth = width;
        mHeight = height;
        mEncoded = encoded;
        mDecoder = new Decoder(path);
        mDecoder.prepare(null, () -> {
            if(mBufferBitmap == null) mBufferBitmap = Bitmap.createBitmap(mDecoder.getSize().width, mDecoder.getSize().height, Bitmap.Config.ARGB_8888);
            cvtYUV_420_888_to_RGBA(mBufferBitmap, mDecoder.mOutputBuffer);
        });
        if(encoded) mEncoder = new BitmapEncoder(width, height, bpp);
    }


    @Nullable
    @Override
    public EncodedFrame first() {
        return this.next();
    }

    /**
     * Ленивое получение видеокадров с кэшированием.
     * @return
     */

    @Nullable
    @Override
    public EncodedFrame next() {
        if(isFullyReadden) return super.next();
        mDecoder.decodeFrame();
        if(mBufferBitmap == null) return null;
        EncodedFrame frame = mEncoded ? mEncoder.encode(mBufferBitmap) : convertFrame(mBufferBitmap, mWidth, mHeight);
        if(frame!= null) mFrames.add(frame);
        if((mDecoder.mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) isFullyReadden = true;
        if(isFullyReadden) {
            mBufferBitmap = null;
            finalize();
        }
        return frame;
    }

    @Override
    protected void finalize() {
        if(mDecoder != null) mDecoder.release();
        if(mEncoder != null) mEncoder.release();
    }
}
