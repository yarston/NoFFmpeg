package com.slava.noffmpeg.mediaworkers;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {

    private static final float BPP = 0.25f;
    private static final int TIMEOUT_US = 10000;
    private int mVideoTrackIndex;
    private MediaCodec mEncoder;
    private Surface mSurface;
    private Size mSize;
    private MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();;
    private ByteBuffer[] mOutputBuffers;

    private MediaMuxer mMuxer = null;
    private int mFramesEncoded = 0;
    private int mFrameRate = 30;
    private boolean mIsMuxerStarted = false;

    public Encoder(String path, Size size, MediaFormat inputFormat) {
        mSize = size;
        if(inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            mFrameRate = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", size.width, size.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (BPP * mFrameRate * size.width * size.height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        Log.i("Encoder", "format: " + format);
        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mEncoder = MediaCodec.createEncoderByType("video/avc");
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mEncoder.createInputSurface();
            mEncoder.start();
            mOutputBuffers = mEncoder.getOutputBuffers();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Canvas canvas = mSurface.lockCanvas(new Rect(0,0,  size.width, size.height));
        mSurface.unlockCanvasAndPost(canvas);
    }

    public void encodeFrame() {
        int outIndex = mEncoder.dequeueOutputBuffer(mInfo, TIMEOUT_US);
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_BUFFERS_CHANGED");
                mOutputBuffers = mEncoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_FORMAT_CHANGED");
                MediaFormat newFormat = mEncoder.getOutputFormat();
                mVideoTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mIsMuxerStarted = true;
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.i("Encoder", "INFO_TRY_AGAIN_LATER");
                break;
            default:
                if(mInfo.size > 0) {
                    mInfo.presentationTimeUs = computePresentationTime(mFramesEncoded++, mFrameRate);
                    mMuxer.writeSampleData(mVideoTrackIndex, mOutputBuffers[outIndex], mInfo);
                }
                mEncoder.releaseOutputBuffer(outIndex, false);
                break;
        }
    }

    private long computePresentationTime(int frameIndex, int frameRate) {
        return frameIndex * 1000000 / frameRate;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public void release() {
        if (mEncoder == null) return;
        mEncoder.signalEndOfInputStream();
        mEncoder.stop();
        mEncoder.release();
        mMuxer.stop();
        mMuxer.release();
        mSurface.release();
        mEncoder = null;
        mSurface = null;
    }
}
