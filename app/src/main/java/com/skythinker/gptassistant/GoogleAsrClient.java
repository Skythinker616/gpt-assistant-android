package com.skythinker.gptassistant;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class GoogleAsrClient extends AsrClientBase {
    SpeechRecognizer speechRecognizer = null;
    IAsrCallback callback = null;
    Context context = null;
    boolean autoStop = false;

    public GoogleAsrClient(Context context) {
        this.context = context;

        if(!SpeechRecognizer.isRecognitionAvailable(context.getApplicationContext()))
            return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { }

            @Override
            public void onBeginningOfSpeech() { }

            @Override
            public void onRmsChanged(float rmsdB) { }

            @Override
            public void onBufferReceived(byte[] buffer) { }

            @Override
            public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                String errorStr = "Code=" + error;
                if(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                    errorStr = context.getString(R.string.text_google_asr_permission_error);
                callback.onError(errorStr);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(data != null && !data.isEmpty()) {
                    Log.d("GoogleAsr", "onResults: " + data.get(0));
                    callback.onResult(data.get(0));
                    if(autoStop)
                        callback.onAutoStop();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> data = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(data != null && !data.isEmpty()) {
                    Log.d("GoogleAsr", "onPartialResults: " + data.get(0));
                    callback.onResult(data.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) { }
        });
    }

    @Override
    public void startRecognize() {
        if(speechRecognizer != null) {
            Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            try {
                speechRecognizer.startListening(speechRecognizerIntent);
            } catch (SecurityException e) {
                callback.onError(context.getString(R.string.text_google_asr_typing_error));
                new ConfirmDialog(context)
                        .setContent(context.getString(R.string.text_google_asr_typing_error))
                        .setOnConfirmListener(() -> {
                            context.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        }).show();
                e.printStackTrace();
            }
        } else {
            callback.onError(context.getString(R.string.text_google_asr_unavailable));
        }
    }

    @Override
    public void stopRecognize() {
        if(speechRecognizer != null) {
            speechRecognizer.cancel();
        }
    }

    @Override
    public void cancelRecognize() {
        if(speechRecognizer != null) {
            speechRecognizer.cancel();
        }
    }

    @Override
    public void setCallback(IAsrCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setParam(String key, Object value) {

    }

    @Override
    public void setEnableAutoStop(boolean enable) {
        autoStop = enable;
    }

    @Override
    public void destroy() {
        if(speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
