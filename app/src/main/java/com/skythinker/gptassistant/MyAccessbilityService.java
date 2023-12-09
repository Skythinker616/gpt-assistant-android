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

    final private int longPressTime = 500; // 长按判定时间
    final private int maxConfirmTime = 3000; // 长按到短按的最长间隔
    final private int banCancelInterval = 2000; // 短按后禁止长按的时间

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
        if(event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) { // 非音量下键不处理
            return super.onKeyEvent(event);
        }

        int eventTime = (int) event.getEventTime(); // 事件发生的时间戳
        int eventAction = event.getAction();

        if(isBaned) { // 当前处于禁用状态（短按后的禁用时间），不拦截事件
            if(eventAction == KeyEvent.ACTION_DOWN) {
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < banCancelInterval) {
                    return super.onKeyEvent(event);
                } else {
                    isBaned = false; // 超出禁用时间，解除禁用
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                keyUpTime = eventTime;
                return super.onKeyEvent(event);
            }
        }

        if(pressCount == 0){ // 当前处于判定周期的第一次按下（标准情况的一次长按+短按为一个判定周期）
            if(eventAction == KeyEvent.ACTION_DOWN) {
                keyDownTime = eventTime;
                isPressing = true;
                handler.postDelayed(() -> { // 等待长按时间后进行长按判定
                    if(isPressing) { // 长按时间后仍然处于按下状态，判定为一次长按
                        if(!MainActivity.isAlive() || !MainActivity.isRunning()) { // 主活动未运行则唤起
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            Log.d("MyAccessbilityService", "startActivity: MainActivity");
                            isInStartDelay = true;
                            handler.postDelayed(() -> { // 等待500ms后发送广播，开始语音识别
                                if(isInStartDelay) {
                                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_START");
                                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_START");
                                }
                            }, 500);
                        } else { // 主活动已在运行， 直接发送广播开始语音识别
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
                if(eventTime - keyDownTime < longPressTime) { // 未达到长按时间就松开，用户只是想调音量，进入禁用状态并弹出音量调节界面
                    isBaned = true;
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    return true;
                } else { // 释放时已经超出了长按时间，表明长按结束
                    pressCount++; // 准备进入判定周期的第二次判定
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SPEECH_STOP"); //发送广播，停止语音识别
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SPEECH_STOP");
                    isWaitingConfirm = true;
                    handler.postDelayed(() -> { // 若超出最长短按等待时间，重置判定周期
                        if(isWaitingConfirm) {
                            pressCount = 0;
                        }
                    }, maxConfirmTime);
                    return true;
                }
            }
        } else if (pressCount == 1) { // 当前处于判定周期的第二次按下
            if(eventAction == KeyEvent.ACTION_DOWN) {
                isWaitingConfirm = false;
                keyDownTime = eventTime;
                if(eventTime - keyUpTime < maxConfirmTime) { // 未超出最长短按等待时间，判定为一次短按，发送广播请求向GPT发送提问
                    Intent broadcastIntent = new Intent("com.skythinker.gptassistant.KEY_SEND");
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                    Log.d("MyAccessbilityService", "broadcast: KEY_SEND");
                    return true;
                } else { // 已超出最长短按等待时间，判定为用户的一次普通点击，不拦截事件
                    return super.onKeyEvent(event);
                }
            } else if(eventAction == KeyEvent.ACTION_UP) {
                if(keyDownTime - keyUpTime < maxConfirmTime) { // 上次按下时被判定为一次成功短按，拦截本次松开事件
                    keyUpTime = eventTime;
                    pressCount = 0;
                    return true;
                } else { // 上次按下时被判定为一次普通点击，不拦截本次松开事件
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