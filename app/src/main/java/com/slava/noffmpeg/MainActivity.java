package com.slava.noffmpeg;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.slava.noffmpeg.frameproviders.FramesProvider;
import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.Size;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.BindView;
import butterknife.ButterKnife;
import lib.folderpicker.FolderPicker;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static com.slava.noffmpeg.VideoPictureFileChooser.DeviceName.Generic;
import static com.slava.noffmpeg.VideoPictureFileChooser.DeviceName.None;
import static com.slava.noffmpeg.mediaworkers.Encoder.selectCodec;
import static com.slava.noffmpeg.mediaworkers.Encoder.selectColorFormat;

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

    private static final float BPP_STEP = 0.00125f;
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int FOLDERPICKER_CODE = 2;
    private static final int PERMISSION_CODE = 3;
    private static final boolean TEST_SEMIPLANAR = false;
    private final VideoPictureFileChooser mFileChooser = new VideoPictureFileChooser(None);
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
        mProcess.setOnClickListener(v -> prepareFile2File());
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
        //mPauseFramesProvider = new ImageFramesProvider(getResources(), R.raw.i, mVideoSize.width, mVideoSize.height, selectColorFormat(selectCodec()), 1.0f, true);
        getPermission();
        //prepareFile2File();
    }

    private void prepareFile2File() {
        if (mFileChooser.mOutFilePath == null) {
            Intent intent = new Intent(this, FolderPicker.class);
            startActivityForResult(intent, FOLDERPICKER_CODE);
        } else Executors.newSingleThreadExecutor().submit(() -> {
            if (!processFile2File(true)) {
                runOnUiThread(() -> Toast.makeText(this, "Файл не может быть обработан", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private boolean processFile2File(boolean useHw) {
        long startTime = System.currentTimeMillis();
        if (mFileChooser.getVideoPath() == null) return false;
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());
        if(!decoder.prepare(null, useHw)) {
            onFail();
        }

        Size size = decoder.getSize();
        Log.v("Decoder", "mVideoSize = " + size.width + " x " + size.height);
        int frameRate = decoder.getFrameRate();
        Log.v("Decoder", "inputFormat = " + decoder.getFormat());
        MediaFormat outputFormat = decoder.getCodec().getOutputFormat();
        Log.v("Decoder", "outputFormat = " + decoder);
        MediaCodecInfo codecInfo = selectCodec();
        int colorFormat = outputFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT) ? outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT) : selectColorFormat(codecInfo);
        Log.v("Decoder", "colorFromat = " + colorFormat);
        Log.v("Decoder", mFileChooser.getStatus());

        mScreenEncoder = new Encoder(mFileChooser.mOutFilePath, size, frameRate, codecInfo, colorFormat, decoder.getRotation(), false, mSeekBar.getProgress() * BPP_STEP);
        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes(), size, colorFormat, decoder.getRotation());

        AtomicInteger nFrames = new AtomicInteger();

        decoder.setImageCallback(image -> {
            Log.v("Decoder", "frame: " + nFrames + " size: " + decoder.mInfo.size + " flags: " + decoder.mInfo.flags);
            processor.process(image);
            mScreenEncoder.writeInputImage(image, decoder.mInfo);
            mScreenEncoder.encodeFrame();
            image.close();
            mProgress.post(() -> mProgress.setProgress(nFrames.incrementAndGet()));
        });

        runOnUiThread(() -> {
            mProgress.setMax(decoder.getMaxFrames());
            mProgress.setProgress(0);
            mStatus.setText("Rendering...");
            mChooseVideo.setEnabled(false);
            mChooseImages.setEnabled(false);
            mProcess.setEnabled(false);
        });

        boolean haveAudio = false;

        try {
            haveAudio = mScreenEncoder.initAudioTrack(mFileChooser.getVideoPath());
        } catch (IOException e) {
            e.printStackTrace();
            haveAudio = false;
            return false;
        }

        //for(int i = 0; i < 100; i++)decoder.decodeFrame();
        while (decoder.haveFrame()) decoder.decodeFrame();

        if(haveAudio) mScreenEncoder.writeAudio();

        try {
            decoder.release();
            mScreenEncoder.release();
        } catch (Exception e) {
            onFail();
            e.printStackTrace();
            return false;
        }

        if (nFrames.get() > 0) {
            runOnUiThread(() -> {
                mStatus.setText(String.format("completed %d frames (%dx%d) in %.2f sec, colorformat=%d",
                        nFrames.get(), size.width, size.height, (System.currentTimeMillis() - startTime) * 0.001f, colorFormat));

                mChooseVideo.setEnabled(true);
                mChooseImages.setEnabled(true);
                mProcess.setEnabled(true);
                mProgress.setProgress(mProgress.getMax());
            });
        } else {
            onFail();
        }
        return nFrames.get() > 0;
    }

    private void onFail() {
        runOnUiThread(() -> {
            mStatus.setText("файл не может быть обработан");
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
        mDrainHandler.postDelayed(this::drain, 33);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK)
            if (mFileChooser.processResult(requestCode, intent)) {
                mStatus.setText(mFileChooser.getStatus());
                if(mFileChooser.mIsPauseSelect) {
                    mFileChooser.mIsPauseSelect = false;
                    mPauseFramesProvider = FramesProvider.fromFile(mFileChooser.getPausePath(), mVideoSize.width, mVideoSize.height, COLOR_FormatSurface, 1.0f, true, 0);
                    if (mPauseFramesProvider == null) Toast.makeText(this, R.string.file_not_suitable, Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
                MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = manager.getMediaProjection(resultCode, intent);
                DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
                Display defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (defaultDisplay == null) throw new RuntimeException("No display found.");
                mScreenRecord.setText("Стоп");
                mScreenEncoder = new Encoder("/sdcard/video.mp4", mVideoSize, 30, mSeekBar.getProgress() * BPP_STEP, true);
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                mMediaProjection.createVirtualDisplay("Recording Display", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, 0, mScreenEncoder.getSurface(),null, null);
                mDrainHandler.postDelayed(this::drain, 10);
            } else if (resultCode == Activity.RESULT_OK) {
                mFileChooser.mOutFilePath = intent.getExtras().getString("data") + "/out2.mp4";
                prepareFile2File();
            }
    }

    private void getPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int canRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            int canWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (canRead != PackageManager.PERMISSION_GRANTED || canWrite != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_CODE);
            }
        }
    }
}
