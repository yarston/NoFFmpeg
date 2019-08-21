package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static com.slava.noffmpeg.mediaworkers.Encoder.TIMEOUT_US;
import static com.slava.noffmpeg.mediaworkers.Encoder.getDefaultFormat;
import static com.slava.noffmpeg.mediaworkers.VideoProcessor.bitmapRGBA8888toYUV420Planar;
import static com.slava.noffmpeg.mediaworkers.VideoProcessor.bitmapRGBA8888toYUV420SemiPlanar;

/**
 * Предоставляет уже закодированные в h264 кадры, которые можно напрямую заливать в Muxer
 */

public abstract class FramesProvider {

    final List<EncodedFrame> mFrames = new ArrayList<>();
    private int mIndex = 0;

    public static FramesProvider fromFile(String path, int width, int height, int colorFormat, float bpp, boolean encoded) {
        int i = path.lastIndexOf('.');
        if (i > 0) {
            switch (path.substring(i + 1)) {
                case "png":
                case "jpg":
                case "jpeg":
                    return new ImageFramesProvider(path, width, height, colorFormat, bpp, encoded);
                case "gif":
                    return new GifFramesProvider(path, width, height, colorFormat, bpp, encoded);
                case "mp4":
                    return new VideoFramesProvider(path, width, height, colorFormat, bpp, encoded);
            }
        }
        return null;
    }

    /**
     * Для случая, когда предоставляется только лишь 1 кадр, нужно предоставить отдельно
     * ключевой фрейм и промежуточный
     * @return первый (ключевой) фрейм
     */

    @Nullable
    public EncodedFrame first() {
        mIndex = 1;
        return mFrames.isEmpty() ? null : mFrames.get(0);
    }

    /**
     * @return ключевой либо промежуточный файл
     */

    @Nullable
    public EncodedFrame next() {
        return mFrames.isEmpty() ? null : mFrames.get(mIndex == mFrames.size() - 1 ? (mIndex = 0) : mIndex++);
    }

    EncodedFrame convertFrame(Bitmap bmp, int width, int height, int colorFormat) {
        if (width % 2 == 1) width++;
        if (height % 2 == 1) height++;
        Bitmap scaled = Bitmap.createScaledBitmap(bmp, width, height, true);
        ByteBuffer bb = ByteBuffer.allocateDirect(width * height * 7 / 4);
        bitmapRGBA8888toYUV420Planar(scaled, bb, width, height, false, false);
        return new EncodedFrame(bb, 0);
    }

    class BitmapEncoder {

        MediaCodec encoder;
        Surface surface;
        Rect area;
        Paint paint = new Paint();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        BitmapEncoder(int width, int height, int colorFormat, float bitsPerPixel) {
            MediaFormat format = getDefaultFormat(width, height, 30, colorFormat, bitsPerPixel);
            try {
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            area = new Rect(0, 0, width, height);
            encoder.start();
        }

        EncodedFrame encode(Bitmap bmp) {
            Canvas canvas = surface.lockCanvas(area);
            canvas.drawBitmap(bmp, null, area, paint);
            surface.unlockCanvasAndPost(canvas);
            int outIndex;
            do {
                outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIndex >= 0) {
                    ByteBuffer buffer = encoder.getOutputBuffers()[outIndex];
                    //Log.d("Encoder", "outIndex: " + outIndex + " flags: " + info.flags + " size:" + info.size);
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    byte[] bytes = new byte[info.size];
                    buffer.get(bytes);
                    encoder.releaseOutputBuffer(outIndex, false);
                    //Log.d("Encoder", "write " + info.size + " bytes, flags: " + info.flags);
                    if (info.size > 0 && (info.flags & BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        //Log.d("Encoder", "add some");
                        return new EncodedFrame(ByteBuffer.wrap(bytes), info.flags);
                    }
                }
            } while (outIndex < 0 || (info.flags & BUFFER_FLAG_CODEC_CONFIG) != 0);
            //Log.d("Encoder", "add null");
            return null;
        }

        void release() {
            encoder.stop();
            encoder.release();
        }
    }
}
