package com.slava.noffmpeg.mediaworkers;

public interface ProgressCallback {
    void onChangeProgress(int progress, int max);
}
