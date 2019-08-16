package com.slava.noffmpeg.mediaworkers;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
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
    public MediaCodec.BufferInfo mInfo;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    private Runnable mCallback;
    private BufferCallback mBufferCallback;
    private boolean mIsReady = false;
    private Image mOutputImage;
    public ByteBuffer mOutputBuffer;

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
        Log.v("decodeFrame", "size " + mFormat.getInteger(MediaFormat.KEY_WIDTH) + " " + mFormat.getInteger(MediaFormat.KEY_HEIGHT));
        return new Size(mFormat.getInteger(MediaFormat.KEY_WIDTH), mFormat.getInteger(MediaFormat.KEY_HEIGHT));
    }

    @Nullable
    public MediaFormat getFormat() {
        return mFormat;
    }

    public boolean prepare(Surface surface) {
        if (mFormat == null) return false;
        try {
            mDecoder = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        //mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, 21);
        mDecoder.configure(mFormat, surface, null, 0);
        if (mDecoder == null) return false;
        mDecoder.start();
        mInfo = new MediaCodec.BufferInfo();
        mInputBuffers = mDecoder.getInputBuffers();
        mOutputBuffers = mDecoder.getOutputBuffers();
        mIsReady = true;
        return true;
    }

    public void setCallback(Runnable callback) {
        mCallback = callback;
    }

    public int getMaxFrames() {
        return (int) (mDuration * mFPS / 1000000);
    }

    public boolean haveFrame() {
        return mIsReady;
    }

    public MediaCodec getCodec() {
        return mDecoder;
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
                if (mInfo.size > 0) {
                    mOutputBuffer = mDecoder.getOutputBuffer(outIndex);
                    mOutputBuffer.position(mInfo.offset);
                    mOutputBuffer.limit(mInfo.offset + mInfo.size);
                    if(mCallback != null) mCallback.run();
                }
                if (outIndex >= 0) mDecoder.releaseOutputBuffer(outIndex, false);
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
