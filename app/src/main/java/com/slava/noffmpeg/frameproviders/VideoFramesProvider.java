package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;

import androidx.annotation.Nullable;

import com.slava.noffmpeg.mediaworkers.Decoder;

import static com.slava.noffmpeg.mediaworkers.VideoProcessor.convert_YUV420Planar_to_RGBA;
import static com.slava.noffmpeg.mediaworkers.VideoProcessor.convert_YUV420SemiPlanar_to_RGBA;

public class VideoFramesProvider extends FramesProvider {

    private final int mColorFormat;
    private final int mRotation;
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
     * @param useHw
     */

    VideoFramesProvider(String path, int width, int height, int colorFormat, float bpp, boolean encoded, int rotation, boolean useHw) {
        mWidth = width;
        mHeight = height;
        mEncoded = encoded;
        mColorFormat = colorFormat;
        mRotation = rotation;
        mDecoder = new Decoder(path);
        mDecoder.prepare(null, useHw);
        mDecoder.setCallback(() -> {
            if(mBufferBitmap == null) mBufferBitmap = Bitmap.createBitmap(mDecoder.getSize().width, mDecoder.getSize().height, Bitmap.Config.ARGB_8888);
            switch (colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    convert_YUV420Planar_to_RGBA(mBufferBitmap, mDecoder.mOutputBuffer);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    convert_YUV420SemiPlanar_to_RGBA(mBufferBitmap, mDecoder.mOutputBuffer);
            }
        });
        if(encoded) mEncoder = new BitmapEncoder(width, height, colorFormat, bpp, rotation);
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
        EncodedFrame frame = mEncoded ? mEncoder.encode(mBufferBitmap) : convertFrame(mBufferBitmap, mWidth, mHeight, mColorFormat, mRotation);
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
