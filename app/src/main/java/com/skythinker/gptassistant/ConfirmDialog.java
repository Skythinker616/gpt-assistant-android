package com.skythinker.gptassistant;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class ConfirmDialog{
    Dialog dialog;
    View dialogView;
    public ConfirmDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        dialogView = inflater.inflate(R.layout.confirm_dialog, null);
        dialog = builder.create();
        setOnConfirmListener(() -> {});
        setOnCancelListener(() -> {});
    }
    public void show() {
        dialog.show();
        dialog.getWindow().setContentView(dialogView);
    }
    public void dismiss() {
        dialog.dismiss();
    }
    public View getContentView() {
        return dialogView;
    }
    public ConfirmDialog setOnConfirmListener(Runnable listener) {
        (dialogView.findViewById(R.id.cv_dialog_ok)).setOnClickListener(v -> {
            listener.run();
            dialog.dismiss();
        });
        return this;
    }
    public ConfirmDialog setOnCancelListener(Runnable listener) {
        (dialogView.findViewById(R.id.cv_dialog_cancel)).setOnClickListener(v -> {
            listener.run();
            dialog.dismiss();
        });
        return this;
    }
    public ConfirmDialog setContent(String content) {
        ((TextView) dialogView.findViewById(R.id.tv_dialog_content)).setText(content);
        return this;
    }
    public ConfirmDialog setContentAlignment(int alignment) {
        ((TextView) dialogView.findViewById(R.id.tv_dialog_content)).setTextAlignment(alignment);
        return this;
    }
    public ConfirmDialog setMarkdownContent(String markdown) {
        new MarkdownRenderer(this.dialog.getContext()).render(((TextView) dialogView.findViewById(R.id.tv_dialog_content)), markdown);
        return this;
    }
    public ConfirmDialog setTitle(String title) {
        ((TextView) dialogView.findViewById(R.id.tv_dialog_title)).setText(title);
        return this;
    }
    public ConfirmDialog setOkText(String text) {
        ((TextView) dialogView.findViewById(R.id.tv_dialog_ok)).setText(text);
        return this;
    }
    public ConfirmDialog setCancelText(String text) {
        ((TextView) dialogView.findViewById(R.id.tv_dialog_cancel)).setText(text);
        return this;
    }
    public ConfirmDialog setOkButtonVisibility(int visibility) {
        (dialogView.findViewById(R.id.cv_dialog_ok)).setVisibility(visibility);
        return this;
    }
    public ConfirmDialog setCancelButtonVisibility(int visibility) {
        (dialogView.findViewById(R.id.cv_dialog_cancel)).setVisibility(visibility);
        return this;
    }
}
