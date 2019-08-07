package com.slava.noffmpeg.mediaworkers;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import com.slava.noffmpeg.frameproviders.EncodedFrame;
import com.slava.noffmpeg.frameproviders.FrameProvider;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;

public class Encoder {

    public static final int TIMEOUT_US = 10000;
    private int mVideoTrackIndex = -1;
    private MediaMuxer mMuxer = null;
    private int mFramesEncoded = 0;
    private int mFrameRate = 30;
    private FrameProvider mPauseFrame = null;
    private boolean mRequestResume = false;
    private boolean mRequestKeyFrame = false;
    private MediaCodec mEncoder;
    private Surface mSurface;
    private final MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
    ;

    //Нужно запилить паузу. Варианты:
    //1. Рисовать на том же холсте, который используется в MediaProjection, нельзя через Canvas, крашится. Но может быть, можно как-то иначе, хз.
    //2. Писать экрвн в промежуточный канвас, рисовать его на холсте и отправлять в канвас кодека, но это оверхэд
    //3. Использовать 2 кодека со своими холстами и переключать их в микшере - так и сделаю.

    public Encoder(String path, Size size, MediaFormat inputFormat, float bitsPerPixel, int n) {
        if (inputFormat != null && inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            mFrameRate = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (bitsPerPixel * mFrameRate * size.width * size.height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        Log.i("Encoder", "format: " + format);
        try {
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mEncoder = MediaCodec.createEncoderByType("video/avc");
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mEncoder.createInputSurface();
            mEncoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MediaFormat getDefaultFormat(int width, int height, int frameRate, float bitsPerPixel) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (bitsPerPixel * frameRate * width * height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        return format;
    }

    public void writeEncodedData(EncodedFrame frame) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer bb = ByteBuffer.wrap(frame.data);
        info.presentationTimeUs = (mFramesEncoded++) * 1000000 / mFrameRate;
        info.offset = 0;
        info.size = frame.data.length;
        info.flags = 9;
        mMuxer.writeSampleData(mVideoTrackIndex, bb, info);
    }



    public boolean encodeFrame() {
        if (isPaused() && !mRequestResume) {
            writeEncodedData(mPauseFrame.next());
            return false;
        }

        if (mRequestKeyFrame) {
            Bundle param = new Bundle();
            param.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mEncoder.setParameters(param);
            mRequestKeyFrame = false;
        }

        int outIndex = mEncoder.dequeueOutputBuffer(mInfo, TIMEOUT_US);
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_BUFFERS_CHANGED");
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.i("Encoder", "INFO_OUTPUT_FORMAT_CHANGED");
                if (mVideoTrackIndex < 0) {
                    MediaFormat newFormat = mEncoder.getOutputFormat();
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
                if (mRequestResume && (mInfo.flags & BUFFER_FLAG_KEY_FRAME) == 0) {
                    writeEncodedData(mPauseFrame.next());
                } else {
                    if (mRequestResume) {
                        mRequestResume = false;
                        mPauseFrame = null;
                    }
                    if (mInfo.size > 0) {
                        mInfo.presentationTimeUs = (mFramesEncoded++) * 1000000 / mFrameRate;
                        ByteBuffer buffer = mEncoder.getOutputBuffer(outIndex);
                        buffer.position(mInfo.offset);
                        buffer.limit(mInfo.offset + mInfo.size);
                        mMuxer.writeSampleData(mVideoTrackIndex, buffer, mInfo);
                    }
                }
                mEncoder.releaseOutputBuffer(outIndex, false);
                break;
        }

        return (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 || ((mInfo.flags & BUFFER_FLAG_CODEC_CONFIG) != 0);
    }

    public void setPause(FrameProvider pauseFrame) {
        if (mPauseFrame == null) mPauseFrame = pauseFrame;
    }

    public boolean isPaused() {
        return mPauseFrame != null && !mRequestResume;
    }

    public void resume() {
        mRequestResume = true;
        mRequestKeyFrame = true;
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
