package com.slava.noffmpeg;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.PauseMaker;
import com.slava.noffmpeg.mediaworkers.Size;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    private static final float BPP_STEP = 0.05f;
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private final VideoPictureFileChooser mFileChooser = new VideoPictureFileChooser();
    private Handler mDrainHandler = null;
    private final PauseMaker mPauseMaker = new PauseMaker();

    @BindView(R.id.btn_video) Button mChooseVideo;
    @BindView(R.id.btn_images) Button mChooseImages;
    @BindView(R.id.btn_process) Button mProcess;
    @BindView(R.id.btn_pause) Button mPause;
    @BindView(R.id.btn_screenrecord) Button mScreenRecord;
    @BindView(R.id.progressBar) ProgressBar mProgress;
    @BindView(R.id.textStatus) TextView mStatus;
    @BindView(R.id.seekBar) SeekBar mSeekBar;
    @BindView(R.id.textBpp) TextView mTextBpp;
    @BindView(R.id.switch1) Switch mSwitch;
    private Encoder mScreenEncoder;
    private MediaProjection mMediaProjection;
    Size size = new Size(1280, 720);
    private boolean isRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mChooseVideo.setOnClickListener(v -> mFileChooser.chooseVideo(this));
        mChooseImages.setOnClickListener(v -> mFileChooser.chooseImages(this));
        mScreenRecord.setOnClickListener(v -> checkScreenRecordPermission());
        mProcess.setOnClickListener(v -> Executors.newSingleThreadExecutor().submit(this::processFile2File));
        mTextBpp.setText(getString(R.string.bpp, mSeekBar.getProgress() * BPP_STEP));
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTextBpp.setText(getString(R.string.bpp, progress * BPP_STEP));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mStatus.setText(mFileChooser.getStatus());
        mPauseMaker.setImage(getResources(), R.raw.i);
        mPause.setOnClickListener(v -> mPause.setText(mPauseMaker.changeStatus() ? R.string.continue_ : R.string.pause));
        mSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) mPauseMaker.setGif(getResources(), R.raw.giphy);
            else mPauseMaker.setImage(getResources(), R.raw.i);
        });
    }

    private void processFile2File() {
        long startTime = System.currentTimeMillis();
        if(mFileChooser.getVideoPath() == null) return;
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());
        Size size = decoder.getSize();
        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes(), size);
        if(size == null) return;
        Log.v("Decoder", "size = " + size.width + " x " + size.height);
        File f = new File(Environment.getExternalStorageDirectory(), "out.mp4");
        Encoder encoder = new Encoder(f.getPath(), size, decoder.getFormat(), mSeekBar.getProgress() * BPP_STEP, 1);

        Surface surface = encoder.getSurface(0);
        if(surface == null) return;
        AtomicInteger nFrames = new AtomicInteger();
        runOnUiThread(() -> {
            mProgress.setMax(decoder.getMaxFrames());
            mProgress.setProgress(0);
            mStatus.setText("Rendering...");
            mChooseVideo.setEnabled(false);
            mChooseImages.setEnabled(false);
            mProcess.setEnabled(false);
        });

        decoder.prepare(null, () -> {
            if (!mPauseMaker.process(surface, size))
                processor.process(surface, decoder.getOutputImage());
            Log.v("Decoder", "frame " + nFrames);
            encoder.encodeFrame(0);
            mProgress.post(() -> mProgress.setProgress(nFrames.incrementAndGet()));
        });

         //for(int i = 0; i < 100; i++)decoder.decodeFrame();
        while (decoder.haveFrame()) decoder.decodeFrame();
        decoder.release();
        encoder.release();

        runOnUiThread(() -> {
            mStatus.setText(String.format("completed in %.2f sec", (System.currentTimeMillis() - startTime) * 0.001f));
            mChooseVideo.setEnabled(true);
            mChooseImages.setEnabled(true);
            mProcess.setEnabled(true);
            mProgress.setProgress(mProgress.getMax());
        });
    }

    private void checkScreenRecordPermission() {
        if(mScreenEncoder != null) {
            isRunning = false;
        } else {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        }
    }

    private void startRecording() {
        DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (defaultDisplay == null) throw new RuntimeException("No display found.");
        mScreenRecord.setText("Стоп");
        mScreenEncoder = new Encoder("/sdcard/video.mp4", size, null, mSeekBar.getProgress() * BPP_STEP, 2);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mMediaProjection.createVirtualDisplay("Recording Display", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, 0, mScreenEncoder.getSurface(0),null, null);

        isRunning = true;
        Executors.newSingleThreadExecutor().submit(()->{
            if(mDrainHandler == null) {
                Looper.prepare();
                Looper looper;
                synchronized (this) {
                    looper = Looper.myLooper();
                    notifyAll();
                }
                mDrainHandler = new Handler(looper);
                mDrainHandler.postDelayed(this::drain, 10);
                Looper.loop();
            }
        });
    }

    private void drain() {
        if(mDrainHandler == null) return;
        mDrainHandler.removeCallbacks(this::drain);
        if(isRunning) {
            if (mScreenEncoder == null) return;
            while (mScreenEncoder != null && mScreenEncoder.encodeFrame(mPauseMaker.process(mScreenEncoder.getSurface(1), size) ? 1 : 0));
            mDrainHandler.postDelayed(this::drain, 10);
        } else {
            releaseEncoders();
        }
    }

    private void releaseEncoders() {
        if(mMediaProjection != null) mMediaProjection.stop();
        mMediaProjection = null;
        mScreenEncoder.release();
        mScreenEncoder = null;
        mScreenRecord.setText(R.string.screen_record);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK)
            if (mFileChooser.processResult(requestCode, intent))
                mStatus.setText(mFileChooser.getStatus());
            else if (requestCode == REQUEST_MEDIA_PROJECTION) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = manager.getMediaProjection(resultCode, intent);
                startRecording();
            }
    }
}
