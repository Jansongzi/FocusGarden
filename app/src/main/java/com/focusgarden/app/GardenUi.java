package com.focusgarden.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

final class GardenUi {
    static final int PAPER = 0xfff4f6fa;
    static final int CARD = 0xffffffff;
    static final int SURFACE_SUBTLE = 0xfff7f9fc;
    static final int BORDER = 0xffe8eef5;
    static final int LINE = 0xffedf1f7;

    static final int INK = 0xff151d2b;
    static final int INK_SUB = 0xff5c697c;
    static final int MUTED = 0xff8a96a8;

    static final int BLUE = 0xff1a6bff;
    static final int BLUE_LIGHT = 0xff3b84ff;
    static final int BLUE_DARK = 0xff1153d6;
    static final int BLUE_SOFT = 0xffeaf2ff;
    static final int BLUE_DISABLED_BG = 0xffeff3f9;
    static final int BLUE_DISABLED_TEXT = 0xff9aa7bc;

    static final int GREEN = 0xff16b364;
    static final int GREEN_SOFT = 0xffe8f8f0;
    static final int RED = 0xfff04438;
    static final int RED_SOFT = 0xfffeeceb;
    static final int AMBER = 0xfff59e0b;
    static final int AMBER_SOFT = 0xfffef5e4;
    static final int PURPLE = 0xff7c5cff;
    static final int PURPLE_SOFT = 0xfff1eeff;
    static final int CYAN = 0xff0ea5e9;
    static final int CYAN_SOFT = 0xffe6f6fe;

    // Legacy aliases preserved for internal compatibility
    static final int FOREST = 0xff1346b8;
    static final int MINT = BLUE_SOFT;
    static final int LIME = 0xffd7e8ff;
    static final int PALE = 0xffd8e6ff;
    static final int GOLD = AMBER;
    static final int SAGE = GREEN;
    static final int SAND = AMBER_SOFT;

    static int dp(Context context, float size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }

    static LinearLayout column(Context context) {
        LinearLayout result = new LinearLayout(context);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    static LinearLayout row(Context context) {
        LinearLayout result = new LinearLayout(context);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }

    static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    static GradientDrawable shape(Context context, int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    static GradientDrawable cardBg(Context context, int color, int strokeColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radiusDp));
        if (strokeColor != 0) shape.setStroke(dp(context, 1), strokeColor);
        return shape;
    }

    static GradientDrawable gradientBg(Context context, int startColor, int endColor, int radiusDp) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{startColor, endColor});
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    static void elevate(View view, float elevationDp) {
        view.setElevation(dp(view.getContext(), elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            view.setOutlineAmbientShadowColor(0x1a152849);
            view.setOutlineSpotShadowColor(0x24152849);
        }
    }

    static RippleDrawable touch(Context context, int color, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x201a6bff),
                shape(context, color, radiusDp), null);
    }

    static RippleDrawable touchCard(Context context, int fillColor, int strokeColor, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x1a1a6bff),
                cardBg(context, fillColor, strokeColor, radiusDp), null);
    }

    static FrameLayout artwork(Context context, int radius) {
        var frame = new FrameLayout(context);
        frame.setBackground(shape(context, 0xff153a31, radius));
        frame.setClipToOutline(true);
        var art = new ImageView(context);
        art.setImageResource(R.drawable.pond_at_dusk);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(art, -1, -1);
        return frame;
    }

    static TextView pillButton(Context context, String label, int bg, int textColor, int radiusDp, int heightDp) {
        TextView v = text(context, label, 14, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(context, heightDp));
        v.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        v.setBackground(touch(context, bg, radiusDp));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setFocusable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        return v;
    }

    static TextView button(Context context, String title, boolean primary) {
        return pillButton(context, title, primary ? BLUE : BLUE_SOFT, primary ? CARD : BLUE, 14, 44);
    }

    static TextView primaryButton(Context context, String title) {
        return pillButton(context, title, BLUE, CARD, 14, 46);
    }

    static TextView secondaryButton(Context context, String title) {
        return pillButton(context, title, BLUE_SOFT, BLUE, 14, 46);
    }

    static TextView dangerButton(Context context, String title) {
        return pillButton(context, title, RED_SOFT, RED, 14, 44);
    }

    static TextView redeemPill(Context context, String label, boolean active) {
        TextView v = text(context, label, 13, active ? CARD : BLUE_DISABLED_TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(context, 38));
        v.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        v.setBackground(active
                ? new RippleDrawable(ColorStateList.valueOf(0x30ffffff), shape(context, BLUE, 12), null)
                : shape(context, BLUE_DISABLED_BG, 12));
        v.setSingleLine(true);
        return v;
    }

    static ImageView iconView(Context context, int drawableRes, int sizeDp, int tint) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawableRes);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tint != 0) view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int px = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return view;
    }

    static FrameLayout iconBadge(Context context, int drawableRes, int bgColor, int tintColor, int boxDp, int iconDp, int cornerDp) {
        FrameLayout box = new FrameLayout(context);
        box.setBackground(shape(context, bgColor, cornerDp));
        ImageView icon = new ImageView(context);
        icon.setImageResource(drawableRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintColor != 0) icon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconPx = dp(context, iconDp);
        box.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));
        int boxPx = dp(context, boxDp);
        box.setLayoutParams(new LinearLayout.LayoutParams(boxPx, boxPx));
        return box;
    }

    static FrameLayout appIcon(Context context, String pkg, int sizeDp, int radiusDp) {
        FrameLayout holder = new FrameLayout(context);
        holder.setBackground(cardBg(context, SURFACE_SUBTLE, BORDER, radiusDp));
        holder.setClipToOutline(true);
        holder.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(context, radiusDp));
            }
        });
        ImageView img = new ImageView(context);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            Drawable d = context.getPackageManager().getApplicationIcon(pkg);
            img.setImageDrawable(d);
        } catch (PackageManager.NameNotFoundException e) {
            img.setImageResource(R.drawable.ic_focus);
        }
        holder.addView(img, new FrameLayout.LayoutParams(-1, -1));
        int px = dp(context, sizeDp);
        holder.setLayoutParams(new LinearLayout.LayoutParams(px, px));
        return holder;
    }

    static EditText styledInput(Context context, String hint, String initial, int inputType) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setTextSize(15);
        input.setInputType(inputType);
        input.setSingleLine(true);
        if (initial != null) input.setText(initial);
        input.setBackground(cardBg(context, SURFACE_SUBTLE, 0xffd9e2ef, 14));
        input.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        return input;
    }

    static LinearLayout chipRow(Context context, String[] labels, int selectedIndex, IntConsumer listener) {
        LinearLayout row = row(context);
        for (int i = 0; i < labels.length; i++) {
            int idx = i;
            boolean active = i == selectedIndex;
            TextView chip = text(context, labels[i], 13, active ? BLUE : INK_SUB, active);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(context, 36));
            chip.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
            chip.setBackground(touchCard(context, active ? BLUE_SOFT : SURFACE_SUBTLE,
                    active ? BLUE : BORDER, 12));
            chip.setOnClickListener(v -> {
                tapHaptic(v);
                listener.accept(idx);
            });
            weight(row, chip);
            if (i + 1 < labels.length) space(row, 8);
        }
        return row;
    }

    static Dialog bottomSheet(Activity activity, String title, String subtitle, View body) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = column(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        float r = dp(activity, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        // Top drag handle
        View handle = new View(activity);
        handle.setBackground(shape(activity, 0xffd5dde9, 3));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(activity, 10);
        handleLp.bottomMargin = dp(activity, 10);
        sheet.addView(handle, handleLp);

        // Header
        LinearLayout head = row(activity);
        head.setPadding(dp(activity, 20), dp(activity, 2), dp(activity, 16), dp(activity, 12));
        LinearLayout titles = column(activity);
        titles.addView(text(activity, title, 19, INK, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            gap(titles, 3);
            titles.addView(text(activity, subtitle, 12, MUTED, false));
        }
        weight(head, titles);

        FrameLayout close = iconBadge(activity, R.drawable.ic_close, SURFACE_SUBTLE, INK_SUB, 32, 16, 16);
        close.setContentDescription("关闭");
        close.setOnClickListener(v -> {
            tapHaptic(v);
            dialog.dismiss();
        });
        head.addView(close);
        sheet.addView(head);

        View sep = new View(activity);
        sep.setBackgroundColor(LINE);
        sheet.addView(sep, -1, dp(activity, 1));

        int maxH = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.88f);
        int maxScrollH = Math.max(dp(activity, 200), maxH - dp(activity, 92));
        ScrollView scroller = new MaxHeightScrollView(activity, maxScrollH);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout wrap = column(activity);
        wrap.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 24));
        wrap.addView(body, -1, -2);
        scroller.addView(wrap, -1, -2);

        sheet.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
        dialog.setContentView(sheet);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.BOTTOM);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            win.setDimAmount(0.42f);
        }
        sheet.setTranslationY(dp(activity, 20));
        sheet.setAlpha(0.92f);
        sheet.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        dialog.show();
        return dialog;
    }

    static Dialog cardDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                             String title, String message, View extraContent,
                             String cancelLabel, String confirmLabel, boolean danger,
                             boolean cancelable, Runnable onConfirm) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        FrameLayout backdrop = new FrameLayout(activity);
        int hPad = dp(activity, 24);
        backdrop.setPadding(hPad, dp(activity, 24), hPad, dp(activity, 24));

        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 24));
        card.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        elevate(card, 12);

        LinearLayout header = row(activity);
        if (iconRes != 0) {
            header.addView(iconBadge(activity, iconRes, badgeBg, badgeTint, 38, 20, 12));
            space(header, 12);
        }
        TextView titleView = text(activity, title, 18, INK, true);
        weight(header, titleView);
        card.addView(header);

        int maxContentH = Math.max(dp(activity, 120),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.46f));
        LinearLayout contentWrap = column(activity);
        if (message != null && !message.isEmpty()) {
            gap(contentWrap, 12);
            TextView msgView = text(activity, message, 14, INK_SUB, false);
            msgView.setLineSpacing(dp(activity, 2), 1.12f);
            contentWrap.addView(msgView, -1, -2);
        }
        if (extraContent != null) {
            gap(contentWrap, 12);
            contentWrap.addView(extraContent, -1, -2);
        }
        if (contentWrap.getChildCount() > 0) {
            ScrollView scroller = new MaxHeightScrollView(activity, maxContentH);
            scroller.setVerticalScrollBarEnabled(false);
            scroller.addView(contentWrap, -1, -2);
            card.addView(scroller, -1, -2);
        }

        if (cancelLabel != null || confirmLabel != null) {
            gap(card, 18);
            LinearLayout btnRow = row(activity);
            if (cancelLabel != null) {
                TextView cancelBtn = pillButton(activity, cancelLabel, SURFACE_SUBTLE, INK_SUB, 13, 44);
                cancelBtn.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 13));
                cancelBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    dialog.dismiss();
                });
                weight(btnRow, cancelBtn);
                if (confirmLabel != null) space(btnRow, 10);
            }
            if (confirmLabel != null) {
                TextView okBtn = pillButton(activity, confirmLabel,
                        danger ? RED : BLUE, CARD, 13, 44);
                okBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    dialog.dismiss();
                    if (onConfirm != null) onConfirm.run();
                });
                weight(btnRow, okBtn);
            }
            card.addView(btnRow);
        }

        backdrop.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        dialog.setContentView(backdrop);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.CENTER);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setDimAmount(0.45f);
        }

        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setAlpha(0f);
        card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
        dialog.show();
        return dialog;
    }

    static Dialog infoDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                             String title, String message, String okLabel) {
        return cardDialog(activity, iconRes, badgeBg, badgeTint, title, message, null,
                null, okLabel == null ? "知道了" : okLabel, false, true, null);
    }

    static Dialog confirmDialog(Activity activity, int iconRes, int badgeBg, int badgeTint,
                                String title, String message, String cancelLabel,
                                String confirmLabel, boolean danger, Runnable onConfirm) {
        return cardDialog(activity, iconRes, badgeBg, badgeTint, title, message, null,
                cancelLabel == null ? "取消" : cancelLabel, confirmLabel, danger, true, onConfirm);
    }

    static Dialog loadingDialog(Activity activity, String title, String subtitle) {
        LinearLayout extra = row(activity);
        extra.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 14));
        extra.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        android.widget.ProgressBar spinner = new android.widget.ProgressBar(activity);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(BLUE));
        extra.addView(spinner, dp(activity, 26), dp(activity, 26));
        space(extra, 12);
        weight(extra, text(activity, subtitle == null || subtitle.isEmpty() ? "请稍候…" : subtitle,
                14, INK_SUB, false));
        return cardDialog(activity, R.drawable.ic_info, BLUE_SOFT, BLUE,
                title, null, extra, null, null, false, false, null);
    }

    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeightPx;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            int cap = (mode == MeasureSpec.UNSPECIFIED) ? maxHeightPx : Math.min(size, maxHeightPx);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    static void gap(LinearLayout parent, int height) {
        parent.addView(new View(parent.getContext()), 1, dp(parent.getContext(), height));
    }

    static void space(LinearLayout parent, int width) {
        parent.addView(new View(parent.getContext()), dp(parent.getContext(), width), 1);
    }

    static void weight(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    static String shortMinutes(long ms) {
        return String.valueOf(Math.max(0, ms / FocusState.MINUTE));
    }

    static String duration(long ms) {
        long minutes = Math.max(0, ms / FocusState.MINUTE);
        if (minutes == 0 && ms > 0) return (ms + 999) / 1000 + " 秒";
        return minutes >= 60 ? minutes / 60 + " 小时 " + minutes % 60 + " 分" : minutes + " 分钟";
    }

    static String countdown(long ms) {
        long sec = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }

    static String clock(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    enum HapticKind { TAP, NAV, SWITCH, CONFIRM, REJECT }

    private static long lastHapticMs;
    private static HapticKind lastHapticKind;

    private static int hapticPriority(HapticKind kind) {
        if (kind == null) return 0;
        return (kind == HapticKind.CONFIRM || kind == HapticKind.REJECT) ? 2 : 1;
    }

    static void tapHaptic(View view) {
        if (view != null) {
            haptic(view, FocusStore.get(view.getContext()).state.hapticFeedback, HapticKind.TAP);
        }
    }

    static void haptic(View view, boolean enabled, HapticKind kind) {
        if (!enabled || view == null || kind == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastHapticMs < 45L && hapticPriority(kind) <= hapticPriority(lastHapticKind)) return;
        lastHapticMs = now;
        lastHapticKind = kind;
        try {
            Context ctx = view.getContext();
            Vibrator vib = null;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = ctx.getSystemService(VibratorManager.class);
                if (vm != null) vib = vm.getDefaultVibrator();
            } else {
                vib = ctx.getSystemService(Vibrator.class);
            }
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 29) {
                    int effectId = switch (kind) {
                        case CONFIRM -> VibrationEffect.EFFECT_CLICK;
                        case REJECT -> VibrationEffect.EFFECT_DOUBLE_CLICK;
                        case TAP, NAV, SWITCH -> VibrationEffect.EFFECT_TICK;
                    };
                    vib.vibrate(VibrationEffect.createPredefined(effectId));
                } else {
                    long ms = switch (kind) {
                        case CONFIRM -> 14L;
                        case REJECT -> 22L;
                        case NAV, SWITCH -> 8L;
                        case TAP -> 6L;
                    };
                    int amp = switch (kind) {
                        case CONFIRM -> 80;
                        case REJECT -> 110;
                        case NAV, SWITCH -> 40;
                        case TAP -> 36;
                    };
                    vib.vibrate(VibrationEffect.createOneShot(ms, amp));
                }
                return;
            }
            view.performHapticFeedback(kind == HapticKind.CONFIRM
                    ? HapticFeedbackConstants.CONTEXT_CLICK
                    : HapticFeedbackConstants.CLOCK_TICK);
        } catch (Exception ignored) {}
    }

    static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private boolean checked;
        private float thumbPos;
        private ValueAnimator animator;
        private java.util.function.Consumer<Boolean> listener;

        SwitchView(Context context, boolean initial) {
            super(context);
            this.checked = initial;
            this.thumbPos = initial ? 1f : 0f;
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> {
                boolean target = !checked;
                setChecked(target, true);
                if (listener != null) listener.accept(target);
                if (checked == target) {
                    haptic(this, FocusStore.get(getContext()).state.hapticFeedback, HapticKind.SWITCH);
                }
            });
        }

        void setOnToggleListener(java.util.function.Consumer<Boolean> listener) {
            this.listener = listener;
        }

        void setChecked(boolean value, boolean animate) {
            if (this.checked == value && ((value && thumbPos == 1f) || (!value && thumbPos == 0f))) return;
            this.checked = value;
            if (animator != null) animator.cancel();
            if (!animate) {
                thumbPos = value ? 1f : 0f;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(thumbPos, value ? 1f : 0f);
            animator.setDuration(180);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                thumbPos = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(getContext(), 48), dp(getContext(), 28));
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float pad = dp(getContext(), 3);
            float radius = h / 2f;
            track.set(0, 0, w, h);

            paint.setColor(blend(0xffdce4f0, BLUE, thumbPos));
            canvas.drawRoundRect(track, radius, radius, paint);

            float thumbR = (h - pad * 2f) / 2f;
            float minX = pad + thumbR;
            float maxX = w - pad - thumbR;
            float cx = minX + (maxX - minX) * thumbPos;
            float cy = h / 2f;

            paint.setColor(0x1f000000);
            canvas.drawCircle(cx, cy + dp(getContext(), 1), thumbR, paint);
            paint.setColor(CARD);
            canvas.drawCircle(cx, cy, thumbR, paint);
        }

        private static int blend(int c1, int c2, float t) {
            int a = Math.round(((c1 >> 24) & 0xff) * (1 - t) + ((c2 >> 24) & 0xff) * t);
            int r = Math.round(((c1 >> 16) & 0xff) * (1 - t) + ((c2 >> 16) & 0xff) * t);
            int g = Math.round(((c1 >> 8) & 0xff) * (1 - t) + ((c2 >> 8) & 0xff) * t);
            int b = Math.round((c1 & 0xff) * (1 - t) + (c2 & 0xff) * t);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    static final class CapsuleBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float progress;
        private int trackColor = LINE;
        private int fillColor = BLUE;

        CapsuleBar(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColors(int trackColor, int fillColor) {
            if (this.trackColor == trackColor && this.fillColor == fillColor) return;
            this.trackColor = trackColor;
            this.fillColor = fillColor;
            invalidate();
        }

        void setProgress(float progress) {
            float clamped = Math.max(0f, Math.min(1f, progress));
            if (clamped == this.progress) return;
            this.progress = clamped;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            paint.setColor(trackColor);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, paint);
            if (progress > 0f) {
                float fillW = Math.max(h, w * progress);
                paint.setColor(fillColor);
                rect.set(0, 0, Math.min(w, fillW), h);
                canvas.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    static final class HeartbeatStripView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF slot = new RectF();
        private int[] buckets = new int[48];

        HeartbeatStripView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setBuckets(int[] data) {
            if (data != null && data.length == 48) {
                this.buckets = data;
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0 || buckets.length == 0) return;
            int n = buckets.length;
            float step = w / n;
            float barW = Math.max(2f, step * 0.54f);
            float offset = (step - barW) * 0.5f;
            float radius = barW * 0.45f;
            for (int i = 0; i < n; i++) {
                int state = buckets[i];
                paint.setColor(state == 2 ? BLUE : state == 1 ? 0xfff5c52c : 0xffe4ebf4);
                float left = i * step + offset;
                slot.set(left, 0, left + barW, h);
                canvas.drawRoundRect(slot, radius, radius, paint);
            }
        }
    }
}
