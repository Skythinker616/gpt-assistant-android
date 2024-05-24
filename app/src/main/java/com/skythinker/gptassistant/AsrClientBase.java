package com.skythinker.gptassistant;

public abstract class AsrClientBase {
    public interface IAsrCallback {
        void onError(String msg);
        void onResult(String result);
        void onAutoStop();
    }
    public abstract void startRecognize();
    public abstract void stopRecognize();
    public abstract void cancelRecognize();
    public abstract void setCallback(IAsrCallback callback);
    public abstract void setParam(String key, Object value);
    public abstract void setEnableAutoStop(boolean enable);
    public abstract void destroy();
}
