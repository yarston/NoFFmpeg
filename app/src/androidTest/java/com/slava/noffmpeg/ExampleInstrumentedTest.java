package com.slava.noffmpeg;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.Size;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.slava.noffmpeg.mediaworkers.Encoder.selectCodec;
import static com.slava.noffmpeg.mediaworkers.Encoder.selectColorFormat;
import static org.junit.Assert.assertEquals;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        assertEquals("com.slava.noffmpeg", appContext.getPackageName());
    }

    @Test
    public void decode1() throws Exception {
        android.util.Log.d("Test", "Blabla");
        System.out.println("E " + "Test" + ": " + "Blabla");
        android.util.Log.d("Test", "decode1");
        ImageReaderDecoderTest test = new ImageReaderDecoderTest();
        test.setUp();
        test.videoDecodeToSurface("/sdcard/RenderTest/VID_20190617_114140.mp4", 1920, 1080, 19, false);
    }

    @Test
    public void decodeVideo() {
       // Decoder decoder = new Decoder("/storage/sdcard0/RenderTest/VID_20190617_114140.mp4");
        System.out.println(Environment.getDataDirectory() + "/RenderTest/VID_20190617_114140.mp4");
        Decoder decoder = new Decoder("/sdcard/RenderTest/VID_20190617_114140.mp4");
        android.util.Log.d("Test", "Blabla");
        System.out.println("E " + "Test" + ": " + "Blabla");

        decoder.prepare(null);
        Size size = decoder.getSize();
        assertEquals(size.width, 1920);
        assertEquals(size.height, 1080);
       // File f = new File("/storage/sdcard0/out_test.mp4");
        File f = new File("/sdcard/out_test.mp4");

        int frameRate = decoder.getFrameRate();
        assertEquals(frameRate, 30);
        MediaFormat outputFormat = decoder.getCodec().getOutputFormat();
        MediaCodecInfo codecInfo = selectCodec();
        int colorFormat = outputFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT) ? outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT) : selectColorFormat(codecInfo);
        Log.v("Decoder", "colorFromat = " + colorFormat);
        Encoder mScreenEncoder = new Encoder(f.getPath(), size, frameRate, codecInfo, colorFormat, decoder.getRotation(), false, 60 * 0.00125f);
        VideoProcessor processor = new VideoProcessor(new ArrayList<>(), size, colorFormat, decoder.getRotation());

        AtomicInteger nFrames = new AtomicInteger();

        decoder.setImageCallback(image -> {
            processor.process(image);
            mScreenEncoder.writeInputImage(image, decoder.mInfo);
            mScreenEncoder.encodeFrame();
            image.close();
            nFrames.incrementAndGet();
        });
        while (decoder.haveFrame()) decoder.decodeFrame();
        try {
            decoder.release();
            mScreenEncoder.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals("frames count:", 317, nFrames.get());
    }

    @Test
    public void ReadAudio() throws IOException {
        MediaExtractor mExtractor = new MediaExtractor();
        int mSampleRate = 0;
        int mChannelsCount = 0;
        mExtractor.setDataSource("/sdcard/RenderTest/VID_20190617_114140.mp4");
        String seachMime = "audio/";
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(seachMime)) {
                mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mChannelsCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                mExtractor.selectTrack(i);
                break;
            }
        }

        int nRiddenSamples = 0;
        while (true) {
            ByteBuffer buffer = ByteBuffer.allocate(16384);
            int sampleSize = mExtractor.readSampleData(buffer, 0);
            if (!mExtractor.advance() || sampleSize <= 0) {
               break;
            } else nRiddenSamples++;
        }
        Log.v("Decoder", "nRiddenSamples:" + nRiddenSamples);
    }

    @Test
    public void ReadWriteAudio() throws IOException {
        MediaMuxer mMuxer = new MediaMuxer("/sdcard/RenderTest/audio_out.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaExtractor extractor = new MediaExtractor();

        int dstIndex = -1;
        extractor.setDataSource("/sdcard/RenderTest/VID_20190617_114140.mp4");
        String seachMime = "audio/";
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(seachMime)) {
                extractor.selectTrack(i);
                dstIndex = mMuxer.addTrack(format);
                break;
            }
        }

        mMuxer.start();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer buffer = ByteBuffer.allocate(16384);

        int nRiddenSamples = 0;
        while (true) {
            buffer.clear();
            int sampleSize = extractor.readSampleData(buffer, 0);
            int flags = extractor.getSampleFlags();
            if (sampleSize <= 0 || flags < 0) break;

            nRiddenSamples++;
            bufferInfo.offset = 0;
            bufferInfo.size = sampleSize;
            bufferInfo.presentationTimeUs = extractor.getSampleTime();
            bufferInfo.flags = flags;
            buffer.position(0);
            buffer.limit(sampleSize);

            Log.d("Encoder", "Frame (" + nRiddenSamples + ") " +
                    "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                    " Flags:" + bufferInfo.flags +
                    " TrackIndex:" + extractor.getSampleTrackIndex() +
                    " Size(b) " + bufferInfo.size);

            mMuxer.writeSampleData(dstIndex, buffer, bufferInfo);
            if (!extractor.advance()) break;
        }
        Log.v("Decoder", "nRiddenSamples:" + nRiddenSamples);
        mMuxer.release();
    }

}
