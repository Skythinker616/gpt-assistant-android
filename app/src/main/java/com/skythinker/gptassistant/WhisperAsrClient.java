package com.skythinker.gptassistant;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class WhisperAsrClient extends AsrClientBase{
    MediaRecorder recorder = null;
    File recordFile = null;
    IAsrCallback callback = null;
    WhisperApiClient apiClient = null;
    double amplitude = 0;
    boolean isRecording = false;
    boolean autoStop = false;

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
            isRecording = true;
            if(autoStop) {
                new Thread(() -> {
                    boolean speechDetected = false;
                    final int SILENCE_THRESHOLD = 70;
                    final int SILENCE_AFTER_SPEECH = 3000;
                    long lastSpeechTime = 0;
                    while(isRecording) {
                        try {
                            if(recorder != null) {
                                final double lpfRatio = 0.5;
                                double ratio = recorder.getMaxAmplitude();
                                if(ratio < 1) ratio = 1;
                                double db = 20 * Math.log10(ratio);
                                amplitude = (1 - lpfRatio) * amplitude + lpfRatio * db;
                                Log.d("whisper amp", String.valueOf(amplitude));
                                if(amplitude > SILENCE_THRESHOLD) {
                                    speechDetected = true;
                                    lastSpeechTime = System.currentTimeMillis();
                                }
                                if(speechDetected && amplitude < SILENCE_THRESHOLD && System.currentTimeMillis() - lastSpeechTime > SILENCE_AFTER_SPEECH) {
                                    speechDetected = false;
                                    stopRecognize(true);
                                }
                            }
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            callback.onError(e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopRecognize(boolean callAutoStop) {
        try {
            if(recorder != null && isRecording) {
                isRecording = false;
                recorder.stop();
                recorder.reset();
                recorder.release();
                new Thread(() -> {
                    try{
                        callback.onResult(apiClient.getWhisperResult(recordFile));
                        if(callAutoStop) {
                            callback.onAutoStop();
                        }
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
    public void stopRecognize() {
        stopRecognize(false);
    }

    @Override
    public void cancelRecognize() {
        try {
            if(recorder != null && isRecording) {
                isRecording = false;
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
    public void setEnableAutoStop(boolean enable) {
        autoStop = enable;
    }

    @Override
    public void destroy() { cancelRecognize(); }
}
