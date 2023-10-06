package com.skythinker.gptassistant;

public abstract class AsrClientBase {
    public interface IAsrCallback {
        void onError(String msg);
        void onResult(String result);
    }
    public abstract void startRecongnize();
    public abstract void stopRecongnize();
    public abstract void cancelRecongnize();
    public abstract void setCallback(IAsrCallback callback);
    public abstract void setParam(String key, Object value);
    public abstract void destroy();
}
