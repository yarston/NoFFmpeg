package com.slava.noffmpeg.mediaworkers;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {

    private static final int TIMEOUT_US = 10000;
    private int mVideoTrackIndex = -1;
    private MediaMuxer mMuxer = null;
    private int mFramesEncoded = 0;
    private int mFrameRate = 30;
    private final CodecHolder[] mCodecHolders;

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

    public boolean encodeFrame(int idx) {
        CodecHolder holder = mCodecHolders[idx];
        MediaCodec encoder = holder.encoder;
        MediaCodec.BufferInfo mInfo = holder.info;
        int outIndex = encoder.dequeueOutputBuffer(mInfo, TIMEOUT_US);
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
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.i("Encoder", "INFO_TRY_AGAIN_LATER");
                return false;//break;
            default:
                if(mInfo.size > 0) {
                    if(holder.outBuffer == null) holder.outBuffer = encoder.getOutputBuffers();
                    mInfo.presentationTimeUs = (mFramesEncoded++) * 1000000 / mFrameRate;
                    mMuxer.writeSampleData(mVideoTrackIndex, holder.outBuffer[outIndex], mInfo);
                }
                encoder.releaseOutputBuffer(outIndex, false);
                break;
        }
        return (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
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
