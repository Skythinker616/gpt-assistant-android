package com.skythinker.gptassistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.TypedValue;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Locale;

public class GlobalUtils {
    // 检查当前语言是否是中文
    public static boolean languageIsChinese() {
        return Locale.getDefault().getLanguage().equals("zh");
    }

    // dp转px
    public static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    // 等比缩放Bitmap到给定的尺寸范围内
    public static Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = 1;
        if(width > maxWidth || height > maxHeight)
            scale = Math.min((float)maxWidth / width, (float)maxHeight / height);
        return Bitmap.createScaledBitmap(bitmap, (int)(width * scale), (int)(height * scale), true);
    }

    // 将Base64编码转换为Bitmap
    public static Bitmap base64ToBitmap(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // 用默认方式打开URL
    public static void browseURL(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        context.startActivity(intent);
    }

    public static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", text);
        clipboard.setPrimaryClip(clip);
    }

    public static void showToast(Context context, String text, boolean isLong) {
        Toast.makeText(context, text, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, int resId, boolean isLong) {
        Toast.makeText(context, resId, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    public static boolean checkVisionSupport(String model) {
        final String[] specialVisionModels = {"gpt-4-turbo", "gpt-4o", "gpt-4o-mini"}; // 支持识图但不包含"vision"的模型
        return model.contains("vision") || Arrays.asList(specialVisionModels).contains(model) || model.endsWith("*");
    }
}
