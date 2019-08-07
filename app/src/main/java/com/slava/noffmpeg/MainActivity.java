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
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.slava.noffmpeg.frameproviders.FramesProvider;
import com.slava.noffmpeg.frameproviders.GifFramesProvider;
import com.slava.noffmpeg.frameproviders.ImageFramesProvider;
import com.slava.noffmpeg.frameproviders.VideoFramesProvider;
import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.Size;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.btn_video) Button mChooseVideo;
    @BindView(R.id.btn_images) Button mChooseImages;
    @BindView(R.id.btn_process) Button mProcess;
    @BindView(R.id.btn_pause) Button mPause;
    @BindView(R.id.btn_screenrecord) Button mScreenRecord;
    @BindView(R.id.progressBar) ProgressBar mProgress;
    @BindView(R.id.textStatus) TextView mStatus;
    @BindView(R.id.seekBar) SeekBar mSeekBar;
    @BindView(R.id.textBpp) TextView mTextBpp;
    @BindView(R.id.btn_select_res) Spinner mSelect;

    private static final float BPP_STEP = 0.05f;
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private final VideoPictureFileChooser mFileChooser = new VideoPictureFileChooser();
    private Handler mDrainHandler = null;
    private Encoder mScreenEncoder;
    private MediaProjection mMediaProjection;
    private Size mVideoSize = new Size(1280, 720);
    private HandlerThread mRenderThread = new HandlerThread("render_thread");
    private FramesProvider mPauseFramesProvider = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mChooseVideo.setOnClickListener(v -> mFileChooser.chooseVideo(this));
        mChooseImages.setOnClickListener(v -> mFileChooser.chooseImages(this));
        mScreenRecord.setOnClickListener(v -> {
            if (mScreenEncoder == null) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
            } else {
                mDrainHandler.post(() -> {
                    mMediaProjection.stop();
                    mScreenEncoder.release();
                    mScreenEncoder = null;
                    runOnUiThread(() -> mScreenRecord.setText(R.string.screen_record));
                });
            }
        });
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
        mPause.setOnClickListener(v -> {
            if(mScreenEncoder != null) {
                if(mScreenEncoder.isPaused()) mScreenEncoder.resume();
                else if(mPauseFramesProvider != null) mScreenEncoder.setPause(mPauseFramesProvider);
                else Toast.makeText(this, R.string.pause_frames_not_loaded, Toast.LENGTH_SHORT).show();
                mPause.setText(mScreenEncoder.isPaused() ? R.string.continue_ : R.string.pause);
            }
        });

        mSelect.setSelection(0, false);
        mSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mFileChooser.choosePause(MainActivity.this, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mFileChooser.choosePause(MainActivity.this, mSelect.getSelectedItemPosition());
            }
        });

        mRenderThread.start();
        mDrainHandler = new Handler(mRenderThread.getLooper());
        mPauseFramesProvider = new ImageFramesProvider(getResources(), R.raw.i, mVideoSize.width, mVideoSize.height, 1.0f);
    }

    private void processFile2File() {
        long startTime = System.currentTimeMillis();
        if(mFileChooser.getVideoPath() == null) return;
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());
        Size size = decoder.getSize();
        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes(), size);
        Log.v("Decoder", "mVideoSize = " + size.width + " x " + size.height);
        File f = new File(Environment.getExternalStorageDirectory(), "out.mp4");
        mScreenEncoder = new Encoder(f.getPath(), size, decoder.getFormat(), mSeekBar.getProgress() * BPP_STEP, 1);

        Surface surface = mScreenEncoder.getSurface();
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
            //if (!mPauseMaker.process(surface, size))
            processor.process(surface, decoder.getOutputImage());
            Log.v("Decoder", "frame " + nFrames);
            mScreenEncoder.encodeFrame();
            mProgress.post(() -> mProgress.setProgress(nFrames.incrementAndGet()));
        });

         //for(int i = 0; i < 100; i++)decoder.decodeFrame();
        while (decoder.haveFrame()) decoder.decodeFrame();
        decoder.release();
        mScreenEncoder.release();

        runOnUiThread(() -> {
            mStatus.setText(String.format("completed in %.2f sec", (System.currentTimeMillis() - startTime) * 0.001f));
            mChooseVideo.setEnabled(true);
            mChooseImages.setEnabled(true);
            mProcess.setEnabled(true);
            mProgress.setProgress(mProgress.getMax());
        });
    }

    private void drain() {
        mDrainHandler.removeCallbacks(this::drain);
        if (mScreenEncoder == null) return;
        while (mScreenEncoder.encodeFrame());
        mDrainHandler.postDelayed(this::drain, 10);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK)
            if (mFileChooser.processResult(requestCode, intent)) {
                mStatus.setText(mFileChooser.getStatus());
                if(mFileChooser.mIsPauseSelect) {
                    mFileChooser.mIsPauseSelect = false;
                    mPauseFramesProvider = null;
                    String path = mFileChooser.getPausePath();
                    int i = path.lastIndexOf('.');
                    if (i > 0) {
                        switch (path.substring(i + 1)) {
                            case "png":
                            case "jpg":
                            case "jpeg":
                                Executors.newSingleThreadExecutor().submit(()->mPauseFramesProvider = new ImageFramesProvider(path, mVideoSize.width, mVideoSize.height, 1.0f));
                                break;
                            case "gif":
                                Executors.newSingleThreadExecutor().submit(()->mPauseFramesProvider = new GifFramesProvider(path, mVideoSize.width, mVideoSize.height, 1.0f));
                                break;
                            case "mp4":
                                Executors.newSingleThreadExecutor().submit(()->mPauseFramesProvider = new VideoFramesProvider(path, mVideoSize.width, mVideoSize.height, 1.0f));
                                break;
                            default:
                                Toast.makeText(this, R.string.file_not_suitable, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = manager.getMediaProjection(resultCode, intent);
                DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
                Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (defaultDisplay == null) throw new RuntimeException("No display found.");
                mScreenRecord.setText("Стоп");
                mScreenEncoder = new Encoder("/sdcard/video.mp4", mVideoSize, null, mSeekBar.getProgress() * BPP_STEP, 2);
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                mMediaProjection.createVirtualDisplay("Recording Display", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, 0, mScreenEncoder.getSurface(),null, null);
                mDrainHandler.postDelayed(this::drain, 10);
            }
    }
}
