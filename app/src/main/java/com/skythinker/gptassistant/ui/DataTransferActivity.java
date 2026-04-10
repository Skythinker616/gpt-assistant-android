package com.skythinker.gptassistant.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;

import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.DataTransferManager;
import com.skythinker.gptassistant.data.GlobalDataHolder;
import com.skythinker.gptassistant.tool.GlobalUtils;

public class DataTransferActivity extends Activity {

    private static final int REQUEST_EXPORT = 1;
    private static final int REQUEST_IMPORT = 2;
    private static final int STATE_IDLE = 0;
    private static final int STATE_EXPORTING = 1;
    private static final int STATE_IMPORTING = 2;

    private final Handler handler = new Handler();
    private Switch swLlm, swAsr, swTemplates, swChats;
    private View exportRow, importRow;
    private TextView tvExportTip, tvImportTip;
    private int transferState = STATE_IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlobalDataHolder.init(this);
        setContentView(R.layout.activity_data_transfer);
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.parseColor("#F5F6F7"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        swLlm = findViewById(R.id.sw_data_transfer_llm);
        swAsr = findViewById(R.id.sw_data_transfer_asr);
        swTemplates = findViewById(R.id.sw_data_transfer_templates);
        swChats = findViewById(R.id.sw_data_transfer_chats);
        exportRow = findViewById(R.id.ll_data_transfer_export);
        importRow = findViewById(R.id.ll_data_transfer_import);
        tvExportTip = findViewById(R.id.tv_data_transfer_export_tip);
        tvImportTip = findViewById(R.id.tv_data_transfer_import_tip);

        bindToggleRow(R.id.ll_data_transfer_llm, swLlm);
        bindToggleRow(R.id.ll_data_transfer_asr, swAsr);
        bindToggleRow(R.id.ll_data_transfer_templates, swTemplates);
        bindToggleRow(R.id.ll_data_transfer_chats, swChats);

        exportRow.setOnClickListener(view -> startExportFlow());
        importRow.setOnClickListener(view -> startImportFlow());
        findViewById(R.id.bt_data_transfer_back).setOnClickListener(view -> finish());
    }

    private void bindToggleRow(int rowId, Switch toggleView) {
        findViewById(rowId).setOnClickListener(view -> toggleView.setChecked(!toggleView.isChecked()));
    }

    private DataTransferManager.Options getSelectedOptions() {
        DataTransferManager.Options options = new DataTransferManager.Options();
        options.includeLlm = swLlm.isChecked();
        options.includeAsr = swAsr.isChecked();
        options.includeTemplates = swTemplates.isChecked();
        options.includeChats = swChats.isChecked();
        return options;
    }

    private boolean ensureHasSelection(DataTransferManager.Options options) {
        if(options.isEmpty()) {
            new ConfirmDialog(this)
                    .setContent(getString(R.string.data_transfer_toast_no_item))
                    .setCancelButtonVisibility(View.GONE)
                    .show();
            return false;
        }
        return true;
    }

    private void startExportFlow() {
        if(transferState != STATE_IDLE) {
            return;
        }
        DataTransferManager.Options options = getSelectedOptions();
        if(!ensureHasSelection(options)) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, DataTransferManager.buildDefaultFileName());
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void startImportFlow() {
        if(transferState != STATE_IDLE) {
            return;
        }
        DataTransferManager.Options options = getSelectedOptions();
        if(!ensureHasSelection(options)) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if(uri == null) {
            return;
        }

        DataTransferManager.Options options = getSelectedOptions();
        if(!ensureHasSelection(options)) {
            return;
        }

        if(requestCode == REQUEST_EXPORT) {
            GlobalUtils.showToast(this, getString(R.string.data_transfer_toast_exporting), false);
            setTransferState(STATE_EXPORTING);
            runExport(uri, options);
        } else if(requestCode == REQUEST_IMPORT) {
            GlobalUtils.showToast(this, getString(R.string.data_transfer_toast_importing), false);
            setTransferState(STATE_IMPORTING);
            runImport(uri, options);
        }
    }

    private void runExport(Uri uri, DataTransferManager.Options options) {
        new Thread(() -> {
            try {
                DataTransferManager.ExportResult result = DataTransferManager.exportToZip(this, uri, options);
                handler.post(() -> {
                    setTransferState(STATE_IDLE);
                    if(!canShowResultDialog()) {
                        return;
                    }
                    new ConfirmDialog(this)
                            .setTitle(getString(R.string.data_transfer_dialog_export_title))
                            .setContent(DataTransferManager.buildExportSummary(this, result, options))
                            .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                            .setCancelButtonVisibility(View.GONE)
                            .show();
                });
            } catch (Exception e) {
                handler.post(() -> setTransferState(STATE_IDLE));
                showTransferError(e);
            }
        }).start();
    }

    private void runImport(Uri uri, DataTransferManager.Options options) {
        new Thread(() -> {
            try {
                DataTransferManager.ImportResult result = DataTransferManager.importFromZip(this, uri, options);
                handler.post(() -> {
                    setTransferState(STATE_IDLE);
                    if(!canShowResultDialog()) {
                        return;
                    }
                    setResult(RESULT_OK);
                    new ConfirmDialog(this)
                            .setTitle(getString(R.string.data_transfer_dialog_import_title))
                            .setContent(DataTransferManager.buildImportSummary(this, result, options))
                            .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                            .setCancelButtonVisibility(View.GONE)
                            .setOnConfirmListener(() -> {
                                setResult(RESULT_OK);
                                finish();
                            })
                            .show();
                });
            } catch (Exception e) {
                handler.post(() -> setTransferState(STATE_IDLE));
                showTransferError(e);
            }
        }).start();
    }

    // 通过副标题提示当前状态，并锁住操作入口，避免重复触发。
    private void setTransferState(int state) {
        transferState = state;
        exportRow.setEnabled(state == STATE_IDLE);
        importRow.setEnabled(state == STATE_IDLE);
        exportRow.setAlpha(state == STATE_IDLE || state == STATE_EXPORTING ? 1f : 0.6f);
        importRow.setAlpha(state == STATE_IDLE || state == STATE_IMPORTING ? 1f : 0.6f);
        if(state == STATE_EXPORTING) {
            tvExportTip.setText(R.string.data_transfer_export_tip_running);
            tvImportTip.setText(R.string.data_transfer_import_tip);
        } else if(state == STATE_IMPORTING) {
            tvExportTip.setText(R.string.data_transfer_export_tip);
            tvImportTip.setText(R.string.data_transfer_import_tip_running);
        } else {
            tvExportTip.setText(R.string.data_transfer_export_tip);
            tvImportTip.setText(R.string.data_transfer_import_tip);
        }
    }

    private void showTransferError(Exception e) {
        e.printStackTrace();
        handler.post(() -> {
            if(!canShowResultDialog()) {
                return;
            }
            String message = e.getMessage();
            if(TextUtils.isEmpty(message)) {
                message = e.getClass().getSimpleName();
            }
            new ConfirmDialog(this)
                    .setContent(getString(R.string.data_transfer_error_prefix) + message)
                    .setContentAlignment(View.TEXT_ALIGNMENT_TEXT_START)
                    .setCancelButtonVisibility(View.GONE)
                    .show();
        });
    }

    private boolean canShowResultDialog() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.translate_left_in, R.anim.translate_right_out);
    }
}
