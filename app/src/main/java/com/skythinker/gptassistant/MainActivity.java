package com.skythinker.gptassistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;

import com.skythinker.gptassistant.ChatApiClient.ChatRole;
import com.skythinker.gptassistant.ChatApiClient.ChatMessage;

@SuppressLint({"UseCompatLoadingForDrawables", "JavascriptInterface", "SetTextI18n"})
@PrismBundle(includeAll = true)
public class MainActivity extends Activity {

    private int selectedTab = 0;
    private TextView tvGptReply;
    private EditText etUserInput;
    private ImageButton btSend, btImage;
    private Spinner spModels;
    private ScrollView svChatArea;
    private LinearLayout llChatList;
    private Handler handler = new Handler();
    private Markwon markwon;
    private long asrStartTime = 0;
    BroadcastReceiver localReceiver = null;

    private static boolean isAlive = false;
    private static boolean isRunning = false;

    ChatApiClient chatApiClient = null;
    private String chatApiBuffer = "";

    private TextToSpeech tts = null;
    private boolean ttsEnabled = true;
    final private List<String> ttsSentenceSeparator = new ArrayList<String>() {{
        add("。");add(".");
        add("？");add("?");
        add("！");add("!");
        add("……");
        add("\n");
    }};
    private int ttsSentenceEndIndex = 0;

    private boolean multiChat = false;
    private List<ChatMessage> multiChatList = new ArrayList<>();

    AsrClientBase asrClient = null;
    AsrClientBase.IAsrCallback asrCallback = null;

    WebScraper webScraper = null;

    Bitmap selectedImageBitmap = null;
    Uri photoUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GlobalDataHolder.init(this);

        markwon = Markwon.builder(this)
                .usePlugin(SyntaxHighlightPlugin.create(new Prism4j(new GrammarLocatorDef()), Prism4jThemeDefault.create(0)))
                .usePlugin(JLatexMathPlugin.create(40, builder -> builder.inlinesEnabled(true)))
//                .usePlugin(TablePlugin.create(this)) // unstable
//                .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
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
                Log.e("TTS", "初始化失败 ErrorCode: " + status);
            }
        });

        setContentView(R.layout.activity_main);

        overridePendingTransition(R.anim.translate_up_in, R.anim.translate_down_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        tvGptReply = findViewById(R.id.tv_chat_notice);
        tvGptReply.setTextIsSelectable(true);
        tvGptReply.setMovementMethod(LinkMovementMethod.getInstance());
        etUserInput = findViewById(R.id.et_user_input);

        btSend = findViewById(R.id.bt_send);
        btImage = findViewById(R.id.bt_image);
        svChatArea = findViewById(R.id.sv_chat_list);
        llChatList = findViewById(R.id.ll_chat_list);

        Intent activityIntent = getIntent();
        if(activityIntent != null){
            String action = activityIntent.getAction();
            if(Intent.ACTION_PROCESS_TEXT.equals(action)) {
                String text = activityIntent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                if(text != null){
                    etUserInput.setText(text);
                }
            } else if(Intent.ACTION_SEND.equals(action)) {
                String type = activityIntent.getType();
                if(type != null && type.startsWith("image/")) {
                    Uri imageUri = activityIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if(imageUri != null) {
                        try {
                            Bitmap bitmap = (Bitmap) BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
                            selectedImageBitmap = bitmap;
                            if(GlobalDataHolder.getLimitVisionSize()) {
                                if (bitmap.getWidth() < bitmap.getHeight())
                                    selectedImageBitmap = resizeBitmap(bitmap, 512, 2048);
                                else
                                    selectedImageBitmap = resizeBitmap(bitmap, 2048, 512);
                            }
                            btImage.setImageResource(R.drawable.image_enabled);
                            if(!GlobalDataHolder.getGptModel().contains("vision"))
                                Toast.makeText(this, "请选择支持vision的模型以发送图片", Toast.LENGTH_LONG).show();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        if(GlobalDataHolder.getSelectedTab() != -1 && GlobalDataHolder.getSelectedTab() < GlobalDataHolder.getTabDataList().size())
            selectedTab = GlobalDataHolder.getSelectedTab();
        updateTabListView();

        webScraper = new WebScraper(this, findViewById(R.id.ll_main_base));

        chatApiClient = new ChatApiClient(GlobalDataHolder.getGptApiHost(),
                GlobalDataHolder.getGptApiKey(),
                GlobalDataHolder.getGptModel(),
                new ChatApiClient.OnReceiveListener() {
                    private long lastRenderTime = 0;

                    @Override
                    public void onMsgReceive(String message) {
                        chatApiBuffer += message;
                        handler.post(() -> {
                            if(System.currentTimeMillis() - lastRenderTime > 100) {
                                boolean isBottom = svChatArea.getChildAt(0).getBottom()
                                        <= svChatArea.getHeight() + svChatArea.getScrollY();
                                try {
                                    markwon.setMarkdown(tvGptReply, chatApiBuffer);
                                } catch (Exception e){
                                    e.printStackTrace();
                                }
                                if(isBottom){
                                    scrollChatAreaToBottom();
                                }
                                lastRenderTime = System.currentTimeMillis();
                            }

                            if(ttsEnabled) {
                                String wholeText = tvGptReply.getText().toString();
                                if(ttsSentenceEndIndex < wholeText.length()) {
                                    int nextSentenceEndIndex = wholeText.length();
                                    boolean found = false;
                                    for(String separator : ttsSentenceSeparator) {
                                        int index = wholeText.indexOf(separator, ttsSentenceEndIndex);
                                        if(index != -1 && index < nextSentenceEndIndex) {
                                            nextSentenceEndIndex = index + separator.length();
                                            found = true;
                                        }
                                    }
                                    if(found) {
                                        String sentence = wholeText.substring(ttsSentenceEndIndex, nextSentenceEndIndex);
                                        ttsSentenceEndIndex = nextSentenceEndIndex;
                                        tts.speak(sentence, TextToSpeech.QUEUE_ADD, null);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onFinished(boolean completed) {
                        handler.post(() -> {
                            String referenceStr = "\n\n参考网页：";
                            int referenceCount = 0;
                            if(completed) {
                                int questionIndex = multiChatList.size() - 1;
                                while(questionIndex >= 0 && multiChatList.get(questionIndex).role != ChatRole.USER) {
                                    questionIndex--;
                                }
                                for(int i = questionIndex + 1; i < multiChatList.size(); i++) {
                                    if(multiChatList.get(i).role == ChatRole.FUNCTION
                                        && multiChatList.get(i-1).role == ChatRole.ASSISTANT
                                        && multiChatList.get(i-1).functionName != null) {
                                        String funcName = multiChatList.get(i-1).functionName;
                                        String funcArgs = multiChatList.get(i-1).contentText;
                                        if(funcName.equals("get_html_text")) {
                                            String url = new JSONObject(funcArgs).getStr("url");
                                            referenceStr += String.format("[[%s]](%s) ", ++referenceCount, url);
                                        }
                                    }
                                }
                            }
                            try {
                                multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setText(chatApiBuffer));
                                ((LinearLayout) tvGptReply.getParent()).setTag(multiChatList.get(multiChatList.size() - 1));
                                btSend.setImageResource(R.drawable.send_btn);
                                markwon.setMarkdown(tvGptReply, chatApiBuffer);
                                String ttsText = tvGptReply.getText().toString();
                                if(ttsEnabled && ttsText.length() > ttsSentenceEndIndex) {
                                    tts.speak(ttsText.substring(ttsSentenceEndIndex), TextToSpeech.QUEUE_ADD, null);
                                }
                                if(referenceCount > 0) {
                                    chatApiBuffer += referenceStr;
                                    markwon.setMarkdown(tvGptReply, chatApiBuffer);
                                }
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        });
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
                            btSend.setImageResource(R.drawable.send_btn);
                        });
                    }

                    @Override
                    public void onFunctionCall(String name, String arg) {
                        Log.d("FunctionCall", String.format("%s: %s", name, arg));
                        multiChatList.add(new ChatMessage(ChatRole.ASSISTANT).setFunction(name).setText(arg));
                        if (name.equals("get_html_text")) {
                            try {
                                JSONObject argJson = new JSONObject(arg);
                                String url = argJson.getStr("url");
                                runOnUiThread(() -> {
                                    markwon.setMarkdown(tvGptReply, String.format("正在访问: [%s](%s)", URLDecoder.decode(url), url));
                                    webScraper.load(url, new WebScraper.Callback() {
                                        @Override
                                        public void onLoadResult(String result) {
                                            postSendFunctionReply(name, result);
//                                            Log.d("FunctionCall", String.format("Response: %s", result));
                                        }

                                        @Override
                                        public void onLoadFail(String message) {
                                            postSendFunctionReply(name, "Failed to get response of this url.");
                                        }
                                    });
                                    Log.d("FunctionCall", String.format("Loading url: %s", url));
                                });
                            } catch (JSONException e) {
                                e.printStackTrace();
                                postSendFunctionReply(name, "Error when getting response.");
                            }
//                        } else if (name.equals("start_app")) { // TODO: ADD FUNCTIONS
//                            try {
//                                JSONObject argJson = new JSONObject(arg);
//                                String packageName = argJson.getStr("package");
//                                Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
//                                if (intent != null) {
//                                    startActivity(intent);
//                                    postSendFunctionReply(name, "App started.");
//                                } else {
//                                    postSendFunctionReply(name, "App not found.");
//                                }
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                                postSendFunctionReply(name, "Error when starting app.");
//                            }
//                        } else if (name.equals("view_uri")) {
//                            try {
//                                JSONObject argJson = new JSONObject(arg);
//                                String uri = argJson.getStr("uri");
//                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
//                                startActivity(intent);
//                                postSendFunctionReply(name, "Activity started.");
//                            } catch (JSONException e) {
//                                e.printStackTrace();
//                                postSendFunctionReply(name, "Error when starting activity.");
//                            }
                        } else {
                            postSendFunctionReply(name, "Function not found.");
                            Log.d("FunctionCall", String.format("Function not found: %s", name));
                        }
                    }
                });

        setFunctions();

        btSend.setOnClickListener(view -> {
            if (chatApiClient.isStreaming()) {
                chatApiClient.stop();
            }else if(webScraper.isLoading()){
                webScraper.stopLoading();
                if(tvGptReply != null)
                    tvGptReply.setText("已取消访问网页。");
                btSend.setImageResource(R.drawable.send_btn);
            }else{
                tts.stop();
                sendQuestion(null);
            }
        });

        btImage.setOnClickListener(view -> {
            if (selectedImageBitmap != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.image_preview_dialog, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                dialog.getWindow().setContentView(dialogView);
                ((ImageView) dialogView.findViewById(R.id.iv_image_preview)).setImageBitmap(selectedImageBitmap);
                ((TextView) dialogView.findViewById(R.id.tv_image_preview_size)).setText(String.format("%s x %s", selectedImageBitmap.getWidth(), selectedImageBitmap.getHeight()));
                dialogView.findViewById(R.id.bt_image_preview_cancel).setOnClickListener(view1 -> dialog.dismiss());
                dialogView.findViewById(R.id.bt_image_preview_del).setOnClickListener(view1 -> {
                    dialog.dismiss();
                    selectedImageBitmap = null;
                    btImage.setImageResource(R.drawable.image);
                });
                dialogView.findViewById(R.id.bt_image_preview_reselect).setOnClickListener(view1 -> {
                    dialogView.findViewById(R.id.bt_image_preview_del).performClick();
                    btImage.performClick();
                });
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                LayoutInflater inflater = LayoutInflater.from(this);
                View dialogView = inflater.inflate(R.layout.image_method_dialog, null);
                AlertDialog dialog = builder.create();
                dialog.show();
                dialog.getWindow().setContentView(dialogView);
                dialogView.findViewById(R.id.bt_take_photo).setOnClickListener(view1 -> {
                    dialog.dismiss();
                    photoUri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", new File(getCacheDir(), "photo.jpg"));
                    Intent intent=new Intent();
                    intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(intent, 1);
                });
                dialogView.findViewById(R.id.bt_select_from_album).setOnClickListener(view1 -> {
                    dialog.dismiss();
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, 2);
                });
                dialogView.findViewById(R.id.bt_image_cancel).setOnClickListener(view1 -> dialog.dismiss());
            }
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
            if(chatApiClient.isStreaming()){
                chatApiClient.stop();
            }
            multiChatList.clear();
            llChatList.removeAllViews();
            tts.stop();
            TextView tv = new TextView(this);
            tv.setTextColor(Color.parseColor("#000000"));
            tv.setTextSize(16);
            tv.setPadding(dpToPx(10), 0, dpToPx(10), 0);
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

        (findViewById(R.id.cv_settings)).setOnClickListener(view -> {
            startActivityForResult(new Intent(MainActivity.this, TabConfActivity.class), 0);
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

        updateModelSpinner();
        updateImageButtonVisible();

        isAlive = true;

        requestPermission();

        asrCallback = new AsrClientBase.IAsrCallback() {
            @Override
            public void onError(String msg) {
                if(tvGptReply != null) {
                    tvGptReply.setText(String.format("语音识别出错: %s", msg));
                }else{
                    Toast.makeText(MainActivity.this, String.format("语音识别出错: %s", msg), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onResult(String result) {
                etUserInput.setText(result);
            }
        };
        setAsrClient(GlobalDataHolder.getAsrUseBaidu() ? "baidu" : "hms");

        localReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_START")) {
                    tts.stop();
                    asrClient.startRecongnize();
                    asrStartTime = System.currentTimeMillis();
                    etUserInput.setText("");
                    etUserInput.setHint("正在聆听...");
                } else if(action.equals("com.skythinker.gptassistant.KEY_SPEECH_STOP")) {
                    etUserInput.setHint("在此输入问题，长按可清除");
                    if(System.currentTimeMillis() - asrStartTime < 1000) {
                        asrClient.cancelRecongnize();
                    } else {
                        asrClient.stopRecongnize();
                    }
                } else if(action.equals("com.skythinker.gptassistant.KEY_SEND")) {
                    if(!chatApiClient.isStreaming())
                        sendQuestion(null);
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

    private void setAsrClient(String type) {
        if(asrClient != null) {
            asrClient.destroy();
        }
        if(type.equals("baidu")) {
            asrClient = new BaiduAsrClient(this);
            asrClient.setCallback(asrCallback);
        } else if (type.equals("hms")) {
            asrClient = new HmsAsrClient(this);
            asrClient.setCallback(asrCallback);
        }
    }

    private void setFunctions() {
        chatApiClient.clearAllFunctions();
        if(GlobalDataHolder.getEnableInternetAccess()) {
            chatApiClient.addFunction("get_html_text", "get all innerText and links of a web page", "{url: {type: string, description: html url}}");
        }
//        if(false) { // TODO: add function
//            chatApiClient.addFunction("start_app", "start an android app", "{package: {type: string, description: app package name}}");
//            chatApiClient.addFunction("view_uri", "start an activity using Intent.ACTION_VIEW with giving uri", "{uri: {type: string, description: target uri}}");
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 0) {
            int tabNum = GlobalDataHolder.getTabDataList().size();
            if(selectedTab >= tabNum)
                selectedTab = tabNum - 1;

            if(GlobalDataHolder.getSelectedTab() != -1)
                GlobalDataHolder.saveSelectedTab(selectedTab);

            updateTabListView();
            updateModelSpinner();
            updateImageButtonVisible();

            chatApiClient.setApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey());
            chatApiClient.setModel(GlobalDataHolder.getGptModel());

            if(GlobalDataHolder.getAsrUseBaidu() && asrClient instanceof HmsAsrClient) {
                setAsrClient("baidu");
            } else if(!GlobalDataHolder.getAsrUseBaidu() && asrClient instanceof BaiduAsrClient) {
                setAsrClient("hms");
            }

            setFunctions();
        } else if((requestCode == 1 || requestCode == 2) && resultCode == RESULT_OK) {
            Uri uri = requestCode == 1 ? photoUri : data.getData();
            try {
                Bitmap bitmap = (Bitmap) BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                selectedImageBitmap = bitmap;
                if(GlobalDataHolder.getLimitVisionSize()) {
                    if (bitmap.getWidth() < bitmap.getHeight())
                        selectedImageBitmap = resizeBitmap(bitmap, 512, 2048);
                    else
                        selectedImageBitmap = resizeBitmap(bitmap, 2048, 512);
                }
                btImage.setImageResource(R.drawable.image_enabled);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void scrollChatAreaToBottom() {
        svChatArea.post(() -> {
            int delta = svChatArea.getChildAt(0).getBottom()
                    - (svChatArea.getHeight() + svChatArea.getScrollY());
            if(delta != 0)
                svChatArea.smoothScrollBy(0, delta);
        });
    }

    private void updateImageButtonVisible() {
        if(GlobalDataHolder.getGptModel().contains("vision"))
            btImage.setVisibility(View.VISIBLE);
        else
            btImage.setVisibility(View.GONE);
    }

    private void updateModelSpinner() {
        Spinner spModels = findViewById(R.id.sp_main_model);
        List<String> models = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.models)));
        models.addAll(GlobalDataHolder.getCustomModels());
        ArrayAdapter<String> modelsAdapter = new ArrayAdapter<String>(this, R.layout.main_model_spinner_item, models) {
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                if(spModels.getSelectedItemPosition() == position) {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    tv.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.tag_background_unselected));
                } else {
                    tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                    tv.setBackgroundColor(Color.WHITE);
                }
                return tv;
            }
        };
        modelsAdapter.setDropDownViewResource(R.layout.model_spinner_dropdown_item);
        spModels.setAdapter(modelsAdapter);
        spModels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                GlobalDataHolder.saveGptApiInfo(GlobalDataHolder.getGptApiHost(), GlobalDataHolder.getGptApiKey(), adapterView.getItemAtPosition(i).toString(), GlobalDataHolder.getCustomModels());
                chatApiClient.setModel(GlobalDataHolder.getGptModel());
                updateImageButtonVisible();
                modelsAdapter.notifyDataSetChanged();
            }
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        for(int i = 0; i < modelsAdapter.getCount(); i++) {
            if(modelsAdapter.getItem(i).equals(GlobalDataHolder.getGptModel())) {
                spModels.setSelection(i);
                break;
            }
            if(i == modelsAdapter.getCount() - 1) {
                spModels.setSelection(0);
            }
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
                if(GlobalDataHolder.getSelectedTab() != -1) {
                    GlobalDataHolder.saveSelectedTab(selectedTab);
                }
                updateTabListView();
            });
            tabList.addView(tabBtn);
        }
    }

    private LinearLayout addChatView(ChatRole role, String content, String imageBase64) {
        ViewGroup.MarginLayoutParams iconParams = new ViewGroup.MarginLayoutParams(dpToPx(30), dpToPx(30));
        iconParams.setMargins(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12));

        ViewGroup.MarginLayoutParams contentParams = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.setMargins(dpToPx(4), dpToPx(15), dpToPx(4), dpToPx(15));

        LinearLayout.LayoutParams popupIconParams = new LinearLayout.LayoutParams(dpToPx(30), dpToPx(30));
        popupIconParams.setMargins(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5));

        LinearLayout llOuter = new LinearLayout(this);
        llOuter.setOrientation(LinearLayout.HORIZONTAL);
        if(role == ChatRole.ASSISTANT)
            llOuter.setBackgroundColor(Color.parseColor("#0A000000"));

        ImageView ivIcon = new ImageView(this);
        if(role == ChatRole.USER)
            ivIcon.setImageResource(R.drawable.chat_user_icon);
        else
            ivIcon.setImageResource(R.drawable.chat_gpt_icon);
        ivIcon.setLayoutParams(iconParams);

        TextView tvContent = new TextView(this);
        SpannableString spannableString = null;
        if(imageBase64 != null) {
            spannableString = new SpannableString(content + "\n ");
            Bitmap bitmap = base64ToBitmap(imageBase64);
            int maxSize = dpToPx(100);
            bitmap = resizeBitmap(bitmap, maxSize, maxSize);
            ImageSpan imageSpan = new ImageSpan(this, bitmap);
            spannableString.setSpan(imageSpan, content.length()+1, content.length() + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            spannableString = new SpannableString(content);
        }
        tvContent.setText(spannableString);
        tvContent.setTextSize(16);
        tvContent.setTextColor(Color.BLACK);
        tvContent.setLayoutParams(contentParams);
        tvContent.setTextIsSelectable(true);
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());

        LinearLayout llPopup = new LinearLayout(this);
        llPopup.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        PaintDrawable popupBackground = new PaintDrawable(Color.TRANSPARENT);
        llPopup.setBackground(popupBackground);
        llPopup.setOrientation(LinearLayout.HORIZONTAL);

        PopupWindow popupWindow = new PopupWindow(llPopup, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
        ivIcon.setTag(popupWindow);

        CardView cvDelete = new CardView(this);
        cvDelete.setForeground(getDrawable(R.drawable.clear_btn));
        cvDelete.setOnClickListener(view -> {
            popupWindow.dismiss();
            ChatMessage chat = (ChatMessage) llOuter.getTag();
            if(chat != null) {
                int index = multiChatList.indexOf(chat);
                multiChatList.remove(chat);
                while(--index > 0 && (multiChatList.get(index).role == ChatRole.FUNCTION
                        || multiChatList.get(index).functionName != null && multiChatList.get(index).functionName.equals("get_html_text")))
                    multiChatList.remove(index);
            }
            if(tvContent == tvGptReply) {
                if(chatApiClient.isStreaming())
                    chatApiClient.stop();
                tts.stop();
            }
            llChatList.removeView(llOuter);
            if(llChatList.getChildCount() == 0)
                ((CardView) findViewById(R.id.cv_clear_chat)).performClick();
        });
        llPopup.addView(cvDelete);

        CardView cvDelBelow = new CardView(this);
        cvDelBelow.setForeground(getDrawable(R.drawable.del_below_btn));
        cvDelBelow.setOnClickListener(view -> {
            popupWindow.dismiss();
            int index = llChatList.indexOfChild(llOuter);
            while(llChatList.getChildCount() > index && llChatList.getChildAt(0) instanceof LinearLayout) {
                PopupWindow pw = (PopupWindow) ((LinearLayout) llChatList.getChildAt(llChatList.getChildCount() - 1)).getChildAt(0).getTag();
                ((LinearLayout) pw.getContentView()).getChildAt(0).performClick();
            }
        });
        llPopup.addView(cvDelBelow);

        if(role == ChatRole.USER) {
            CardView cvEdit = new CardView(this);
            cvEdit.setForeground(getDrawable(R.drawable.edit_btn));
            cvEdit.setOnClickListener(view -> {
                popupWindow.dismiss();
                ChatMessage chat = (ChatMessage) llOuter.getTag();
                String text = chat.contentText;
                if(chat.contentImageBase64 != null) {
                    if(text.endsWith("\n "))
                        text = text.substring(0, text.length() - 2);
                    selectedImageBitmap = base64ToBitmap(chat.contentImageBase64);
                    btImage.setImageResource(R.drawable.image_enabled);
                } else {
                    selectedImageBitmap = null;
                    btImage.setImageResource(R.drawable.image);
                }
                etUserInput.setText(text);
                cvDelBelow.performClick();
            });
            llPopup.addView(cvEdit);

            CardView cvRetry = new CardView(this);
            cvRetry.setForeground(getDrawable(R.drawable.retry_btn));
            cvRetry.setOnClickListener(view -> {
                popupWindow.dismiss();
                ChatMessage chat = (ChatMessage) llOuter.getTag();
                String text = chat.contentText;
                if(chat.contentImageBase64 != null) {
                    if(text.endsWith("\n "))
                        text = text.substring(0, text.length() - 2);
                    selectedImageBitmap = base64ToBitmap(chat.contentImageBase64);
                } else {
                    selectedImageBitmap = null;
                }
                cvDelBelow.performClick();
                sendQuestion(text);
            });
            llPopup.addView(cvRetry);
        }

        CardView cvCopy = new CardView(this);
        cvCopy.setForeground(getDrawable(R.drawable.copy_btn));
        cvCopy.setOnClickListener(view -> {
            popupWindow.dismiss();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("chat", tvContent.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
        llPopup.addView(cvCopy);

        for(int i = 0; i < llPopup.getChildCount(); i++) {
            CardView cvBtn = (CardView) llPopup.getChildAt(i);
            cvBtn.setLayoutParams(popupIconParams);
            cvBtn.setCardBackgroundColor(Color.WHITE);
            cvBtn.setRadius(dpToPx(5));
        }

        ivIcon.setOnClickListener(view -> {
            popupWindow.showAsDropDown(view, dpToPx(30), -dpToPx(35));
        });

        llOuter.addView(ivIcon);
        llOuter.addView(tvContent);

        llChatList.addView(llOuter);

        return llOuter;
    }

    private void sendQuestion(String input){
        if(!multiChat){
            llChatList.removeAllViews();
            multiChatList.clear();
        }

        String userInput = (input == null) ? etUserInput.getText().toString() : input;
        if(multiChatList.size() == 0 && input == null){
            String template = GlobalDataHolder.getTabDataList().get(selectedTab).getPrompt();
            if(!template.contains("%input%"))
                template += "%input%";
            String question = template.replace("%input%", userInput);
            multiChatList.add(new ChatMessage(ChatRole.USER).setText(question));
        }else {
            multiChatList.add(new ChatMessage(ChatRole.USER).setText(userInput));
        }

        if(selectedImageBitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            selectedImageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            multiChatList.get(multiChatList.size() - 1).setImage(base64);
        }

        if(llChatList.getChildCount() > 0 && llChatList.getChildAt(0) instanceof TextView){
            llChatList.removeViewAt(0);
        }

        if(multiChat && multiChatList.size() > 0 && llChatList.getChildCount() > 0){
            String firstUserInput = multiChatList.get(0).contentText;
            ((TextView) ((LinearLayout) llChatList.getChildAt(0)).getChildAt(1)).setText(firstUserInput);
        }

        if(GlobalDataHolder.getOnlyLatestWebResult()) {
            for (int i = 0; i < multiChatList.size(); i++) {
                ChatMessage chatItem = multiChatList.get(i);
                if (chatItem.role == ChatRole.FUNCTION) {
                    multiChatList.remove(i);
                    i--;
                    if(i > 0 && multiChatList.get(i).role == ChatRole.ASSISTANT) {
                        multiChatList.remove(i);
                        i--;
                    }
                }
            }
        }

        LinearLayout llInput = addChatView(ChatRole.USER, multiChat ? multiChatList.get(multiChatList.size() - 1).contentText : userInput, multiChatList.get(multiChatList.size() - 1).contentImageBase64);
        LinearLayout llReply = addChatView(ChatRole.ASSISTANT, "正在等待回复...", null);

        llInput.setTag(multiChatList.get(multiChatList.size() - 1));

        tvGptReply = (TextView) llReply.getChildAt(1);

        scrollChatAreaToBottom();

        chatApiBuffer = "";
        ttsSentenceEndIndex = 0;
        chatApiClient.sendPromptList(multiChatList);
        btImage.setImageResource(R.drawable.image);
        selectedImageBitmap = null;
        btSend.setImageResource(R.drawable.cancel_btn);
    }

    private void postSendFunctionReply(String funcName, String reply) {
        handler.post(() -> {
            Log.d("FunctionCall", "postSendFunctionReply: " + funcName);
            multiChatList.add(new ChatMessage(ChatRole.FUNCTION).setFunction(funcName).setText(reply));
            chatApiClient.sendPromptList(multiChatList);
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = 1;
        if(width > maxWidth || height > maxHeight)
            scale = Math.min((float)maxWidth / width, (float)maxHeight / height);
        return Bitmap.createScaledBitmap(bitmap, (int)(width * scale), (int)(height * scale), true);
    }

    private Bitmap base64ToBitmap(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
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
        String[] tmpList = new String[toApplyList.size()];
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
        asrClient.destroy();
        tts.stop();
        tts.shutdown();
        webScraper.destroy();
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_up_in, R.anim.translate_down_out);
    }
}