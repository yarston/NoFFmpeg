package com.slava.noffmpeg;

import android.app.Activity;
import android.content.Intent;

import com.vincent.filepicker.Constant;
import com.vincent.filepicker.activity.ImagePickActivity;
import com.vincent.filepicker.activity.VideoPickActivity;
import com.vincent.filepicker.filter.entity.ImageFile;
import com.vincent.filepicker.filter.entity.VideoFile;

import java.util.ArrayList;
import java.util.List;

class VideoPictureFileChooser {

    private String mVideoFilePath;
    private String mPauseFile;
    private List<String> mImageFilePathes = new ArrayList<>();
    public boolean mIsPauseSelect;

    void chooseVideo(Activity activity) {
        Intent intent = new Intent(activity, VideoPickActivity.class);
        intent.putExtra(Constant.MAX_NUMBER, 2);
        activity.startActivityForResult(intent, Constant.REQUEST_CODE_PICK_VIDEO);
    }

    void chooseImages(Activity activity) {
        Intent intent = new Intent(activity, ImagePickActivity.class);
        intent.putExtra(Constant.MAX_NUMBER, 4);
        activity.startActivityForResult(intent, Constant.REQUEST_CODE_PICK_IMAGE);
    }

    void choosePause(Activity activity, int position) {
        mIsPauseSelect = true;
        if(position == 2)
            activity.startActivityForResult(new Intent(activity, VideoPickActivity.class).putExtra(Constant.MAX_NUMBER, 1), Constant.REQUEST_CODE_PICK_VIDEO);
        else
            activity.startActivityForResult(new Intent(activity, ImagePickActivity.class).putExtra(Constant.MAX_NUMBER, 1), Constant.REQUEST_CODE_PICK_IMAGE);
    }

    boolean processResult(int requestCode, Intent intent) {
        switch (requestCode) {
            case Constant.REQUEST_CODE_PICK_IMAGE:
                mImageFilePathes.clear();
                ArrayList<ImageFile> listImages = intent.getParcelableArrayListExtra(Constant.RESULT_PICK_IMAGE);
                if (listImages != null)
                    if (mIsPauseSelect)
                        mPauseFile = listImages.get(0).getPath();
                    else
                        for (ImageFile f : listImages)
                            mImageFilePathes.add(f.getPath());
                return true;
            case Constant.REQUEST_CODE_PICK_VIDEO:
                ArrayList<VideoFile> listVideos = intent.getParcelableArrayListExtra(Constant.RESULT_PICK_VIDEO);
                if (listVideos != null && !listVideos.isEmpty())
                    if (mIsPauseSelect)
                        mPauseFile = listVideos.get(0).getPath();
                    else {
                        mVideoFilePath = listVideos.get(0).getPath();
                        if(listVideos.size() == 2) mImageFilePathes.add(listVideos.get(1).getPath());
                    }
                return true;
        }
        return false;
    }

    String getStatus() {
        StringBuilder builder = new StringBuilder("selected videos:")
                .append(mVideoFilePath == null ? 0 : 1)
                .append(" mPauseFrames: ").append(mImageFilePathes.size()).append("\n");
        if(mVideoFilePath != null) builder.append(mVideoFilePath).append("\n");
        for(String s : mImageFilePathes) builder.append(s).append("\n");
        return builder.toString();
    }

    String getVideoPath() {
        return mVideoFilePath;
    }

    String getPausePath() {
        return mPauseFile;
    }

    List<String> getImagePathes() {
        return mImageFilePathes;
    }
}

