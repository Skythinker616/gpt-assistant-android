package com.skythinker.gptassistant;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class BaiduAsrClient extends AsrClientBase{
    private EventManager asr = null;
    String asrBuffer = "";
    IAsrCallback callback = null;
    EventListener listener = null;

    public BaiduAsrClient(Context context) {
        asr = EventManagerFactory.create(context, "asr");
        listener = new EventListener() {
            @Override
            public void onEvent(String name, String params, byte[] data, int offset, int length) {
                if(name.equals(SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL)) {
                    Log.d("bd asr partial", params);
                    try {
                        JSONObject json = new JSONObject(params);
                        String resultType = json.getString("result_type");
                        if(resultType.equals("final_result")) {
                            String bestResult = json.getString("best_result");
                            asrBuffer += bestResult;
                            callback.onResult(asrBuffer);
                        }else if(resultType.equals("partial_result")){
                            String bestResult = json.getString("best_result");
                            callback.onResult(String.format("%s%s", asrBuffer, bestResult));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else if(name.equals(SpeechConstant.CALLBACK_EVENT_ASR_FINISH)) {
                    try {
                        JSONObject json = new JSONObject(params);
                        int errorCode = json.getInt("error");
                        if(errorCode != 0) {
                            String errorMessage = json.getString("desc");
                            Log.d("asr error", "error code: " + errorCode + ", error message: " + errorMessage);
                            callback.onError(errorMessage);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        asr.registerListener(listener);
    }

    @Override
    public void startRecongnize() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(SpeechConstant.APP_ID, GlobalDataHolder.getAsrAppId());
        params.put(SpeechConstant.APP_KEY, GlobalDataHolder.getAsrApiKey());
        params.put(SpeechConstant.SECRET, GlobalDataHolder.getAsrSecretKey());
        if(GlobalDataHolder.getAsrUseRealTime()){
            params.put(SpeechConstant.BDS_ASR_ENABLE_LONG_SPEECH, true);
            params.put(SpeechConstant.VAD, SpeechConstant.VAD_DNN);
        }
        else{
            params.put(SpeechConstant.BDS_ASR_ENABLE_LONG_SPEECH, false);
            params.put(SpeechConstant.VAD, SpeechConstant.VAD_TOUCH);
        }
        params.put(SpeechConstant.PID, 15374);
        asr.send(SpeechConstant.ASR_START, (new JSONObject(params)).toString(), null, 0, 0);
        asrBuffer = "";
    }

    @Override
    public void stopRecongnize() {
        asr.send(SpeechConstant.ASR_STOP, "{}", null, 0, 0);
    }

    @Override
    public void cancelRecongnize() {
        asr.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0);
    }

    @Override
    public void setCallback(IAsrCallback callback) {
        this.callback = callback;
    }

    @Override
    public void setParam(String key, Object value) {

    }

    @Override
    public void destroy() {
        cancelRecongnize();
        asr.unregisterListener(listener);
    }
}
