package com.slava.noffmpeg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    VideoPictureFileChooser mFileChooser = new VideoPictureFileChooser();

    @BindView(R.id.btn_video) Button mChooseVideo;
    @BindView(R.id.btn_images) Button mChooseImages;
    @BindView(R.id.btn_process) Button mProcess;
    @BindView(R.id.progressBar) ProgressBar mProgress;
    @BindView(R.id.textStatus) TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mChooseVideo.setOnClickListener(v -> mFileChooser.chooseVideo(this));
        mChooseImages.setOnClickListener(v -> mFileChooser.chooseImages(this));
        mProcess.setOnClickListener(v -> new Thread(this::process).run());
        mStatus.setText(mFileChooser.getStatus());
    }

    private void process() {
        if(mFileChooser.getVideoPath() == null) return;
        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes());
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());
        Encoder encoder = new Encoder("out.mp4", decoder.getSize());
        AtomicInteger nFrames = new AtomicInteger();
        runOnUiThread(() -> {
            mProgress.setMax(decoder.getMaxFrames());
            mProgress.setProgress(0);
            mStatus.setText("Rendering...");
            mChooseVideo.setEnabled(false);
            mChooseImages.setEnabled(false);
            mProcess.setEnabled(false);
        });
        decoder.prepare(s -> {
            processor.process(s);
            mProgress.post(() -> mProgress.setProgress(nFrames.incrementAndGet()));
            encoder.commit();
        });
        while (decoder.haveFrame()) decoder.decodeFrame();
        runOnUiThread(() -> {
            mStatus.setText("finish");
            mChooseVideo.setEnabled(true);
            mChooseImages.setEnabled(true);
            mProcess.setEnabled(true);
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) mFileChooser.processResult(requestCode, intent);
        mStatus.setText(mFileChooser.getStatus());
    }
}
