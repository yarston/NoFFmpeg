package com.slava.noffmpeg.mediaworkers;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {

    private static final int TIMEOUT_US = 10000;
    private ByteBuffer[] outputBuffers;
    MediaCodec.BufferInfo mBufferInfo;
    MediaCodec mEncoder;
    Surface mSurface;
    private Size mSize;
    private static final float BPP = 0.25f;
    private MediaCodec.BufferInfo mInfo;
    private ByteBuffer[] mOutputBuffers;

    private static final String MIME_TYPE = "video/avc";
    private static final boolean DEBUG = true;	// TODO set false on release
    private static final String TAG = "MediaVideoEncoder";

    public Encoder(String path, Size size, MediaFormat inputFormat) {
        mSize = size;
        int fps = 30;
        if(inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            fps = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", size.width, size.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (BPP * fps * size.width * size.height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        Log.i("Encoder", "format: " + format);
        try {
            mEncoder = MediaCodec.createEncoderByType("video/avc");
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mEncoder.createInputSurface();
            //mEncoder.start();
            //outputBuffers = mEncoder.getOutputBuffers();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void encodeFrame() {
        if(mOutputBuffers == null) {
            outputBuffers = mEncoder.getOutputBuffers();
        }
        int outIndex = mEncoder.dequeueOutputBuffer(mInfo, TIMEOUT_US);
        ByteBuffer[] mOutputBuffers;
        //if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) mIsReady = false;
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                mOutputBuffers = mEncoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                break;
            default:
                ByteBuffer data = outputBuffers[outIndex];
                data.position(mBufferInfo.offset);
                data.limit(mBufferInfo.offset + mBufferInfo.size);

                mEncoder.releaseOutputBuffer(outIndex, true);
                //if (mCallback != null) mCallback.run();
                break;
        }
    }

    public Surface getSurface() {
        return mSurface;
    }

    public void release() {
        if (mEncoder == null) return;
        mEncoder.stop();
        mEncoder.release();
        mSurface.release();
        mEncoder = null;
        mSurface = null;
    }
}
