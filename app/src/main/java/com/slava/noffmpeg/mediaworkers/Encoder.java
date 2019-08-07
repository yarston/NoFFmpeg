package com.slava.noffmpeg.mediaworkers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;

public class Encoder {

    private static final int TIMEOUT_US = 10000;
    private int mVideoTrackIndex = -1;
    private MediaMuxer mMuxer = null;
    private int mFramesEncoded = 0;
    private int mFrameRate = 30;
    private final CodecHolder[] mCodecHolders;
    private Runnable mRequetSwitch = null;
    private EncodedFrame mPauseFrame = null;
    private boolean mRequestResume = false;
    private boolean mRequestKeyFrame = false;

    //Нужно запилить паузу. Варианты:
    //1. Рисовать на том же холсте, который используется в MediaProjection, нельзя через Canvas, крашится. Но может быть, можно как-то иначе, хз.
    //2. Писать экрвн в промежуточный канвас, рисовать его на холсте и отправлять в канвас кодека, но это оверхэд
    //3. Использовать 2 кодека со своими холстами и переключать их в микшере - так и сделаю.

    public Encoder(String path, Size size, MediaFormat inputFormat, float bitsPerPixel, int n) {
        if(inputFormat != null && inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            mFrameRate = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (bitsPerPixel * mFrameRate * size.width * size.height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        Log.i("Encoder", "format: " + format);
        mCodecHolders = new CodecHolder[n];
        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            for(int i = 0; i < mCodecHolders.length; i++) mCodecHolders[i] = new CodecHolder(format);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MediaFormat getDefaultFormat(Size size, int frameRate, float bitsPerPixel) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (bitsPerPixel * frameRate * size.width * size.height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        return format;
    }

    public static void getEncodedFrames(List<Bitmap> in, List<EncodedFrame> out, Size size, float bitsPerPixel) {
        MediaFormat format = getDefaultFormat(size, 30, bitsPerPixel);
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
        Rect area = new Rect(0, 0, size.width, size.height);
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

    public void writeEncodedData(EncodedFrame frame) {
        MediaCodec.BufferInfo info = new  MediaCodec.BufferInfo();
        ByteBuffer bb = ByteBuffer.wrap(frame.data);
        info.presentationTimeUs = (mFramesEncoded++) * 1000000 / mFrameRate;
        info.offset = 0;
        info.size = frame.data.length;
        info.flags = 9;
        mMuxer.writeSampleData(mVideoTrackIndex, bb, info);
    }

    public boolean encodeFrame(int idx) {

        CodecHolder holder = mCodecHolders[idx];
        MediaCodec encoder = holder.encoder;
        MediaCodec.BufferInfo info = holder.info;

        if(isPaused() && !mRequestResume) {
            writeEncodedData(mPauseFrame);
            return false;
        }

        int outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US);

        if(mRequestKeyFrame) {
            Bundle param = new Bundle();
            param.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            encoder.setParameters(param);
            mRequestKeyFrame = false;
        }

        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_BUFFERS_CHANGED");
                holder.outBuffer = encoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_FORMAT_CHANGED");
                if(mVideoTrackIndex < 0) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    mVideoTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                }
                break;
            case INFO_TRY_AGAIN_LATER:
                Log.i("Encoder", "INFO_TRY_AGAIN_LATER");
                return false;//break;
            default:
                // Если нужно выйти из паузы, начинаем кодировать входящие фреймы.
                // Но сначала нужно дождаться ключевого кадра, только после его записи
                // можно прекратить писать фреймы паузы и начать писать фреймы видео.
                // Он хоть и запрашивается вне очереди, но система не гарантирует его немедленное появление.
                // В противном случае закодированный фрейм видео необходимо отбросить
                // и записать вместо него фрейм паузы.
                if(mRequestResume && (info.flags & BUFFER_FLAG_KEY_FRAME) == 0) {
                    writeEncodedData(mPauseFrame);
                } else {

                    if (mRequestResume) {
                        mRequestResume = false;
                        mPauseFrame = null;
                    }

                    if (info.size > 0) {
                        if (holder.outBuffer == null) holder.outBuffer = encoder.getOutputBuffers();
                        info.presentationTimeUs = (mFramesEncoded++) * 1000000 / mFrameRate;
                        ByteBuffer buffer = holder.outBuffer[outIndex];
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                        if ((info.flags & BUFFER_FLAG_KEY_FRAME) != 0) {
                            if (mRequetSwitch != null) {
                                mRequetSwitch.run();
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(outIndex, false);
                break;
        }
        return (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
    }

    public void setPause(EncodedFrame pauseFrame) {
        if(mPauseFrame == null) mPauseFrame = pauseFrame;
    }

    public boolean isPaused() {
        return mPauseFrame != null;
    }

    public void resume() {
        mRequestResume = true;
        mRequestKeyFrame = true;
    }

    public Surface getSurface(int idx) {
        return mCodecHolders[idx].surface;
    }

    public void release() {
        for(int i = 0; i < mCodecHolders.length; i++) {
            CodecHolder holder = mCodecHolders[i];
            if(holder != null) {
                if(holder.encoder != null) {
                    holder.encoder.signalEndOfInputStream();
                    holder.encoder.stop();
                    holder.encoder.release();
                }
                if(holder.surface != null) holder.surface.release();
            }
        }
        mMuxer.stop();
        mMuxer.release();
    }

    private class CodecHolder {

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] outBuffer;
        MediaCodec encoder;
        Surface surface;

        CodecHolder(MediaFormat format) throws IOException {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = encoder.createInputSurface();
            encoder.start();
        }
    }
}
