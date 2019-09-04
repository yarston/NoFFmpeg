package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static com.slava.noffmpeg.mediaworkers.Encoder.TIMEOUT_US;
import static com.slava.noffmpeg.mediaworkers.Encoder.getDefaultFormat;
import static com.slava.noffmpeg.mediaworkers.VideoProcessor.bitmapRGBA8888toYUV420Planar;

/**
 * Предоставляет уже закодированные в h264 кадры, которые можно напрямую заливать в Muxer
 */

public abstract class FramesProvider {

    final List<EncodedFrame> mFrames = new ArrayList<>();
    private int mIndex = 0;

    public static FramesProvider fromFile(String path, int width, int height, int colorFormat, float bpp, boolean encoded, int rotation) {
        int i = path.lastIndexOf('.');
        if (i > 0) {
            switch (path.substring(i + 1)) {
                case "png":
                case "jpg":
                case "jpeg":
                    return new ImageFramesProvider(path, width, height, colorFormat, bpp, encoded, rotation);
                case "gif":
                    return new GifFramesProvider(path, width, height, colorFormat, bpp, encoded, rotation);
                case "mp4":
                    return new VideoFramesProvider(path, width, height, colorFormat, bpp, encoded, rotation, true);
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

    public void reset() {
        mIndex = 0;
    }

    EncodedFrame convertFrame(Bitmap bmp, int width, int height, int colorFormat, int rotation) {
        if (width % 2 == 1) width++;
        if (height % 2 == 1) height++;
        Bitmap scaled = null;
        if(rotation == 0) {
            scaled = Bitmap.createScaledBitmap(bmp, width, height, true);
        } else {
            scaled = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(scaled);
            Matrix m = new Matrix();
            m.setScale((float) width / bmp.getWidth(), (float) height / bmp.getHeight());
            m.postRotate(-rotation, width * 0.5f, height * 0.5f);
            c.drawBitmap(bmp, m, new Paint());

        }
        ByteBuffer bb = ByteBuffer.allocateDirect(width * height * 7 / 4);
        bitmapRGBA8888toYUV420Planar(scaled, bb, width, height, false, false);
        return new EncodedFrame(bb, 0);
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    public static void getEmptyBlock(int w, int h) {
        BitmapEncoder enc = new BitmapEncoder(w, h, COLOR_FormatSurface, 1, 0);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for(int i = 0; i < 1024; i++) {
            enc.encode(bmp);
        }
    }

    static class BitmapEncoder {

        MediaCodec encoder;
        Surface surface;
        Rect area;
        Paint paint = new Paint();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        BitmapEncoder(int width, int height, int colorFormat, float bitsPerPixel, int rotation) {
            MediaFormat format = getDefaultFormat(width, height, 30, colorFormat, 30 * width * height / 40);
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
                    Log.d("Encoder", "write " + info.size + " bytes, flags: " + info.flags);
                    if (info.size > 0 && (info.flags & BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        //Log.d("Encoder", "add some");
                        if(bytes.length < 20) Log.d("Encoder", "string:" + bytesToHex(bytes));
                        return new EncodedFrame(ByteBuffer.wrap(bytes), info.flags);
                    }
                }
            } while (outIndex < 0 || (info.flags & BUFFER_FLAG_CODEC_CONFIG) != 0);
            Log.d("Encoder", "add null");
            return null;
        }

        void release() {
            encoder.stop();
            encoder.release();
        }
    }
}
