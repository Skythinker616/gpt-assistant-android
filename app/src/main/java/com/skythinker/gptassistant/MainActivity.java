package com.skythinker.gptassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private LinearLayout llChatList;
    private Handler handler = new Handler();
    private Markwon markwon;
    private EventManager asr;
    private long asrStartTime = 0;
    String asrBuffer = "";
    BroadcastReceiver localReceiver = null;

    private static boolean isAlive = false;
    private static boolean isRunning = false;

    ChatApiClient chatApiClient = null;
    private String chatApiBuffer = "";

    private TextToSpeech tts = null;
    private boolean ttsEnabled = true;

    private boolean multiChat = false;
    private List<Pair<ChatApiClient.ChatRole, String>> multiChatList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        tvGptReply = findViewById(R.id.tv_chat_notice);
        tvGptReply.setMovementMethod(LinkMovementMethod.getInstance());
        etUserInput = findViewById(R.id.et_user_input);
        Intent activityIntent = getIntent();
        if(activityIntent != null){
            String action = activityIntent.getAction();
            if(action != null && action.equals("android.intent.action.PROCESS_TEXT")){
                String text = activityIntent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                if(text != null){
                    etUserInput.setText(text);
                }
            }
        }
        llChatList = findViewById(R.id.ll_chat_list);
        (findViewById(R.id.cv_settings)).setOnClickListener(view -> {
            startActivityForResult(new Intent(MainActivity.this, TabConfActivity.class), 0);
        });
        updateTabListView();
        chatApiClient = new ChatApiClient(GlobalDataHolder.getGptApiHost(),
                GlobalDataHolder.getGptApiKey(),
                GlobalDataHolder.getGpt4Enable() ? ChatCompletion.Model.GPT_4_0613.getName() : ChatCompletion.Model.GPT_3_5_TURBO_0613.getName(),
                new ChatApiClient.OnReceiveListener() {
                    @Override
                    public void onReceive(String message) {
                        chatApiBuffer += message;
                        handler.post(() -> {
                            ScrollView scrollView = findViewById(R.id.sv_chat_list);
                            boolean isBottom = scrollView.getChildAt(0).getBottom()
                                    <= scrollView.getHeight() + scrollView.getScrollY();

                            markwon.setMarkdown(tvGptReply, chatApiBuffer);

                            if(isBottom){
                                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                            }
                        });
                    }

                    @Override
                    public void onFinished() {
                        if(ttsEnabled){
                            handler.post(() -> {
                                tts.speak(tvGptReply.getText().toString(), TextToSpeech.QUEUE_FLUSH, null);
                                multiChatList.add(new Pair<>(ChatApiClient.ChatRole.ASSISTANT, chatApiBuffer));
                            });
                        }
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            String errText = String.format("获取失败: %s", message);
                            if(tvGptReply != null){
                                tvGptReply.setText(errText);
                            }else{
                                Toast.makeText(MainActivity.this, errText, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

        (findViewById(R.id.bt_send)).setOnClickListener(view -> {
            tts.stop();
            sendQuestion();
        });

        etUserInput.setOnLongClickListener(view -> {
            etUserInput.setText("");
            return true;
        });

        (findViewById(R.id.cv_multi_chat)).setOnClickListener(view -> {
            multiChat = !multiChat;
            if(multiChat){
                ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn_enabled));
            }else{
                ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn));
            }
        });

        (findViewById(R.id.cv_clear_chat)).setOnClickListener(view -> {
            multiChatList.clear();
            llChatList.removeAllViews();
            TextView tv = new TextView(this);
            tv.setTextColor(Color.parseColor("#000000"));
            tv.setTextSize(16);
            int paddingInPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
            tv.setPadding(paddingInPx, 0, paddingInPx, 0);
            tv.setText("请向GPT提出问题。");
            tvGptReply = tv;
            llChatList.addView(tv);
        });

        (findViewById(R.id.cv_tts_off)).setOnClickListener(view -> {
            ttsEnabled = !ttsEnabled;
            if(ttsEnabled) {
                ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off));
            }else{
                ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off_enable));
                tts.stop();
            }
        });

        (findViewById(R.id.cv_close)).setOnClickListener(view -> {
            finish();
        });

        (findViewById(R.id.view_bg_empty)).setOnClickListener(view -> {
            finish();
        });

        if(GlobalDataHolder.getDefaultEnableMultiChat()){
            multiChat = true;
            ((CardView) findViewById(R.id.cv_multi_chat)).setForeground(getDrawable(R.drawable.chat_btn_enabled));
        }

        if(!GlobalDataHolder.getDefaultEnableTts()){
            ttsEnabled = false;
            ((CardView) findViewById(R.id.cv_tts_off)).setForeground(getDrawable(R.drawable.tts_off_enable));
        }

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
                    asrStartTime = System.currentTimeMillis();
                    asrBuffer = "";
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
                    sendQuestion();
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
            chatApiClient.setModel(GlobalDataHolder.getGpt4Enable() ? ChatCompletion.Model.GPT_4_0613.getName() : ChatCompletion.Model.GPT_3_5_TURBO_0613.getName());
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
                if(selectedTab != finalI){
                    findViewById(R.id.cv_clear_chat).performClick();
                }
                selectedTab = finalI;
                updateTabListView();
            });
            tabList.addView(tabBtn);
        }
    }

    private void sendQuestion(){
        if(!multiChat){
            llChatList.removeAllViews();
            multiChatList.clear();
        }

        if(multiChatList.size() == 0){
            PromptTabData tabData = GlobalDataHolder.getTabDataList().get(selectedTab);
            multiChatList.add(new Pair<>(ChatApiClient.ChatRole.SYSTEM, tabData.getPrompt()));
        }
        String userInput = etUserInput.getText().toString();
        multiChatList.add(new Pair<>(ChatApiClient.ChatRole.USER, userInput));

        if(llChatList.getChildCount() > 0 && llChatList.getChildAt(0) instanceof TextView){
            llChatList.removeViewAt(0);
        }

        if(multiChat && multiChatList.size() > 0 && multiChatList.get(0).first == ChatApiClient.ChatRole.SYSTEM){
            String systemPrompt = multiChatList.get(0).second;
            multiChatList.remove(0);
            String firstUserInput = multiChatList.get(0).second;
            String newUserInput = systemPrompt + " " + firstUserInput;
            multiChatList.set(0, new Pair<>(ChatApiClient.ChatRole.USER, newUserInput));
            if(llChatList.getChildCount() > 0){
                ((TextView) ((LinearLayout) llChatList.getChildAt(0)).getChildAt(1)).setText(newUserInput);
            }
        }

        ViewGroup.MarginLayoutParams iconParams = new ViewGroup.MarginLayoutParams(80, 80);
        iconParams.setMargins(10, 30, 10, 30);

        ViewGroup.MarginLayoutParams contentParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.setMargins(10, 40, 10, 40);

        LinearLayout userLinearLayout = new LinearLayout(this);
        userLinearLayout.setOrientation(LinearLayout.HORIZONTAL);

        ImageView userIcon = new ImageView(this);
        userIcon.setImageResource(R.drawable.chat_user_icon);
        userIcon.setLayoutParams(iconParams);

        TextView userQuestion = new TextView(this);
        userQuestion.setText(multiChatList.get(multiChatList.size() - 1).second);
        userQuestion.setTextSize(16);
        userQuestion.setTextColor(Color.BLACK);
        userQuestion.setLayoutParams(contentParams);
        userQuestion.setMovementMethod(LinkMovementMethod.getInstance());
        userQuestion.setTextIsSelectable(true);

        userLinearLayout.addView(userIcon);
        userLinearLayout.addView(userQuestion);

        LinearLayout gptLinearLayout = new LinearLayout(this);
        gptLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        gptLinearLayout.setBackgroundColor(Color.parseColor("#0A000000"));

        ImageView gptIcon = new ImageView(this);
        gptIcon.setImageResource(R.drawable.chat_gpt_icon);
        gptIcon.setLayoutParams(iconParams);

        TextView gptReply = new TextView(this);
        gptReply.setTextSize(16);
        gptReply.setTextColor(Color.BLACK);
        gptReply.setLayoutParams(contentParams);
        userQuestion.setMovementMethod(LinkMovementMethod.getInstance());
        userQuestion.setTextIsSelectable(true);

        gptLinearLayout.addView(gptIcon);
        gptLinearLayout.addView(gptReply);

        llChatList.addView(userLinearLayout);
        llChatList.addView(gptLinearLayout);

        tvGptReply = gptReply;

        chatApiBuffer = "";
        tvGptReply.setText("正在等待回复...");
        chatApiClient.sendPromptList(multiChatList);
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
            Log.d("asr partial", params);
            try {
                JSONObject json = new JSONObject(params);
                String resultType = json.getString("result_type");
                if(resultType.equals("final_result")) {
                    String bestResult = json.getString("best_result");
                    asrBuffer += bestResult;
                    etUserInput.setText(asrBuffer);
                }else if(resultType.equals("partial_result")){
                    String bestResult = json.getString("best_result");
                    etUserInput.setText(String.format("%s%s", asrBuffer, bestResult));
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
                    if(tvGptReply != null) {
                        tvGptReply.setText(String.format("语音识别出错: %s", errorMessage));
                    }else{
                        Toast.makeText(this, String.format("语音识别出错: %s", errorMessage), Toast.LENGTH_LONG).show();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}