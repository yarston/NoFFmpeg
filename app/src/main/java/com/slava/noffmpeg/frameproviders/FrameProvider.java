package com.slava.noffmpeg.frameproviders;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;
import static com.slava.noffmpeg.mediaworkers.Encoder.TIMEOUT_US;
import static com.slava.noffmpeg.mediaworkers.Encoder.getDefaultFormat;

/**
 * Предоставляет уже закодированные в h264 кадры, которые можно напрямую заливать в Muxer
 */

public abstract class FrameProvider {

    public abstract EncodedFrame next();

    public static void getEncodedFrames(List<Bitmap> in, List<EncodedFrame> out, int width, int height, float bitsPerPixel) {
        MediaFormat format = getDefaultFormat(width, height, 30, bitsPerPixel);
        MediaCodec encoder;
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = encoder.createInputSurface();
        encoder.start();
        Rect area = new Rect(0, 0, width, height);
        Paint paint = new Paint();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        for(Bitmap bmp : in) {
            if (bmp == null) continue;
            Canvas canvas = surface.lockCanvas(area);
            canvas.drawBitmap(bmp, null, area, paint);
            surface.unlockCanvasAndPost(canvas);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int outIndex;

            do {
                outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if(outIndex >= 0) {
                    ByteBuffer buffer = encoder.getOutputBuffers()[outIndex];
                    Log.d("Encoder", "outIndex: " + outIndex + " flags: " + info.flags + " size:" + info.size);
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    byte[] ba = new byte[buffer.remaining()];
                    buffer.get(ba);
                    encoder.releaseOutputBuffer(outIndex, false);
                    Log.d("Encoder", "write " + info.size + " bytes, flags: " + info.flags);
                    try {
                        baos.write(ba);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("Encoder", "outIndex " + outIndex);
            } while (outIndex != INFO_TRY_AGAIN_LATER || (info.flags & BUFFER_FLAG_CODEC_CONFIG) != 0);
            byte[] bytes = baos.toByteArray();
            out.add(new EncodedFrame(bytes, info.flags));
            Log.d("Encoder", "image buffer: " + bytes.length + "\n---");
        }
    }
}