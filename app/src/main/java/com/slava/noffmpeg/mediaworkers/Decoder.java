package com.slava.noffmpeg.mediaworkers;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Decoder {

    private static final int TIMEOUT_US = 10000;
    private final MediaExtractor mExtractor;
    private long mDuration;
    private MediaCodec mDecoder;
    private int mFPS = 30;
    private String mVideoPath;
    private MediaFormat mFormat;
    private MediaCodec.BufferInfo mInfo;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    private Runnable mCallback;
    private boolean mIsReady = false;

    public Decoder(String videoPath) {
        mVideoPath = videoPath;
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(mVideoPath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                mExtractor.selectTrack(i);
                mFormat = format;
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE))
                    mFPS = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                if (format.containsKey(MediaFormat.KEY_DURATION))
                    mDuration = format.getLong(MediaFormat.KEY_DURATION);
                break;
            }
        }
    }

    @Nullable
    public Size getSize() {
        if (mFormat == null) return null;
        return new Size(mFormat.getInteger(MediaFormat.KEY_WIDTH), mFormat.getInteger(MediaFormat.KEY_HEIGHT));
    }

    @Nullable
    public MediaFormat getFormat() {
        return mFormat;
    }

    public boolean prepare(Surface surface, Runnable callback) {
        mCallback = callback;
        if (mFormat == null) return false;
        try {
            mDecoder = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        mDecoder.configure(mFormat, surface, null, 0);
        if (mDecoder == null) return false;
        mDecoder.start();
        mInfo = new MediaCodec.BufferInfo();
        mInputBuffers = mDecoder.getInputBuffers();
        mOutputBuffers = mDecoder.getOutputBuffers();
        mIsReady = true;
        return true;
    }

    public int getMaxFrames() {
        return (int) (mDuration * mFPS / 1000000);
    }

    public boolean haveFrame() {
        return mIsReady;
    }

    public void decodeFrame() {
        int inIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
        if (inIndex < 0) return;

        ByteBuffer buffer = mInputBuffers[inIndex];

        int sampleSize = mExtractor.readSampleData(buffer, 0);
        if (mExtractor.advance() && sampleSize > 0) {
            mDecoder.queueInputBuffer(inIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
        } else {
            mIsReady = false;
        }

        int outIndex = mDecoder.dequeueOutputBuffer(mInfo, TIMEOUT_US);
        if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) mIsReady = false;
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                mOutputBuffers = mDecoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                break;
            default:
                mDecoder.releaseOutputBuffer(outIndex, true);
                if (mCallback != null) mCallback.run();
                break;
        }
    }

    public void release() {
        if (mDecoder == null) return;
        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;
    }
}