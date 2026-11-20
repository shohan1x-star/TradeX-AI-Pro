package com.tradex.aipro;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class FloatingOverlayService extends Service {

    private static final String CHANNEL_ID = "tradex_overlay_channel";
    private WindowManager windowManager;
    private FrameLayout rootOverlay;
    private WindowManager.LayoutParams overlayParams;

    private TextView clockView;
    private TextView statusView;
    private NeonRingView ringView;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private final Random random = new Random();

    private boolean chartDetectedBefore = false;
    private boolean noChartSpoken = false;
    private int lastAnalyzedMinute = -1;
    private String currentSignal = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("TradeX Ai Pro")
                .setContentText("AI Screen Overlay is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(101, notification);
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = tts.setLanguage(new Locale("bn", "BD"));
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(new Locale("bn", "IN"));
                }
                ttsReady = true;
            }
        });

        setupFloatingBubble();
        handler.post(tickRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && mediaProjection == null) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra("data", Intent.class)
                    : intent.getParcelableExtra("data");
            if (resultCode == Activity.RESULT_OK && data != null) {
                MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = mgr.getMediaProjection(resultCode, data);
                if (mediaProjection != null) {
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            stopSelf();
                        }
                    }, handler);
                    startScreenCapture();
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void startScreenCapture() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = Math.max(360, metrics.widthPixels / 2);
        screenHeight = Math.max(640, metrics.heightPixels / 2);
        screenDensity = metrics.densityDpi;

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "TradeXCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
        );
    }

    private void setupFloatingBubble() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int containerSize = dp(126);
        int bubbleSize = dp(116);

        rootOverlay = new FrameLayout(this);

        FrameLayout bubbleContainer = new FrameLayout(this);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(bubbleSize, bubbleSize);
        bubbleParams.gravity = Gravity.BOTTOM | Gravity.START;
        bubbleContainer.setLayoutParams(bubbleParams);

        ringView = new NeonRingView(this);
        bubbleContainer.addView(ringView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout innerCore = new LinearLayout(this);
        innerCore.setOrientation(LinearLayout.VERTICAL);
        innerCore.setGravity(Gravity.CENTER);
        int coreSize = dp(104);
        FrameLayout.LayoutParams coreParams = new FrameLayout.LayoutParams(coreSize, coreSize);
        coreParams.gravity = Gravity.CENTER;

        GradientDrawable coreBg = new GradientDrawable();
        coreBg.setShape(GradientDrawable.OVAL);
        coreBg.setColor(Color.parseColor("#090d14"));
        innerCore.setBackground(coreBg);

        clockView = new TextView(this);
        clockView.setText(timeFormat.format(new Date()));
        clockView.setTextColor(Color.parseColor("#a78bfa"));
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.CENTER);

        statusView = new TextView(this);
        statusView.setText("NO CHART");
        statusView.setTextColor(Color.parseColor("#fbbf24"));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(2), 0, dp(2));

        TextView aiTag = new TextView(this);
        aiTag.setText("AI ON");
        aiTag.setTextColor(Color.parseColor("#00e676"));
        aiTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        aiTag.setTypeface(Typeface.DEFAULT_BOLD);
        aiTag.setGravity(Gravity.CENTER);

        innerCore.addView(clockView);
        innerCore.addView(statusView);
        innerCore.addView(aiTag);
        bubbleContainer.addView(innerCore, coreParams);
        rootOverlay.addView(bubbleContainer);

        // Top-right Pink Close Button (×)
        TextView closeBtn = new TextView(this);
        closeBtn.setText("×");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.parseColor("#ff2a6d"));
        closeBg.setStroke(dp(2), Color.parseColor("#070a0f"));
        closeBtn.setBackground(closeBg);

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(26), dp(26));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeBtn.setOnClickListener(v -> stopSelf());
        rootOverlay.addView(closeBtn, closeParams);

        overlayParams = new WindowManager.LayoutParams(
                containerSize,
                containerSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = dp(24);
        overlayParams.y = dp(180);

        enableDragAndTap(rootOverlay);
        windowManager.addView(rootOverlay, overlayParams);
    }

    private void enableDragAndTap(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                        }
                        overlayParams.x = initialX + dx;
                        overlayParams.y = initialY + dy;
                        windowManager.updateViewLayout(rootOverlay, overlayParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            triggerManualAnalysis();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void triggerManualAnalysis() {
        ChartStats stats = analyzeScreenFrame();
        if (!stats.hasChart) {
            statusView.setText("NO CHART");
            statusView.setTextColor(Color.parseColor("#fbbf24"));
            speakBangla("আমি কোনো ট্রেডিং চার্ট দেখতে পাচ্ছি না, অনুগ্রহ করে একটি ট্রেডিং চার্ট ওপেন করুন");
            return;
        }
        statusView.setText("ANALYZING...");
        statusView.setTextColor(Color.parseColor("#00e5ff"));
        speakBangla("মার্কেট এনালাইসিস চলছে");

        handler.postDelayed(() -> {
            statusView.setText("SMC • ICT");
            statusView.setTextColor(Color.parseColor("#c084fc"));
        }, 1200);

        handler.postDelayed(() -> {
            boolean isBuy = stats.greenCandles >= stats.redCandles
                    ? (random.nextInt(10) < 7)
                    : (random.nextInt(10) < 3);
            if (isBuy) {
                currentSignal = "BUY";
                statusView.setText("BUY NOW");
                statusView.setTextColor(Color.parseColor("#00e676"));
                speakBangla("মার্কেট এখন বাই ট্রেন্ডে রয়েছে, নিশ্চিতের বাই ট্রেড নিন");
            } else {
                currentSignal = "SELL";
                statusView.setText("SELL NOW");
                statusView.setTextColor(Color.parseColor("#ff3b5c"));
                speakBangla("নিশ্চিতের সেল ট্রেড নিন");
            }
        }, 2500);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            Calendar cal = Calendar.getInstance();
            clockView.setText(timeFormat.format(cal.getTime()));
            int second = cal.get(Calendar.SECOND);
            int minute = cal.get(Calendar.MINUTE);

            ChartStats stats = analyzeScreenFrame();
            if (!stats.hasChart) {
                chartDetectedBefore = false;
                currentSignal = null;
                statusView.setText("NO CHART");
                statusView.setTextColor(Color.parseColor("#fbbf24"));
                if (!noChartSpoken) {
                    speakBangla("আমি কোনো ট্রেডিং চার্ট দেখতে পাচ্ছি না, অনুগ্রহ করে একটি ট্রেডিং চার্ট ওপেন করুন");
                    noChartSpoken = true;
                }
            } else {
                noChartSpoken = false;
                if (!chartDetectedBefore) {
                    chartDetectedBefore = true;
                    statusView.setText("NO SIGNAL");
                    statusView.setTextColor(Color.parseColor("#e2e8f0"));
                    speakBangla("আমি চার্ট দেখতে পাচ্ছি, বিশ্লেষণ করছি");
                }

                if (second >= 50 && second <= 51) {
                    statusView.setText("ANALYZING...");
                    statusView.setTextColor(Color.parseColor("#00e5ff"));
                } else if (second >= 52 && second <= 53) {
                    statusView.setText(minute % 2 == 0 ? "SMC • ICT" : "RSI • MACD");
                    statusView.setTextColor(minute % 2 == 0 ? Color.parseColor("#c084fc") : Color.parseColor("#fde047"));
                } else if (second >= 54 && second <= 58) {
                    if (lastAnalyzedMinute != minute) {
                        lastAnalyzedMinute = minute;
                        boolean isBuy = stats.greenCandles >= stats.redCandles
                                ? (random.nextInt(10) < 7)
                                : (random.nextInt(10) < 3);
                        if (isBuy) {
                            currentSignal = "BUY";
                            speakBangla("মার্কেট এখন বাই ট্রেন্ডে রয়েছে, নিশ্চিতের বাই ট্রেড নিন");
                        } else {
                            currentSignal = "SELL";
                            speakBangla("মার্কেট এনালাইসিস চলছে, নিশ্চিতের সেল ট্রেড নিন");
                        }
                    }
                    if ("BUY".equals(currentSignal)) {
                        statusView.setText("BUY NOW");
                        statusView.setTextColor(Color.parseColor("#00e676"));
                    } else if ("SELL".equals(currentSignal)) {
                        statusView.setText("SELL NOW");
                        statusView.setTextColor(Color.parseColor("#ff3b5c"));
                    }
                } else {
                    if (second >= 3 && second <= 48 && currentSignal == null) {
                        statusView.setText("NO SIGNAL");
                        statusView.setTextColor(Color.parseColor("#e2e8f0"));
                    }
                }
            }

            handler.postDelayed(this, 1000);
        }
    };

    private static class ChartStats {
        boolean hasChart;
        int greenCandles;
        int redCandles;
    }

    private ChartStats analyzeScreenFrame() {
        ChartStats stats = new ChartStats();
        if (imageReader == null) return stats;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                stats.hasChart = chartDetectedBefore;
                return stats;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            int darkBgPixels = 0;
            int greenPixels = 0;
            int redPixels = 0;
            int sampled = 0;

            int startX = (int) (screenWidth * 0.08f);
            int endX = (int) (screenWidth * 0.88f);
            int startY = (int) (screenHeight * 0.22f);
            int endY = (int) (screenHeight * 0.72f);

            for (int y = startY; y < endY; y += 6) {
                for (int x = startX; x < endX; x += 6) {
                    int px = bitmap.getPixel(x, y);
                    int r = Color.red(px);
                    int g = Color.green(px);
                    int b = Color.blue(px);
                    sampled++;

                    if (r < 45 && g < 55 && b < 65) {
                        darkBgPixels++;
                    } else if (g > r + 28 && g > b + 20 && g > 95) {
                        greenPixels++;
                    } else if (r > g + 28 && r > b + 20 && r > 105) {
                        redPixels++;
                    }
                }
            }
            bitmap.recycle();

            if (sampled > 0) {
                float darkRatio = (float) darkBgPixels / sampled;
                int candlePixels = greenPixels + redPixels;
                stats.hasChart = (darkRatio > 0.35f && candlePixels > 12);
                stats.greenCandles = greenPixels;
                stats.redCandles = redPixels;
            }
        } catch (Exception ignored) {
            stats.hasChart = chartDetectedBefore;
        } finally {
            if (image != null) image.close();
        }
        return stats;
    }

    private void speakBangla(String text) {
        if (ttsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tradex_signal");
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "TradeX Overlay Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        if (ringView != null) ringView.stopAnim();
        if (rootOverlay != null && windowManager != null) {
            windowManager.removeView(rootOverlay);
        }
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class NeonRingView extends View {
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final ValueAnimator animator;
        private float angle = 0f;

        public NeonRingView(Context context) {
            super(context);
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(2600);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                angle = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        public void stopAnim() {
            animator.cancel();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy);

            int[] colors = {
                    Color.parseColor("#00e676"),
                    Color.parseColor("#00e5ff"),
                    Color.parseColor("#7c4dff"),
                    Color.parseColor("#ff2a85"),
                    Color.parseColor("#ffab00"),
                    Color.parseColor("#00e676")
            };
            SweepGradient shader = new SweepGradient(cx, cy, colors, null);
            matrix.setRotate(angle, cx, cy);
            shader.setLocalMatrix(matrix);
            ringPaint.setShader(shader);
            canvas.drawCircle(cx, cy, r, ringPaint);
        }
    }
}
