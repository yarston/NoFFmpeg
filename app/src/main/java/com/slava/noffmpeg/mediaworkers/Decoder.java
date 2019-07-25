package com.slava.noffmpeg.mediaworkers;

public class Decoder {

    private String mVideoPath;

    public Decoder(String videoPath) {
        mVideoPath = videoPath;
    }

    public int[] getSize() {
        return new int[]{0, 0};
    }

    public void prepare(SurfaceCallback callback) {
    }

    public int getMaxFrames() {
        return 0;
    }

    public boolean haveFrame() {
        return false;
    }

    public void decodeFrame() {

    }
}
