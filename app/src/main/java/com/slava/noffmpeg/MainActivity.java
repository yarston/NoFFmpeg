package com.slava.noffmpeg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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
    VideoPictureFileChooser mFileChooser = new VideoPictureFileChooser();
    PauseMaker mPauseMaker = new PauseMaker();

    @BindView(R.id.btn_video) Button mChooseVideo;
    @BindView(R.id.btn_images) Button mChooseImages;
    @BindView(R.id.btn_process) Button mProcess;
    @BindView(R.id.btn_pause) Button mPause;
    @BindView(R.id.progressBar) ProgressBar mProgress;
    @BindView(R.id.textStatus) TextView mStatus;
    @BindView(R.id.seekBar) SeekBar mSeekBar;
    @BindView(R.id.textBpp) TextView mTextBpp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mChooseVideo.setOnClickListener(v -> mFileChooser.chooseVideo(this));
        mChooseImages.setOnClickListener(v -> mFileChooser.chooseImages(this));
        mProcess.setOnClickListener(v -> Executors.newSingleThreadExecutor().submit(this::process));
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
        //select animated gif or static image
        //mPauseMaker.setGif(getResources(), R.raw.giphy);
        mPauseMaker.setImage(getResources(), R.raw.i);
        mPause.setOnClickListener(v -> mPause.setText(mPauseMaker.changeStatus() ? R.string.continue_ : R.string.pause));
    }

    private void process() {
        long startTime = System.currentTimeMillis();
        if(mFileChooser.getVideoPath() == null) return;
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());
        Size size = decoder.getSize();
        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes(), size);
        if(size == null) return;
        Log.v("Decoder", "size = " + size.width + " x " + size.height);
        File f = new File(Environment.getExternalStorageDirectory(), "out.mp4");
        Encoder encoder = new Encoder(f.getPath(), size, decoder.getFormat(), mSeekBar.getProgress() * BPP_STEP);

        Surface surface = encoder.getSurface();
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
            encoder.encodeFrame();
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

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) mFileChooser.processResult(requestCode, intent);
        mStatus.setText(mFileChooser.getStatus());
    }
}
