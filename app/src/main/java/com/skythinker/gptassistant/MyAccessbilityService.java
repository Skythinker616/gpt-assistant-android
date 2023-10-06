package com.skythinker.gptassistant;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

public class MyAccessbilityService extends AccessibilityService {

    private static boolean isConnected = false;
    private Handler handler = new Handler();
    private int keyDownTime = 0, keyUpTime = 0;
    private boolean isWaitingConfirm = false;
    private int pressCount = 0;
    private boolean isPressing = false;
    private boolean isBaned = false;
    private boolean isInStartDelay = false;
    AudioManager audioManager;
    Vibrator vibrator;

    final private int longPressTime = 500, maxConfirmTime = 3000, banCancelInterval = 2000;

    public MyAccessbilityService() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d("MyAccessbilityService", "onKeyEvent: " + event.toString());
        if(event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event);
        }

        int eventTime = (int) event.getEventTime();
        int eventAction = event.getAction();

        if(isBaned) {
            if(eventAction == KeyEvent.ACTION_DOWN) {
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < banCancelInterval) {
                    return super.onKeyEvent(event);
                } else {
                    isBaned = false;
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                keyUpTime = eventTime;
                return super.onKeyEvent(event);
            }
        }

        if(pressCount == 0){
            if(eventAction == KeyEvent.ACTION_DOWN) {
                keyDownTime = eventTime;
                isPressing = true;
                handler.postDelayed(() -> {
                    if(isPressing) {
                        if(!MainActivity.isAlive() || !MainActivity.isRunning()) {
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            Log.d("MyAccessbilityService", "startActivity: MainActivity");
                            isInStartDelay = true;
                            handler.postDelayed(() -> {
                                if(isInStartDelay) {
                                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_START");
                                }
                            }, 500);
                        } else {
                            Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                            Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_START");
                        }
                        vibrator.vibrate(100);
                    }
                }, longPressTime);
                return true;
            } else if(eventAction == KeyEvent.ACTION_UP) {
                keyUpTime = eventTime;
                isPressing = false;
                isInStartDelay = false;
                if(eventTime - keyDownTime < longPressTime) {
                    isBaned = true;
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return true;
                } else {
                    pressCount++;
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_STOP");
                    isWaitingConfirm = true;
                    handler.postDelayed(() -> {
                        if(isWaitingConfirm) {
                            pressCount = 0;
                        }
                    }, maxConfirmTime);
                    return true;
                }
            }
        } else if (pressCount == 1) {
            if(eventAction == KeyEvent.ACTION_DOWN) {
                isWaitingConfirm = false;
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < maxConfirmTime) {
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SEND");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SEND");
                    return true;
                } else {
                    return super.onKeyEvent(event);
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                if(keyDownTime - keyUpTime < maxConfirmTime) {
                    keyUpTime = eventTime;
                    pressCount = 0;
                    return true;
                } else {
                    keyUpTime = eventTime;
                    pressCount = 0;
                    return super.onKeyEvent(event);
                }
            }
        }

        return super.onKeyEvent(event);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isConnected = true;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        vibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
    }

    public static boolean isConnected() {
        return isConnected;
    }
}