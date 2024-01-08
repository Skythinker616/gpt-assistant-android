package com.skythinker.gptassistant;

import android.content.Context;
import android.media.MediaRecorder;

import java.io.File;

public class WhisperAsrClient extends AsrClientBase{
    MediaRecorder recorder = null;
    File recordFile = null;
    IAsrCallback callback = null;
    WhisperApiClient apiClient = null;

    public WhisperAsrClient(Context context, String url, String apiKey) {
        recordFile = new File(context.getFilesDir().getAbsolutePath() + "/whisper.m4a");
        apiClient = new WhisperApiClient(context, url, apiKey);
    }

    public void setApiInfo(String url, String apiKey) {
        apiClient.setApiInfo(url, apiKey);
    }

    @Override
    public void startRecognize() {
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            callback.onError(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stopRecognize() {
        try {
            if(recorder != null) {
                recorder.stop();
                recorder.reset();
                recorder.release();
                new Thread(() -> {
                    try{
                        callback.onResult(apiClient.getWhisperResult(recordFile));
                    }catch (Exception e) {
                        callback.onError(e.getMessage());
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (Exception e) {
            callback.onError(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void cancelRecognize() {
        try {
            if(recorder != null) {
                recorder.stop();
                recorder.reset();
                recorder.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCallback(IAsrCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setParam(String key, Object value) { }

    @Override
    public void destroy() {
        cancelRecognize();
    }
}
