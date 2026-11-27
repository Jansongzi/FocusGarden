package com.focusgarden.app;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import static com.focusgarden.app.GardenUi.*;

final class SleepLockUi extends Dialog {
    private final MainActivity activity;
    private final FocusState state;
    private final Runnable onStateChanged;

    private final ImageView backdropPhoto;
    private final View dimVeil;
    private final SevenSegmentClockView clockView;
    private final TextView remainSub;
    private final TextView streakView;
    private final TextView promptView;
    private final TextView scheduleView;
    private final FrameLayout dimBtn;
    private boolean hasPhotoBackdrop;
    private boolean dimmed;
    private int quoteOffset;

    SleepLockUi(MainActivity activity, FocusState state, Runnable onStateChanged) {
        super(activity, android.R.style.Theme_Material_NoActionBar);
        this.activity = activity;
        this.state = state;
        this.onStateChanged = onStateChanged;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                Toast.makeText(activity, "早睡锁机进行中，如需退出请点击底部按钮", Toast.LENGTH_SHORT).show();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_BACK;
        });

        FrameLayout stage = new FrameLayout(activity);
        stage.addView(new SleepBackdropView(activity), -1, -1);

        backdropPhoto = new ImageView(activity);
        backdropPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropPhoto.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdropPhoto.setVisibility(View.GONE);
        stage.addView(backdropPhoto, -1, -1);

        dimVeil = new View(activity);
        dimVeil.setBackgroundColor(0x18000000);
        stage.addView(dimVeil, -1, -1);

        LinearLayout content = column(activity);
        int gutter = dp(activity, 24);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(gutter + bars.left, dp(activity, 18) + bars.top,
                        gutter + bars.right, dp(activity, 20) + bars.bottom);
            } else {
                view.setPadding(gutter + insets.getSystemWindowInsetLeft(),
                        dp(activity, 22) + insets.getSystemWindowInsetTop(),
                        gutter + insets.getSystemWindowInsetRight(),
                        dp(activity, 20) + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        content.setPadding(gutter, dp(activity, 36), gutter, dp(activity, 28));

        // Top inspirational quote block
        TextView quoteMark = text(activity, "“", 34, 0xb8ffffff, true);
        quoteMark.setIncludeFontPadding(false);
        content.addView(quoteMark);

        TextView quoteText = text(activity, "", 15, 0xf2ffffff, false);
        quoteText.setLineSpacing(dp(activity, 3), 1.2f);
        content.addView(quoteText, -1, -2);

        Runnable renderQuote = () -> {
            QuoteBank.Quote q = QuoteBank.quoteForOffset(activity, quoteOffset);
            String author = q.author().isEmpty() ? "" : "\n—— " + q.author();
            quoteText.setText(q.text() + author);
        };
        renderQuote.run();
        quoteText.setOnClickListener(v -> {
            tapHaptic(v);
            quoteOffset++;
            renderQuote.run();
        });

        gap(content, 12);
        View line = new View(activity);
        line.setBackgroundColor(0x36ffffff);
        content.addView(line, -1, dp(activity, 1));
        gap(content, 10);

        LinearLayout statusHead = row(activity);
        streakView = text(activity, "", 13, 0xd8ffffff, true);
        weight(statusHead, streakView);
        FrameLayout bgBtn = iconBadge(activity, R.drawable.ic_wallpaper, 0x26ffffff, ON_ACCENT, 34, 17, 10);
        bgBtn.setContentDescription("切换早睡锁机背景");
        bgBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
                cardBg(activity, 0x26ffffff, 0x45ffffff, 10), null));
        bgBtn.setOnClickListener(v -> {
            tapHaptic(v);
            TimerAppearance.showSettings(activity, this::reloadBackground);
        });
        statusHead.addView(bgBtn);
        content.addView(statusHead, -1, -2);

        // Center spacer + crisp 7-segment clock
        View topSpacer = new View(activity);
        content.addView(topSpacer, new LinearLayout.LayoutParams(1, 0, 1f));

        clockView = new SevenSegmentClockView(activity);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(-1, dp(activity, 94));
        clockLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(clockView, clockLp);
        gap(content, 10);

        remainSub = text(activity, "", 13, 0xc8ffffff, false);
        remainSub.setGravity(Gravity.CENTER);
        remainSub.setFontFeatureSettings("tnum");
        content.addView(remainSub, -1, -2);

        View midSpacer = new View(activity);
        content.addView(midSpacer, new LinearLayout.LayoutParams(1, 0, 0.9f));

        promptView = text(activity, "", 20, ON_ACCENT, true);
        promptView.setGravity(Gravity.CENTER);
        promptView.setShadowLayer(dp(activity, 4), 0, dp(activity, 2), 0xaa000000);
        promptView.setVisibility(View.GONE);
        content.addView(promptView, -1, -2);
        gap(content, 6);

        // Lower-center status stack
        TextView challengeTitle = text(activity, "早睡挑战", 19, ON_ACCENT, true);
        challengeTitle.setGravity(Gravity.CENTER);
        content.addView(challengeTitle, -1, -2);
        gap(content, 4);

        TextView statusSub = text(activity, "夜间强制锁机中 · 离开页面将被拦截", 13, 0xd9ffffff, false);
        statusSub.setGravity(Gravity.CENTER);
        content.addView(statusSub, -1, -2);
        gap(content, 4);

        scheduleView = text(activity, "", 12, 0xb8ffffff, false);
        scheduleView.setGravity(Gravity.CENTER);
        content.addView(scheduleView, -1, -2);

        gap(content, 24);

        // Bottom controls bar
        LinearLayout bottomRow = row(activity);
        dimBtn = iconBadge(activity, R.drawable.ic_moon, 0x26ffffff, ON_ACCENT, 44, 20, 22);
        dimBtn.setContentDescription("切换夜间护眼暗度");
        dimBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
                cardBg(activity, 0x26ffffff, 0x45ffffff, 22), null));
        dimBtn.setOnClickListener(v -> {
            tapHaptic(v);
            dimmed = !dimmed;
            applyDimState();
            dimBtn.setAlpha(dimmed ? 0.65f : 1f);
            Toast.makeText(activity, dimmed ? "已开启深睡护眼暗屏" : "已恢复常规夜览亮度", Toast.LENGTH_SHORT).show();
        });
        bottomRow.addView(dimBtn);
        space(bottomRow, 10);

        TextView delayBtn = glassButton(activity, "修改延后 (35分)", 0x26ffffff, 0x48ffffff);
        delayBtn.setOnClickListener(v -> handleDelayLock(v));
        weight(bottomRow, delayBtn);
        space(bottomRow, 10);

        TextView exitBtn = glassButton(activity, state.isLayFlat() ? "暂停至重置" : "主动退出 (35分)", 0x42f04438, 0x70ff8a80);
        exitBtn.setOnClickListener(v -> handleExitLock(v));
        weight(bottomRow, exitBtn);
        content.addView(bottomRow, -1, -2);

        stage.addView(content, -1, -1);
        setContentView(stage);

        Window win = getWindow();
        if (win != null) {
            win.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            win.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) win.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) win.setDecorFitsSystemWindows(false);
        }
        show();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            applySystemBars(win, true);
        }
        reloadBackground();
        update(System.currentTimeMillis());
    }

    private void applyDimState() {
        int bgColor = dimmed
                ? (hasPhotoBackdrop ? 0xb8000000 : 0x98000000)
                : (hasPhotoBackdrop ? 0x6c000000 : 0x18000000);
        dimVeil.setBackgroundColor(bgColor);
    }

    void reloadBackground() {
        hasPhotoBackdrop = TimerAppearance.Mode.SLEEP.showIn(state, backdropPhoto);
        backdropPhoto.setVisibility(hasPhotoBackdrop ? View.VISIBLE : View.GONE);
        FocusState.BlockAppearance builtIn = FocusState.builtInBlockAppearance(
                TimerAppearance.Mode.SLEEP.currentForSession(activity, state));
        if (builtIn != null && !builtIn.sleepText().isEmpty()) {
            promptView.setText(builtIn.displayText(FocusState.Restriction.BEDTIME));
            promptView.setVisibility(View.VISIBLE);
        } else {
            promptView.setVisibility(View.GONE);
        }
        applyDimState();
    }

    private void handleDelayLock(View v) {
        long costMs = FocusState.SLEEP_CONFIG_COST_MINUTES * FocusState.MINUTE;
        if (!state.canAfford(costMs)) {
            haptic(v, state.hapticFeedback, HapticKind.REJECT);
            infoDialog(activity, R.drawable.ic_shield, RED_SOFT, RED,
                    "可支配时间不足 " + FocusState.SLEEP_CONFIG_COST_MINUTES + " 分钟",
                    "早睡锁机期间修改延后必须消耗 " + FocusState.SLEEP_CONFIG_COST_MINUTES
                            + " 分钟自由时间。\n\n" + (state.isLayFlat() ? "储蓄罐余额：" : "当前可支配时间：")
                            + duration(state.totalSpendableMs()) + "。",
                    "继续早睡");
            return;
        }
        tapHaptic(v);
        FocusSheets.pickSleepStartClock(activity, state.sleepStart, newStart -> {
            if (newStart == state.sleepStart) return;
            FocusState.SleepScheduleIssue issue = FocusState.sleepScheduleIssue(newStart, state.sleepEnd);
            if (issue != FocusState.SleepScheduleIssue.NONE) {
                haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                OnboardingUi.showSleepScheduleError(activity, issue);
                return;
            }
            confirmDialog(activity, R.drawable.ic_clock, AMBER_SOFT, AMBER,
                    "消耗 35 分钟延后入睡时间？",
                    "入睡时间将修改为 " + clock(newStart) + "。\n\n" + (state.isLayFlat() ? "仅从储蓄罐扣除 35 分钟" : "扣除 35 分钟自由时间") + "，本轮早睡挑战不可领奖。",
                    "取消", "消耗 35 分钟延后", true, () -> {
                        long now = System.currentTimeMillis();
                        if (state.delayActiveSleepLock(newStart, state.sleepEnd, now, ZoneId.systemDefault())) {
                            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                            onStateChanged.run();
                            Toast.makeText(activity, "已延后入睡至 " + clock(newStart) + "，消耗 35 分钟（本轮不可领奖）",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                            Toast.makeText(activity, "可支配时间不足 35 分钟", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void handleExitLock(View v) {
        if (state.isLayFlat()) {
            FocusSheets.confirmSuspendBedtime(activity, state, onStateChanged);
            return;
        }
        long costMs = FocusState.SLEEP_CONFIG_COST_MINUTES * FocusState.MINUTE;
        if (!state.canAfford(costMs)) {
            haptic(v, state.hapticFeedback, HapticKind.REJECT);
            infoDialog(activity, R.drawable.ic_shield, RED_SOFT, RED,
                    "可支配时间不足 " + FocusState.SLEEP_CONFIG_COST_MINUTES + " 分钟",
                    "主动退出夜间锁机必须消耗 " + FocusState.SLEEP_CONFIG_COST_MINUTES
                            + " 分钟自由时间。\n\n当前可支配时间（可用 + 储蓄罐）：" + duration(state.totalSpendableMs()) + "。",
                    "继续早睡");
            return;
        }
        tapHaptic(v);
        confirmDialog(activity, R.drawable.ic_shield, RED_SOFT, RED,
                "消耗 35 分钟退出早睡锁机？",
                "退出后今晚不再强制锁机，但会立即扣除 35 分钟自由时间（优先扣可用，不足扣储蓄罐），且今晚早睡挑战失败、明天不可领取奖励。",
                "继续早睡", "消耗 35 分钟退出", true, () -> {
                    long now = System.currentTimeMillis();
                    if (state.exitActiveSleepLock(now, ZoneId.systemDefault())) {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.CONFIRM);
                        onStateChanged.run();
                        Toast.makeText(activity, "已消耗 35 分钟退出今晚锁机，本轮无法领取奖励", Toast.LENGTH_SHORT).show();
                    } else {
                        haptic(activity.getWindow().getDecorView(), state.hapticFeedback, HapticKind.REJECT);
                        Toast.makeText(activity, "可支配时间不足 35 分钟", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    void update(long now) {
        ZoneId zone = ZoneId.systemDefault();
        long endMs = state.currentSleepWindowEndMs(now, zone);
        long remMs = Math.max(0L, endMs - now);
        long totalSec = (remMs + 999L) / 1000L;
        long hrs = totalSec / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;

        String hhmm = String.format(Locale.ROOT, "%02d:%02d", Math.min(99L, hrs), mins);
        boolean colonVisible = (now / 500L) % 2L == 0L;
        clockView.update(hhmm, colonVisible);

        MainActivity.setTextIfChanged(remainSub,
                String.format(Locale.ROOT, "距 %s 解锁还有 %02d:%02d:%02d",
                        clock(state.sleepEnd), hrs, mins, secs));
        int streak = state.earlySleepStreak(LocalDate.now());
        MainActivity.setTextIfChanged(streakView,
                "已连续早睡 " + streak + " 天 · 今晚安静休息");
        String reward = state.sleepTargetEndMs == endMs && endMs > now
                ? "醒来后可领取 +" : "完成完整时段可领取 +";
        MainActivity.setTextIfChanged(scheduleView,
                clock(state.sleepStart) + " – " + clock(state.sleepEnd)
                        + " · " + reward + state.earlySleepRewardMinutes + " 分钟");
    }

    private static TextView glassButton(Context context, String label, int fillColor, int strokeColor) {
        TextView btn = text(context, label, 13, ON_ACCENT, true);
        btn.setGravity(Gravity.CENTER);
        btn.setMinHeight(dp(context, 44));
        btn.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        btn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
                cardBg(context, fillColor, strokeColor, 14), null));
        btn.setFocusable(true);
        return btn;
    }

    private static final class SleepBackdropView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hill = new Path();
        private final Path aurora1 = new Path();
        private final Path aurora2 = new Path();
        private int cachedW;
        private int cachedH;
        private Shader skyShader;
        private Shader moonGlow;

        SleepBackdropView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (w != cachedW || h != cachedH) {
                cachedW = w;
                cachedH = h;
                skyShader = new LinearGradient(0f, 0f, 0f, h,
                        new int[]{0xff06101e, 0xff0c243a, 0xff10363f, 0xff0b1a2a},
                        new float[]{0f, 0.42f, 0.76f, 1f}, Shader.TileMode.CLAMP);
                moonGlow = new RadialGradient(w * 0.72f, h * 0.30f, Math.min(w, h) * 0.55f,
                        new int[]{0x445eead4, 0x1638bdf8, 0x000c243a}, null, Shader.TileMode.CLAMP);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(skyShader);
            canvas.drawRect(0f, 0f, w, h, paint);

            paint.setShader(moonGlow);
            canvas.drawRect(0f, 0f, w, h, paint);
            paint.setShader(null);

            // Soft aurora ribbons
            aurora1.reset();
            aurora1.moveTo(-w * 0.1f, h * 0.36f);
            aurora1.cubicTo(w * 0.25f, h * 0.18f, w * 0.55f, h * 0.44f, w * 1.1f, h * 0.22f);
            aurora1.lineTo(w * 1.1f, h * 0.42f);
            aurora1.cubicTo(w * 0.55f, h * 0.58f, w * 0.28f, h * 0.34f, -w * 0.1f, h * 0.50f);
            aurora1.close();
            paint.setColor(0x2234d399);
            canvas.drawPath(aurora1, paint);

            aurora2.reset();
            aurora2.moveTo(-w * 0.05f, h * 0.26f);
            aurora2.cubicTo(w * 0.35f, h * 0.12f, w * 0.65f, h * 0.32f, w * 1.08f, h * 0.16f);
            aurora2.lineTo(w * 1.08f, h * 0.28f);
            aurora2.cubicTo(w * 0.62f, h * 0.40f, w * 0.30f, h * 0.24f, -w * 0.05f, h * 0.36f);
            aurora2.close();
            paint.setColor(0x1e6ee7b7);
            canvas.drawPath(aurora2, paint);

            // Subtle starry sky specks
            float[][] stars = {
                    {0.14f, 0.12f, 1.8f}, {0.28f, 0.20f, 1.3f}, {0.42f, 0.10f, 2.0f},
                    {0.62f, 0.16f, 1.4f}, {0.82f, 0.11f, 2.1f}, {0.90f, 0.24f, 1.5f},
                    {0.18f, 0.28f, 1.4f}, {0.52f, 0.25f, 1.6f}, {0.75f, 0.21f, 1.3f},
                    {0.34f, 0.35f, 1.2f}, {0.86f, 0.38f, 1.7f}, {0.10f, 0.42f, 1.5f}
            };
            paint.setColor(0x99ffffff);
            float dp = getResources().getDisplayMetrics().density;
            for (float[] s : stars) {
                canvas.drawCircle(w * s[0], h * s[1], s[2] * dp, paint);
            }

            // Quiet distant ridge silhouette at bottom
            hill.reset();
            hill.moveTo(0f, h * 0.78f);
            hill.cubicTo(w * 0.28f, h * 0.72f, w * 0.56f, h * 0.81f, w * 1.0f, h * 0.74f);
            hill.lineTo(w, h);
            hill.lineTo(0f, h);
            hill.close();
            paint.setColor(0x70060d18);
            canvas.drawPath(hill, paint);
        }
    }

    private static final class SevenSegmentClockView extends View {
        // Segment bits: bit0=a(top), bit1=b(top-right), bit2=c(bot-right), bit3=d(bot), bit4=e(bot-left), bit5=f(top-left), bit6=g(mid)
        private static final int[] DIGIT_MASK = {
                0b0111111, // 0
                0b0000110, // 1
                0b1011011, // 2
                0b1001111, // 3
                0b1100110, // 4
                0b1101101, // 5
                0b1111101, // 6
                0b0000111, // 7
                0b1111111, // 8
                0b1101111  // 9
        };
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF seg = new RectF();
        private String hhmm = "00:00";
        private boolean colonOn = true;

        SevenSegmentClockView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription("距离解锁还剩 00:00");
        }

        void update(String hhmm, boolean colonOn) {
            boolean timeChanged = !hhmm.equals(this.hhmm);
            if (!timeChanged && colonOn == this.colonOn) return;
            this.hhmm = hhmm;
            this.colonOn = colonOn;
            if (timeChanged) setContentDescription("距离解锁还剩 " + hhmm);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || hhmm.length() < 5) return;

            float digitH = Math.min(h * 0.90f, dp(getContext(), 84));
            float digitW = digitH * 0.54f;
            float stroke = digitH * 0.115f;
            float gap = digitW * 0.24f;
            float colonW = stroke * 1.45f;
            float totalW = digitW * 4f + gap * 3f + colonW;
            float startX = (w - totalW) / 2f;
            float topY = (h - digitH) / 2f;

            drawDigit(canvas, hhmm.charAt(0), startX, topY, digitW, digitH, stroke);
            drawDigit(canvas, hhmm.charAt(1), startX + digitW + gap, topY, digitW, digitH, stroke);

            // Colon dots
            float cx = startX + digitW * 2f + gap * 1.5f + colonW * 0.5f;
            paint.setColor(colonOn ? 0xffffffff : 0x55ffffff);
            canvas.drawRoundRect(cx - stroke * 0.5f, topY + digitH * 0.27f,
                    cx + stroke * 0.5f, topY + digitH * 0.27f + stroke, stroke * 0.35f, stroke * 0.35f, paint);
            canvas.drawRoundRect(cx - stroke * 0.5f, topY + digitH * 0.61f,
                    cx + stroke * 0.5f, topY + digitH * 0.61f + stroke, stroke * 0.35f, stroke * 0.35f, paint);

            float rightStart = startX + digitW * 2f + gap * 2f + colonW;
            drawDigit(canvas, hhmm.charAt(3), rightStart, topY, digitW, digitH, stroke);
            drawDigit(canvas, hhmm.charAt(4), rightStart + digitW + gap, topY, digitW, digitH, stroke);
        }

        private void drawDigit(Canvas canvas, char ch, float x, float y, float w, float h, float t) {
            int val = (ch >= '0' && ch <= '9') ? (ch - '0') : 0;
            int mask = DIGIT_MASK[val];
            float r = t * 0.45f;
            float pad = t * 0.35f;
            float halfH = h / 2f;

            drawSeg(canvas, (mask & 1) != 0, x + pad + t * 0.5f, y, x + w - pad - t * 0.5f, y + t, r);
            drawSeg(canvas, (mask & 2) != 0, x + w - t, y + pad + t * 0.4f, x + w, y + halfH - pad, r);
            drawSeg(canvas, (mask & 4) != 0, x + w - t, y + halfH + pad, x + w, y + h - pad - t * 0.4f, r);
            drawSeg(canvas, (mask & 8) != 0, x + pad + t * 0.5f, y + h - t, x + w - pad - t * 0.5f, y + h, r);
            drawSeg(canvas, (mask & 16) != 0, x, y + halfH + pad, x + t, y + h - pad - t * 0.4f, r);
            drawSeg(canvas, (mask & 32) != 0, x, y + pad + t * 0.4f, x + t, y + halfH - pad, r);
            drawSeg(canvas, (mask & 64) != 0, x + pad + t * 0.5f, y + halfH - t * 0.5f,
                    x + w - pad - t * 0.5f, y + halfH + t * 0.5f, r);
        }

        private void drawSeg(Canvas canvas, boolean on, float l, float t, float r, float b, float rad) {
            paint.setColor(on ? 0xfff8fafc : 0x1effffff);
            seg.set(l, t, r, b);
            canvas.drawRoundRect(seg, rad, rad, paint);
        }
    }
}
