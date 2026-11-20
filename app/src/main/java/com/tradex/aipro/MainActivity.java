package com.tradex.aipro;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_MEDIA_PROJECTION = 1002;
    private MediaProjectionManager projectionManager;
    private String selectedEngine = "AUTO SIGNAL AI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#070a0f"));
        window.setNavigationBarColor(Color.parseColor("#070a0f"));

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1003);
            }
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.parseColor("#070a0f"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(28));

        // Circular TX Logo Emblem
        LogoView logoView = new LogoView(this);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        logoParams.bottomMargin = dp(12);
        root.addView(logoView, logoParams);

        // Title: TRADEX AI PRO
        TextView title = new TextView(this);
        title.setText("TRADEX AI PRO");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.08f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(22);
        root.addView(title, titleParams);

        // 4 AI Feature Cards
        root.addView(createFeatureCard(
                "🤖", "AUTO SIGNAL AI",
                "AI POWERED 1-MINUTE AUTOMATED SIGNALS",
                "[ 1M FRAME ]", "#00e676"));

        root.addView(createFeatureCard(
                "👁️", "SCREEN AI PRO",
                "ANALYZE CHARTS & MARKET PATTERNS WITH AI",
                "[ AI VISION ]", "#00e5ff"));

        root.addView(createFeatureCard(
                "🕯️", "CANDLE REACTION",
                "REAL-TIME CANDLESTICK PATTERN RECOGNITION",
                "[ PATTERNS ]", "#c084fc"));

        root.addView(createFeatureCard(
                "📈", "PRICE ACTION PRO",
                "ADVANCED BREAKOUT STRATEGIES & INDICATORS",
                "[ PRO STRATEGY ]", "#ffab00"));

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private View createFeatureCard(String emoji, String titleText, String descText, String tagText, String accentHex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#11161f"));
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), Color.parseColor("#1e2736"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        TextView iconBox = new TextView(this);
        iconBox.setText(emoji);
        iconBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        iconBox.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(Color.parseColor("#0a0f17"));
        iconBg.setCornerRadius(dp(14));
        iconBg.setStroke(dp(1), Color.parseColor("#243044"));
        iconBox.setBackground(iconBg);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.rightMargin = dp(14);
        card.addView(iconBox, iconParams);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tTitle = new TextView(this);
        tTitle.setText(titleText);
        tTitle.setTextColor(Color.WHITE);
        tTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tTitle.setTypeface(Typeface.DEFAULT_BOLD);

        TextView tDesc = new TextView(this);
        tDesc.setText(descText);
        tDesc.setTextColor(Color.parseColor("#8b9bb4"));
        tDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tDesc.setPadding(0, dp(3), 0, dp(5));

        TextView tTag = new TextView(this);
        tTag.setText(tagText);
        tTag.setTextColor(Color.parseColor(accentHex));
        tTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tTag.setTypeface(Typeface.DEFAULT_BOLD);

        textCol.addView(tTitle);
        textCol.addView(tDesc);
        textCol.addView(tTag);
        card.addView(textCol);

        card.setOnClickListener(v -> {
            selectedEngine = titleText;
            startOverlayFlow();
        });

        return card;
    }

    private void startOverlayFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "অনুগ্রহ করে Display over other apps পারমিশন চালু করুন", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
            }
        } else if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, FloatingOverlayService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                serviceIntent.putExtra("engine", selectedEngine);
                startForegroundService(serviceIntent);
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Screen share permission cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private static class LogoView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public LogoView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(cx, cy) - 6f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#091118"));
            canvas.drawCircle(cx, cy, r, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setShader(new LinearGradient(0, 0, w, h,
                    Color.parseColor("#00e676"), Color.parseColor("#00b0ff"), Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#00e676"));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(r * 0.62f);
            canvas.drawText("TX", cx, cy + r * 0.12f, paint);

            float pillW = r * 1.35f;
            float pillH = r * 0.34f;
            RectF pill = new RectF(cx - pillW / 2f, cy + r * 0.28f, cx + pillW / 2f, cy + r * 0.28f + pillH);
            canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint);

            paint.setColor(Color.parseColor("#04130b"));
            paint.setTextSize(pillH * 0.56f);
            canvas.drawText("TradeX AI Pro", cx, pill.centerY() + pillH * 0.2f, paint);
        }
    }
}
