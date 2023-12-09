package com.skythinker.gptassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.huawei.hms.mlsdk.asr.MLAsrConstants;
import com.huawei.hms.mlsdk.asr.MLAsrListener;
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer;
import com.huawei.hms.mlsdk.common.MLApplication;

public class HmsAsrClient extends AsrClientBase{

    MLAsrRecognizer hwAsrRecognizer = null;
    IAsrCallback callback = null;

    public HmsAsrClient(Context context) {
        MLApplication.getInstance().setApiKey(context.getString(R.string.hms_api_key));
        hwAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context);
        hwAsrRecognizer.setAsrListener(new MLAsrListener() { // 设置HMS识别回调
            @Override
            public void onResults(Bundle bundle) { // 识别完成
                Log.d("hwAsr", "onResults: " + bundle.getString("results_recognizing"));
                callback.onResult(bundle.getString("results_recognizing"));
            }

            @Override
            public void onRecognizingResults(Bundle bundle) { // 部分识别结果
//                Log.d("hwAsr", "onRecognizingResults: " + bundle.getString("results_recognizing"));
                callback.onResult(bundle.getString("results_recognizing"));
            }

            @Override
            public void onError(int code, String msg) { // 识别错误
                msg = "code=" + code + "  " + msg ;
                Log.d("hwAsr", "onError: " +  msg);
                if(code == 11203 || code == 11219)
                    msg += "\n可能由于作者关闭了免费HMS语音服务，或在新版本中更新了参数，若该错误持续发生，请尝试更新版本或改为使用百度语音接口";
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
    public void startRecongnize() {
        Intent hwAsrIntent = new Intent(MLAsrConstants.ACTION_HMS_ASR_SPEECH);
        hwAsrIntent.putExtra(MLAsrConstants.LANGUAGE, "zh-CN");
        hwAsrIntent.putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_WORDFLUX);
        hwAsrIntent.putExtra(MLAsrConstants.VAD_START_MUTE_DURATION, 60000);
        hwAsrIntent.putExtra(MLAsrConstants.VAD_END_MUTE_DURATION, 60000);
        hwAsrIntent.putExtra(MLAsrConstants.PUNCTUATION_ENABLE, true);
        hwAsrRecognizer.startRecognizing(hwAsrIntent);
    }

    @Override
    public void stopRecongnize() {
        hwAsrRecognizer.destroy();
    }

    @Override
    public void cancelRecongnize() {
        hwAsrRecognizer.destroy();
    }

    @Override
    public void setCallback(IAsrCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setParam(String key, Object value) { }

    @Override
    public void destroy() {
        hwAsrRecognizer.destroy();
    }
}
