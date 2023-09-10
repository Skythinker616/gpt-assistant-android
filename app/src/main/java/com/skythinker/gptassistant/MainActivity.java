package com.skythinker.gptassistant;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;
import com.plexpt.chatgpt.ChatGPT;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.ChatCompletionResponse;
import com.plexpt.chatgpt.entity.chat.Message;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ch.qos.logback.core.helpers.ThrowableToStringArray;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(includeAll = true)
public class MainActivity extends Activity implements EventListener {

    private int selectedTab = 0;
    private TextView tvGptReply;
    private EditText etUserInput;
    private Handler handler = new Handler();
    private Markwon markwon;
    private EventManager asr;
    private long asrStartTime = 0;
    BroadcastReceiver localReceiver = null;

    private static boolean isAlive = false;
    private static boolean isRunning = false;

    private static Context selfContext = null;

    ChatApiClient chatApiClient = null;
    private String chatApiBuffer = "";

    private TextToSpeech tts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selfContext = this;
        GlobalDataHolder.init(this);
        markwon = Markwon.builder(this)
                .usePlugin(SyntaxHighlightPlugin.create(new Prism4j(new GrammarLocatorDef()), Prism4jThemeDefault.create(0)))
                .usePlugin(JLatexMathPlugin.create(40, builder -> builder.inlinesEnabled(true)))
                .usePlugin(TablePlugin.create(this))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build();
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(Locale.getDefault());
                if(res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "不支持当前语言");
                }else{
                    Log.d("TTS", "初始化成功");
                }
            }else{
                Log.e("TTS", "初始化失败");
            }
        });
        setContentView(R.layout.activity_main);
        etUserInput = findViewById(R.id.et_user_input);
        tvGptReply = findViewById(R.id.tv_gpt_reply);
        tvGptReply.setMovementMethod(LinkMovementMethod.getInstance());
        (findViewById(R.id.bt_edit)).setOnClickListener(view -> {
            startActivityForResult(new Intent(MainActivity.this, TabConfActivity.class), 0);
        });
        updateTabListView();
        chatApiClient = new ChatApiClient(GlobalDataHolder.getGptApiHost(),
                GlobalDataHolder.getGptApiKey(),
                new ChatApiClient.OnReceiveListener() {
                    @Override
                    public void onReceive(String message) {
                        chatApiBuffer += message;
                        handler.post(() -> {
                            markwon.setMarkdown(tvGptReply, chatApiBuffer);
                        });
                    }

                    @Override
                    public void onFinished() {
                        if(GlobalDataHolder.getTtsEnable()){
                            handler.post(() -> {
                                tts.speak(tvGptReply.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
                            });
                        }
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            tvGptReply.setText(String.format("获取失败: %s", message));
                        });
                    }
                });

        (findViewById(R.id.bt_send)).setOnClickListener(view -> {
            tts.stop();
            chatApiBuffer = "";
            tvGptReply.setText("正在等待回复...");
            PromptTabData tabData = GlobalDataHolder.getTabDataList().get(selectedTab);
            String userInput = etUserInput.getText().toString();
            chatApiClient.sendPrompt(tabData.getPrompt(), userInput);
        });

        etUserInput.setOnLongClickListener(view -> {
            etUserInput.setText("");
            return true;
        });

//        // 触发软键盘
//        etUserInput.requestFocus();
//        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
//        imm.showSoftInput(findViewById(R.id.et_user_input), InputMethodManager.RESULT_UNCHANGED_SHOWN);

        (findViewById(R.id.view_bg_empty)).setOnClickListener(view -> {
            finish();
        });

        isAlive = true;

        requestPermission();
        asr = EventManagerFactory.create(this, "asr");
        asr.registerListener(this);

        localReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_START")) {
                    tts.stop();
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put(SpeechConstant.APP_ID, GlobalDataHolder.getAsrAppId());
                    params.put(SpeechConstant.APP_KEY, GlobalDataHolder.getAsrApiKey());
                    params.put(SpeechConstant.SECRET, GlobalDataHolder.getAsrSecretKey());
                    asr.send(SpeechConstant.ASR_START, (new JSONObject(params)).toString(), null, 0, 0);
                    asrStartTime = System.currentTimeMillis();
                    etUserInput.setText("");
                    etUserInput.setHint("正在聆听...");
                } else if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_STOP")) {
                    etUserInput.setHint("在此输入问题，长按可清除");
                    if(System.currentTimeMillis() - asrStartTime < 1000) {
                        asr.send(SpeechConstant.ASR_CANCEL, null, null, 0, 0);
                    } else {
                        asr.send(SpeechConstant.ASR_STOP, "{}", null, 0, 0);
                    }
                } else if(action.equals("com.skythinker.gptassistant.KEY_SEND")) {
                    chatApiBuffer = "";
                    tvGptReply.setText("正在等待回复...");
                    PromptTabData tabData = GlobalDataHolder.getTabDataList().get(selectedTab);
                    String userInput = etUserInput.getText().toString();
                    chatApiClient.sendPrompt(tabData.getPrompt(), userInput);
                } else if(action.equals("com.skythinker.gptassistant.SHOW_KEYBOARD")) {
                    etUserInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.showSoftInput(findViewById(R.id.et_user_input), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SPEECH_START");
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SPEECH_STOP");
        intentFilter.addAction("com.skythinker.gptassistant.KEY_SEND");
        intentFilter.addAction("com.skythinker.gptassistant.SHOW_KEYBOARD");
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, intentFilter);

        if(GlobalDataHolder.getCheckAccessOnStart()) {
            if(!MyAccessbilityService.isConnected()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                View view = inflater.inflate(R.layout.ask_accessibility_dialog, null);
                Button btAskAccessOk = view.findViewById(R.id.bt_ask_access_ok);
                Button btAskAccessCancel = view.findViewById(R.id.bt_ask_access_cancel);
                final Dialog dialog = builder.create();
                dialog.show();
                dialog.getWindow().setContentView(view);
                btAskAccessOk.setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                });
                btAskAccessCancel.setOnClickListener(v -> {
                    dialog.dismiss();
                    Toast.makeText(MainActivity.this, "无障碍服务未开启，无法使用音量键控制", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0) {
            updateTabListView();
            chatApiClient.setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
        }
    }

    private void updateTabListView() {
        LinearLayout tabList = findViewById(R.id.tabs_layout);
        tabList.removeAllViews();
        List<PromptTabData> tabDataList = GlobalDataHolder.getTabDataList();
        for (int i = 0; i < tabDataList.size(); i++) {
            PromptTabData tabData = tabDataList.get(i);
            Button tabBtn = new Button(this);
            tabBtn.setText(tabData.getTitle());
            tabBtn.setTextSize(16);
            if(i == selectedTab) {
                tabBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tabBtn.setBackgroundResource(R.drawable.tab_background_selected);
            } else {
                tabBtn.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                tabBtn.setBackgroundResource(R.drawable.tab_background_unselected);
            }
            ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 20, 0);
            tabBtn.setLayoutParams(params);
            int finalI = i;
            tabBtn.setOnClickListener(view -> {
                selectedTab = finalI;
                updateTabListView();
            });
            tabList.addView(tabBtn);
        }
    }

    public static boolean isAlive() {
        return isAlive;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    private void requestPermission() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        ArrayList<String> toApplyList = new ArrayList<String>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
            }
        }
        String tmpList[] = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            requestPermissions(toApplyList.toArray(tmpList), 123);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        Log.d("main activity", "back to main activity");
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        Log.d("main activity", "leave main activity");
    }

    @Override
    protected void onDestroy() {
        isAlive = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver);
        asr.send(SpeechConstant.ASR_CANCEL, "{}", null, 0, 0);
        asr.unregisterListener(this);
        tts.stop();
        tts.shutdown();
        super.onDestroy();
    }

    @Override
    public void onEvent(String name, String params, byte[] data, int offset, int length) {
        if(name.equals(SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL)) {
            try {
                JSONObject json = new JSONObject(params);
                String resultType = json.getString("result_type");
                if(resultType.equals("final_result")) {
                    String bestResult = json.getString("best_result");
                    etUserInput.setText(bestResult);
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
                    tvGptReply.setText(String.format("语音识别出错: %s", errorMessage));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}