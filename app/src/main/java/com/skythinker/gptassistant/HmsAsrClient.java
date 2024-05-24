package com.skythinker.gptassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.huawei.hms.mlsdk.asr.MLAsrConstants;
import com.huawei.hms.mlsdk.asr.MLAsrListener;
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer;
import com.huawei.hms.mlsdk.common.MLApplication;

public class HmsAsrClient extends AsrClientBase{

    Context context = null;
    MLAsrRecognizer hwAsrRecognizer = null;
    IAsrCallback callback = null;
    boolean autoStop = false;

    public HmsAsrClient(Context context) {
        this.context = context;
        MLApplication.getInstance().setApiKey(context.getString(R.string.hms_api_key));
        hwAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context);
        hwAsrRecognizer.setAsrListener(new MLAsrListener() { // 设置HMS识别回调
            @Override
            public void onResults(Bundle bundle) { // 识别完成
                Log.d("hwAsr", "onResults: " + bundle.getString("results_recognizing"));
                String result = bundle.getString("results_recognizing");
                callback.onResult(result);
                if(autoStop && result == null)
                    callback.onAutoStop();
            }

            @Override
            public void onRecognizingResults(Bundle bundle) { // 部分识别结果
                Log.d("hwAsr", "onRecognizingResults: " + bundle.getString("results_recognizing"));
                callback.onResult(bundle.getString("results_recognizing"));
            }

            @Override
            public void onError(int code, String msg) { // 识别错误
                msg = "code=" + code + "  " + msg ;
                Log.d("hwAsr", "onError: " +  msg);
                if(code == 11203 || code == 11219)
                    msg += context.getString(R.string.text_hms_asr_failed_error);
                callback.onError(msg);
            }

            @Override
            public void onStartListening() { }

            @Override
            public void onStartingOfSpeech() { }

            @Override
            public void onVoiceDataReceived(byte[] bytes, float v, Bundle bundle) { }

            @Override
            public void onState(int i, Bundle bundle) { }
        });
    }

    @Override
    public void startRecognize() {
        if(Build.CPU_ABI.contains("x86")) {
            callback.onError(context.getString(R.string.text_hms_asr_unsupported));
            return;
        }
        Intent hwAsrIntent = new Intent(MLAsrConstants.ACTION_HMS_ASR_SPEECH);
        hwAsrIntent.putExtra(MLAsrConstants.LANGUAGE, "zh-CN");
        hwAsrIntent.putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_WORDFLUX);
        hwAsrIntent.putExtra(MLAsrConstants.VAD_START_MUTE_DURATION, 60000);
        if(autoStop)
            hwAsrIntent.putExtra(MLAsrConstants.VAD_END_MUTE_DURATION, 3000);
        else
            hwAsrIntent.putExtra(MLAsrConstants.VAD_END_MUTE_DURATION, 60000);
        hwAsrIntent.putExtra(MLAsrConstants.PUNCTUATION_ENABLE, true);
        hwAsrRecognizer.startRecognizing(hwAsrIntent);
    }

    @Override
    public void stopRecognize() {
        hwAsrRecognizer.destroy();
    }

    @Override
    public void cancelRecognize() {
        hwAsrRecognizer.destroy();
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
    public void destroy() {
        hwAsrRecognizer.destroy();
    }
}
