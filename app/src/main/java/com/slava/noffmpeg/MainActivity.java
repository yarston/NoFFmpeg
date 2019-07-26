package com.slava.noffmpeg;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.slava.noffmpeg.mediaworkers.Decoder;
import com.slava.noffmpeg.mediaworkers.Encoder;
import com.slava.noffmpeg.mediaworkers.Size;
import com.slava.noffmpeg.mediaworkers.VideoProcessor;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.slava.noffmpeg.mediaworkers.VideoProcessor.YUV_420_888_toRGB;

public class MainActivity extends AppCompatActivity {

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord).rbga;\n" +
                    "}\n";

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
        mProcess.setOnClickListener(v -> Executors.newSingleThreadExecutor().submit(this::process));
        mStatus.setText(mFileChooser.getStatus());
    }

    private void process() {
        long startTime = System.currentTimeMillis();
        if(mFileChooser.getVideoPath() == null) return;

        VideoProcessor processor = new VideoProcessor(mFileChooser.getImagePathes());
        Decoder decoder = new Decoder(mFileChooser.getVideoPath());

        File f = new File(Environment.getExternalStorageDirectory(), "out.mp4");

        Size size = decoder.getSize();
        if(size == null) return;
        Log.v("Decoder", "size = " + size.width + " x " + size.height);
        Encoder encoder = new Encoder(f.getPath(), size, decoder.getFormat());

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

            Image img = decoder.getOutputImage();
            if(img != null)  {
                Canvas canvas = surface.lockCanvas(new Rect(0, 0, size.width, size.height));
                //Bitmap bmp = YUV_420_888_toRGB(this, img, size.width, size.height);
                //canvas.drawBitmap(bmp, 0, 0, new Paint());
                img.close();
                surface.unlockCanvasAndPost(canvas);
            } else {
                Canvas canvas = surface.lockCanvas(new Rect(0, 0, size.width, size.height));
                surface.unlockCanvasAndPost(canvas);
            }

            Log.v("Decoder", "frame " + nFrames);
            encoder.encodeFrame();
            mProgress.post(() -> mProgress.setProgress(nFrames.incrementAndGet()));
        });

         for(int i = 0; i < 100; i++)decoder.decodeFrame();
        //while (decoder.haveFrame()) decoder.decodeFrame();
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
