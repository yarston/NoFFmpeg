package com.slava.noffmpeg.mediaworkers;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.slava.noffmpeg.frameproviders.EncodedFrame;
import com.slava.noffmpeg.frameproviders.FramesProvider;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;
import static com.slava.noffmpeg.mediaworkers.VideoProcessor.copyPicture;

public class Encoder {

    public static final int TIMEOUT_US = 10000;
    private final int mWidth;
    private final int mHeight;
    private int mVideoTrackIndex = -1;
    private MediaMuxer mMuxer = null;
    private long mFramesEncoded = 0;
    private int mFrameRate;
    private FramesProvider mPauseFrame = null;
    private boolean mRequestResume = false;
    private boolean mRequestKeyFrame = false;
    private boolean mIsFirstPauseFrame = false;
    private MediaCodec mEncoder;
    private Surface mSurface;
    private final MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
    private static final boolean DEBUG = true;
    private int mInIndex;


    //Нужно запилить паузу. Варианты:
    //1. Рисовать на том же холсте, который используется в MediaProjection, нельзя через Canvas, крашится. Но может быть, можно как-то иначе, хз.
    //2. Писать экрвн в промежуточный канвас, рисовать его на холсте и отправлять в канвас кодека, но это оверхэд
    //3. Использовать 2 кодека со своими холстами и переключать их в микшере - так и сделаю.

    public Encoder(String path, Size size, int frameRate, @NonNull MediaCodecInfo codecInfo, int colorFormat, float bitsPerPixel, boolean withSurface) {
        mFrameRate = frameRate;
        mWidth = size.width;
        mHeight = size.height;
        try {
            MediaFormat format = getDefaultFormat(size.width, size.height, mFrameRate, colorFormat, (int) (bitsPerPixel * mFrameRate * size.width * size.height));
            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //mEncoder = MediaCodec.createEncoderByType("video/avc");
            mEncoder = MediaCodec.createByCodecName(codecInfo.getName());
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if(withSurface) mSurface = mEncoder.createInputSurface();
            mEncoder.start();
            if(DEBUG) Log.d("Encoder", "encoder strarted");
        } catch (IOException e) {
            if(DEBUG) e.printStackTrace();
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    public static MediaCodecInfo selectCodec() {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) continue;

            for (String type : codecInfo.getSupportedTypes())
                if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC))
                    return codecInfo;
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    public static int selectColorFormat(MediaCodecInfo codecInfo) {
        for(int colorFormat : codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats)
            if (isRecognizedFormat(colorFormat)) return colorFormat;
        if(DEBUG) Log.e("Encoder", "couldn't find a good color format for " + codecInfo.getName() + " / " + MediaFormat.MIMETYPE_VIDEO_AVC);
        return 0;   // not reached
        //return 21;
    }

    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    public static MediaFormat getDefaultFormat(int width, int height, int frameRate, int colorFormat, float bitsPerPixel) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (bitsPerPixel * frameRate * width * height));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 3 / 2);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        return format;
    }

    public void writeInputImage(Image dstImage, MediaCodec.BufferInfo info) {
        int inIndex = mEncoder.dequeueInputBuffer(TIMEOUT_US);
        if (inIndex < 0) return;
        ByteBuffer buffer = mEncoder.getInputBuffer(inIndex);
        if(buffer == null) return;
        int rem = buffer.remaining();
        Image srcImage = mEncoder.getInputImage(inIndex);

        Image.Plane[] inPlanes = dstImage.getPlanes();
        if(inPlanes.length < 3) return;
        Image.Plane[] outPlanes = srcImage.getPlanes();
        if(outPlanes.length < 3) return;

        Image.Plane pi0 = inPlanes[0];
        Image.Plane pi1 = inPlanes[1];
        Image.Plane pi2 = inPlanes[2];
        Image.Plane po0 = outPlanes[0];
        Image.Plane po1 = outPlanes[1];
        Image.Plane po2 = outPlanes[2];

        copyPicture(mWidth, mHeight,
                pi0.getBuffer(), pi0.getRowStride(), pi0.getPixelStride(),
                pi1.getBuffer(), pi1.getRowStride(), pi1.getPixelStride(),
                pi2.getBuffer(), pi2.getRowStride(), pi2.getPixelStride(),
                po0.getBuffer(), po0.getRowStride(), po0.getPixelStride(),
                po1.getBuffer(), po1.getRowStride(), po1.getPixelStride(),
                po2.getBuffer(), po2.getRowStride(), po2.getPixelStride());

        mEncoder.queueInputBuffer(inIndex, 0, rem, info.presentationTimeUs, info.flags);
    }

    public void writeBuffer(ByteBuffer inBuffer, MediaCodec.BufferInfo info) {
        int inIndex = mEncoder.dequeueInputBuffer(TIMEOUT_US);
        if (inIndex < 0) return;
        ByteBuffer buffer = mEncoder.getInputBuffer(inIndex);
        buffer.put(inBuffer);
        mEncoder.queueInputBuffer(inIndex, 0, info.size, info.presentationTimeUs, info.flags);
    }

    public void writeEncodedData(EncodedFrame frame) {
        mFramesEncoded++;
        if(frame == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.presentationTimeUs = (mFramesEncoded) * 1000000 / mFrameRate;
        info.offset = 0;
        info.size = frame.data.capacity();
        mMuxer.writeSampleData(mVideoTrackIndex, frame.data, info);
    }

    public boolean encodeFrame() {
        if (isPaused() && !mRequestResume) {
            writeEncodedData(mIsFirstPauseFrame ? mPauseFrame.first() : mPauseFrame.next());
            mIsFirstPauseFrame = false;
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
                //Log.i("Encoder", "INFO_OUTPUT_BUFFERS_CHANGED");
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                //Log.i("Encoder", "INFO_OUTPUT_FORMAT_CHANGED");
                if (mVideoTrackIndex < 0) {
                    MediaFormat newFormat = mEncoder.getOutputFormat();
                    mVideoTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                }
                break;
            case INFO_TRY_AGAIN_LATER:
                //Log.i("Encoder", "INFO_TRY_AGAIN_LATER");
                return (mInfo.flags & BUFFER_FLAG_CODEC_CONFIG) != 0;
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
                        //Log.v("Encoder", "write bytes:" + mInfo.size);
                    }
                }
                mEncoder.releaseOutputBuffer(outIndex, false);
                break;
        }

        return (mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0;
    }

    public void setPause(FramesProvider pauseFrame) {
        if (mPauseFrame == null) {
            mPauseFrame = pauseFrame;
            mIsFirstPauseFrame = true;
        }
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
        try {
            mEncoder.signalEndOfInputStream();
        } catch (Exception ignored) {
        }
        mEncoder.stop();
        mEncoder.release();
        mMuxer.stop();
        mMuxer.release();
        if(mSurface != null) mSurface.release();
        mEncoder = null;
        mSurface = null;
    }
}
