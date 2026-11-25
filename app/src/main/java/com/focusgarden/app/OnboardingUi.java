package com.focusgarden.app;

import static com.focusgarden.app.GardenUi.*;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Permissions-first setup wizard and per-page theme-aware dashed Spotlight onboarding overlay.
 */
final class OnboardingUi {

    private static final int TOTAL_CORE_PERMS = 5;

    private OnboardingUi() {}

    record GuideStep(
            View target,
            int mascotRes,
            String title,
            String body,
            int cornerRadiusDp,
            int padDp) {}

    private record PermRenderSignature(KeepAliveManager.StatusSnapshot snap, boolean floatingBall) {}

    static FocusSheets.LiveRefresh showPermissionWizard(MainActivity activity, FocusState state,
                                                      Runnable onPickWallpaper, Runnable onChanged,
                                                      Runnable onFinished) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        FrameLayout backdrop = new FrameLayout(activity);
        int hPad = dp(activity, 20);
        backdrop.setPadding(hPad, dp(activity, 24), hPad, dp(activity, 24));

        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 24));
        card.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 16));
        elevate(card, 12);

        // Header with Q-version mascot
        LinearLayout header = row(activity);
        ImageView mascot = new ImageView(activity);
        mascot.setImageResource(R.drawable.guide_mascot_perm);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mascot, dp(activity, 64), dp(activity, 64));
        space(header, 12);

        LinearLayout titleCol = column(activity);
        titleCol.addView(text(activity, "先把后台守护打开吧～", 18, INK, true));
        gap(titleCol, 4);
        TextView subtitleTv = text(activity, "没开这些权限的话，小管家就看不见你在玩什么，也没法准时提醒和拦截哦！",
                12, INK_SUB, false);
        subtitleTv.setLineSpacing(dp(activity, 2), 1.08f);
        titleCol.addView(subtitleTv);
        weight(header, titleCol);
        card.addView(header);
        gap(card, 12);

        // Progress header + bar
        LinearLayout progRow = row(activity);
        TextView progBadge = text(activity, "核心权限 0 / 5", 12, BLUE, true);
        progBadge.setBackground(shape(activity, BLUE_SOFT, 10));
        progBadge.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        progRow.addView(progBadge);
        space(progRow, 8);
        TextView progHint = text(activity, "", 12, MUTED, false);
        weight(progRow, progHint);
        card.addView(progRow);
        gap(card, 8);

        CapsuleBar progBar = new CapsuleBar(activity);
        progBar.setColors(BLUE_SOFT, BLUE);
        card.addView(progBar, -1, dp(activity, 7));
        gap(card, 12);

        // Scrollable permission checklist
        int maxListH = Math.max(dp(activity, 160),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.45f));
        LinearLayout listBody = column(activity);
        ScrollView scroller = new BoundedScrollView(activity, maxListH);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(listBody, -1, -2);
        card.addView(scroller, -1, -2);
        gap(card, 14);

        // Pinned footer buttons
        TextView primaryBtn = primaryButton(activity, "去开启下一项");
        card.addView(primaryBtn, -1, -2);

        TextView skipBtn = text(activity, "暂不开启，先跳过", 13, MUTED, true);
        skipBtn.setGravity(Gravity.CENTER);
        skipBtn.setMinimumHeight(dp(activity, 40));
        skipBtn.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 4));
        card.addView(skipBtn, -1, -2);

        backdrop.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        dialog.setContentView(backdrop);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.CENTER);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(isDark() ? 0.65f : 0.46f);
        }

        final PermRenderSignature[] lastRendered = {null};
        final boolean[] finishedOnce = {false};
        final Dialog[] warnDialogRef = {null};
        final KeepAliveManager.StatusSnapshot[] latestSnap = {KeepAliveManager.cachedStatus(activity)};

        Runnable finishAndNotify = () -> {
            if (finishedOnce[0]) return;
            finishedOnce[0] = true;
            if (warnDialogRef[0] != null && warnDialogRef[0].isShowing()) warnDialogRef[0].dismiss();
            warnDialogRef[0] = null;
            state.markGuideCompleted(FocusState.GUIDE_PERMS);
            FocusStore.get(activity).save();
            if (dialog.isShowing()) dialog.dismiss();
            if (onFinished != null) onFinished.run();
        };

        Runnable requestSkip = () -> {
            int coreReady = countCoreReady(latestSnap[0]);
            if (coreReady >= TOTAL_CORE_PERMS) {
                finishAndNotify.run();
                return;
            }
            if (warnDialogRef[0] != null && warnDialogRef[0].isShowing()) return;
            warnDialogRef[0] = showSkipWarningDialog(activity, TOTAL_CORE_PERMS - coreReady,
                    () -> {
                        warnDialogRef[0] = null;
                        finishAndNotify.run();
                    },
                    () -> warnDialogRef[0] = null);
        };

        skipBtn.setOnClickListener(v -> {
            tapHaptic(v);
            requestSkip.run();
        });

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                requestSkip.run();
                return true;
            }
            return false;
        });

        java.util.function.Consumer<KeepAliveManager.StatusSnapshot> renderSnap = snap -> {
            latestSnap[0] = snap;
            PermRenderSignature sig = new PermRenderSignature(snap, state.floatingBall);
            if (sig.equals(lastRendered[0])) return;
            lastRendered[0] = sig;

            int coreReady = countCoreReady(snap);
            progBadge.setText("核心权限 " + coreReady + " / " + TOTAL_CORE_PERMS);
            progBadge.setTextColor(coreReady >= TOTAL_CORE_PERMS ? GREEN : BLUE);
            progBadge.setBackground(shape(activity, coreReady >= TOTAL_CORE_PERMS ? GREEN_SOFT : BLUE_SOFT, 10));
            progBar.setColors(coreReady >= TOTAL_CORE_PERMS ? GREEN_SOFT : BLUE_SOFT,
                    coreReady >= TOTAL_CORE_PERMS ? GREEN : BLUE);
            progBar.setProgress(coreReady / (float) TOTAL_CORE_PERMS);
            progHint.setText(coreReady >= TOTAL_CORE_PERMS ? "已全部就绪 ✓" : "开启后才能准确记录和拦截");

            listBody.removeAllViews();

            // Group 1: Core 5 permissions
            listBody.addView(text(activity, "核心必开权限（5 项）", 12, MUTED, true));
            gap(listBody, 6);
            LinearLayout coreBox = column(activity);
            coreBox.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
            coreBox.setPadding(dp(activity, 12), dp(activity, 2), dp(activity, 12), dp(activity, 2));

            coreBox.addView(permRow(activity, "无障碍服务",
                    "识别前台应用，超时或锁机时准时拦住分心", snap.accOk(),
                    activity::accessibilityNotice));
            addLine(coreBox);
            coreBox.addView(permRow(activity, "悬浮窗",
                    "在其他应用上方显示拦截页和顶部时间条", snap.overlayOk(),
                    () -> KeepAliveManager.openOverlaySettings(activity)));
            addLine(coreBox);
            coreBox.addView(permRow(activity, "使用量统计权限",
                    "精准记录学习与娱乐时长，不怕漏算", snap.usageOk(),
                    () -> KeepAliveManager.openUsageStatsSettings(activity)));
            addLine(coreBox);
            coreBox.addView(permRow(activity, "关闭省电优化",
                    "防止息屏或切后台后把小管家清理掉", snap.batteryOk(),
                    () -> KeepAliveManager.requestIgnoreBatteryOptimizations(activity)));
            addLine(coreBox);
            coreBox.addView(permRow(activity, "通知权限",
                    "保持后台守护通知常驻，运行更稳定", snap.notifOk(),
                    () -> KeepAliveManager.openNotificationSettings(activity)));
            listBody.addView(coreBox);
            gap(listBody, 12);

            // Group 2: Extra keep-alive enhancements
            listBody.addView(text(activity, "防杀强化（强烈建议）", 12, MUTED, true));
            gap(listBody, 6);
            LinearLayout guardBox = column(activity);
            guardBox.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
            guardBox.setPadding(dp(activity, 12), dp(activity, 2), dp(activity, 12), dp(activity, 2));

            guardBox.addView(permRow(activity, "保活壁纸",
                    "设为动态壁纸，降低国产系统杀后台概率", snap.wallpaperOk(),
                    onPickWallpaper));
            addLine(guardBox);
            guardBox.addView(floatingBallRow(activity, state, onChanged));
            addLine(guardBox);
            guardBox.addView(autostartRow(activity));
            listBody.addView(guardBox);

            // Dynamic primary button targeting next ungranted core item
            if (coreReady >= TOTAL_CORE_PERMS) {
                primaryBtn.setText("太棒啦！权限就绪，开始使用");
                primaryBtn.setBackground(touch(activity, GREEN, 14));
                primaryBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    finishAndNotify.run();
                });
                skipBtn.setVisibility(View.GONE);
            } else {
                String nextLabel;
                Runnable nextAction;
                if (!snap.accOk()) {
                    nextLabel = "无障碍服务";
                    nextAction = activity::accessibilityNotice;
                } else if (!snap.overlayOk()) {
                    nextLabel = "悬浮窗";
                    nextAction = () -> KeepAliveManager.openOverlaySettings(activity);
                } else if (!snap.usageOk()) {
                    nextLabel = "使用量统计权限";
                    nextAction = () -> KeepAliveManager.openUsageStatsSettings(activity);
                } else if (!snap.batteryOk()) {
                    nextLabel = "关闭省电优化";
                    nextAction = () -> KeepAliveManager.requestIgnoreBatteryOptimizations(activity);
                } else {
                    nextLabel = "通知权限";
                    nextAction = () -> KeepAliveManager.openNotificationSettings(activity);
                }
                primaryBtn.setText("去开启下一项：" + nextLabel);
                primaryBtn.setBackground(touch(activity, BLUE, 14));
                primaryBtn.setOnClickListener(v -> {
                    tapHaptic(v);
                    nextAction.run();
                });
                skipBtn.setVisibility(View.VISIBLE);
            }
        };

        Runnable triggerRefresh = () -> KeepAliveManager.refreshStatusAsync(activity, snap -> {
            if (dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed()) {
                renderSnap.accept(snap);
            }
        });

        renderSnap.accept(KeepAliveManager.cachedStatus(activity));
        if (!RedeemUi.reducedMotion(activity)) {
            card.setScaleX(0.95f);
            card.setScaleY(0.95f);
            card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        dialog.show();
        triggerRefresh.run();

        return new FocusSheets.LiveRefresh() {
            @Override public void refresh() {
                if (dialog.isShowing()) {
                    lastRendered[0] = null;
                    triggerRefresh.run();
                }
            }

            @Override public boolean isShowing() {
                return dialog.isShowing();
            }
        };
    }

    private static int countCoreReady(KeepAliveManager.StatusSnapshot snap) {
        if (snap == null) return 0;
        int n = 0;
        if (snap.accOk()) n++;
        if (snap.overlayOk()) n++;
        if (snap.usageOk()) n++;
        if (snap.batteryOk()) n++;
        if (snap.notifOk()) n++;
        return n;
    }

    private static Dialog showSkipWarningDialog(Activity activity, int missingCount,
                                                Runnable onConfirmSkip, Runnable onDismissed) {
        Dialog warn = new Dialog(activity);
        warn.requestWindowFeature(Window.FEATURE_NO_TITLE);
        warn.setCancelable(true);
        warn.setCanceledOnTouchOutside(true);

        FrameLayout backdrop = new FrameLayout(activity);
        backdrop.setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24));

        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 24));
        card.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        elevate(card, 12);

        LinearLayout top = row(activity);
        ImageView mascot = new ImageView(activity);
        mascot.setImageResource(R.drawable.guide_mascot_warn);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(mascot, dp(activity, 72), dp(activity, 72));
        space(top, 12);

        LinearLayout copy = column(activity);
        copy.addView(text(activity, "真的要现在跳过吗？", 17, INK, true));
        gap(copy, 5);
        TextView msg = text(activity,
                "还有 " + missingCount + " 项核心权限没开哦！不开启后台与监测权限，应用统计、超时拦截和早睡锁机都无法正常使用。\n\n之后也能随时在「设置 → 强力守护与系统」里补开～",
                13, INK_SUB, false);
        msg.setLineSpacing(dp(activity, 2), 1.1f);
        copy.addView(msg);
        weight(top, copy);
        card.addView(top);
        gap(card, 18);

        LinearLayout btnRow = row(activity);
        TextView skipAnyway = pillButton(activity, "仍要跳过", SURFACE_SUBTLE, INK_SUB, 13, 44);
        skipAnyway.setBackground(touchCard(activity, SURFACE_SUBTLE, BORDER, 13));
        skipAnyway.setOnClickListener(v -> {
            tapHaptic(v);
            warn.dismiss();
            if (onConfirmSkip != null) onConfirmSkip.run();
        });
        weight(btnRow, skipAnyway);
        space(btnRow, 10);

        TextView goBack = pillButton(activity, "回去开启", BLUE, ON_ACCENT, 13, 44);
        goBack.setOnClickListener(v -> {
            tapHaptic(v);
            warn.dismiss();
        });
        weight(btnRow, goBack);
        card.addView(btnRow);

        backdrop.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        warn.setContentView(backdrop);

        Window win = warn.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.CENTER);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.48f);
        }
        warn.setOnDismissListener(d -> {
            if (onDismissed != null) onDismissed.run();
        });
        warn.show();
        return warn;
    }

    private static LinearLayout permRow(Activity activity, String title, String desc,
                                        boolean granted, Runnable onClick) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        if (granted) {
            r.addView(iconView(activity, R.drawable.ic_check_circle, 20, 0));
        } else {
            View emptyCircle = new View(activity);
            emptyCircle.setBackground(cardBg(activity, CARD, mutedCircleBorder(), 10));
            r.addView(emptyCircle, dp(activity, 20), dp(activity, 20));
        }
        space(r, 10);

        LinearLayout copy = column(activity);
        copy.addView(text(activity, title, 14, INK, true));
        TextView sub = text(activity, desc, 11, MUTED, false);
        copy.addView(sub);
        weight(r, copy);
        space(r, 8);

        TextView badge = text(activity, granted ? "已开启" : "去开启", 11, granted ? GREEN : BLUE, true);
        badge.setSingleLine(true);
        badge.setBackground(shape(activity, granted ? GREEN_SOFT : BLUE_SOFT, 8));
        badge.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));
        r.addView(badge);

        r.setOnClickListener(v -> {
            tapHaptic(v);
            onClick.run();
        });
        return r;
    }

    private static LinearLayout floatingBallRow(Activity activity, FocusState state, Runnable onChanged) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        LinearLayout copy = column(activity);
        copy.addView(text(activity, "打开悬浮小球", 14, INK, true));
        copy.addView(text(activity, "边缘微透明小球保活（需先开悬浮窗）", 11, MUTED, false));
        weight(r, copy);
        space(r, 8);

        SwitchView sw = new SwitchView(activity, state.floatingBall);
        sw.setContentDescription("打开悬浮小球");
        sw.setOnToggleListener(on -> {
            if (on && !KeepAliveManager.hasOverlayPermission(activity)) {
                haptic(sw, state.hapticFeedback, HapticKind.REJECT);
                sw.setChecked(false, true);
                Toast.makeText(activity, "请先开启上方的「悬浮窗」权限哦～", Toast.LENGTH_SHORT).show();
                KeepAliveManager.openOverlaySettings(activity);
                return;
            }
            state.floatingBall = on;
            FocusStore.get(activity).save();
            KeepAliveManager.syncServiceState(activity);
            if (onChanged != null) onChanged.run();
        });
        r.addView(sw);
        r.setOnClickListener(v -> sw.performClick());
        return r;
    }

    private static LinearLayout autostartRow(Activity activity) {
        LinearLayout r = row(activity);
        r.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        LinearLayout copy = column(activity);
        copy.addView(text(activity, "打开自启动", 14, INK, true));
        copy.addView(text(activity, "小米/华为/OPPO/vivo 建议开启并锁定后台", 11, MUTED, false));
        weight(r, copy);
        space(r, 8);

        TextView go = text(activity, "去设置", 11, BLUE, true);
        go.setSingleLine(true);
        go.setBackground(shape(activity, BLUE_SOFT, 8));
        go.setPadding(dp(activity, 9), dp(activity, 4), dp(activity, 9), dp(activity, 4));
        r.addView(go);
        r.setOnClickListener(v -> {
            tapHaptic(v);
            KeepAliveManager.openAutostartSettings(activity);
        });
        return r;
    }

    private static void addLine(LinearLayout parent) {
        View line = new View(parent.getContext());
        line.setBackgroundColor(LINE);
        parent.addView(line, -1, dp(parent.getContext(), 1));
    }

    private static final class BoundedScrollView extends ScrollView {
        private final int maxHeightPx;

        BoundedScrollView(Context context, int maxHeightPx) {
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

    // =========================================================================
    // Static Theme-Aware Dashed Spotlight Overlay
    // =========================================================================
    static final class SpotlightGuideView extends FrameLayout {
        private final FrameLayout host;
        private final ScrollView scroller;
        private final List<GuideStep> steps;
        private final Runnable onFinish;

        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path scrimPath = new Path();
        private final RectF cutoutRect = new RectF();
        private final RectF dashRect = new RectF();

        private final LinearLayout card;
        private final ImageView mascotImg;
        private final TextView stepBadgeTv;
        private final TextView titleTv;
        private final TextView bodyTv;
        private final TextView skipTv;
        private final TextView nextBtn;

        private int currentIndex;
        private int currentRadiusPx;
        private boolean hasTargetRect;
        private int layoutRetryCount;
        private boolean finished;

        private SpotlightGuideView(FrameLayout host, ScrollView scroller,
                                   List<GuideStep> steps, Runnable onFinish) {
            super(host.getContext());
            this.host = host;
            this.scroller = scroller;
            this.steps = steps;
            this.onFinish = onFinish;

            Context ctx = host.getContext();
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);

            scrimPaint.setStyle(Paint.Style.FILL);
            scrimPaint.setColor(isDark() ? 0xCC060911 : 0xAD0E1418);

            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(dp(ctx, 2.5f));
            dashPaint.setStrokeCap(Paint.Cap.ROUND);
            dashPaint.setColor(isDark() ? BLUE : BLUE_LIGHT);
            dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(ctx, 7f), dp(ctx, 5f)}, 0f));

            card = column(ctx);
            GradientDrawable cardBgDrawable = new GradientDrawable();
            cardBgDrawable.setColor(CARD);
            cardBgDrawable.setCornerRadius(dp(ctx, 20));
            cardBgDrawable.setStroke(dp(ctx, 1.5f), blueStroke());
            card.setBackground(cardBgDrawable);
            card.setPadding(dp(ctx, 15), dp(ctx, 15), dp(ctx, 15), dp(ctx, 14));
            elevate(card, 8);

            LinearLayout topRow = row(ctx);
            mascotImg = new ImageView(ctx);
            mascotImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mascotImg.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            topRow.addView(mascotImg, dp(ctx, 74), dp(ctx, 74));
            space(topRow, 12);

            LinearLayout copyCol = column(ctx);
            LinearLayout titleLine = row(ctx);
            stepBadgeTv = text(ctx, "", 11, BLUE, true);
            stepBadgeTv.setSingleLine(true);
            stepBadgeTv.setBackground(shape(ctx, BLUE_SOFT, 8));
            stepBadgeTv.setPadding(dp(ctx, 7), dp(ctx, 2), dp(ctx, 7), dp(ctx, 2));
            titleLine.addView(stepBadgeTv);
            space(titleLine, 6);

            titleTv = text(ctx, "", 15, INK, true);
            weight(titleLine, titleTv);
            copyCol.addView(titleLine);
            gap(copyCol, 6);

            bodyTv = text(ctx, "", 13, INK_SUB, false);
            bodyTv.setLineSpacing(dp(ctx, 2.5f), 1.08f);
            copyCol.addView(bodyTv);
            weight(topRow, copyCol);
            card.addView(topRow);
            gap(card, 12);

            LinearLayout actions = row(ctx);
            skipTv = text(ctx, "跳过引导", 13, MUTED, true);
            skipTv.setGravity(Gravity.CENTER_VERTICAL);
            skipTv.setMinimumHeight(dp(ctx, 36));
            skipTv.setPadding(dp(ctx, 6), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
            skipTv.setOnClickListener(v -> {
                tapHaptic(v);
                dismiss();
            });
            actions.addView(skipTv);

            View spacer = new View(ctx);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

            nextBtn = pillButton(ctx, "下一步", BLUE, ON_ACCENT, 13, 38);
            nextBtn.setPadding(dp(ctx, 18), dp(ctx, 6), dp(ctx, 18), dp(ctx, 6));
            nextBtn.setOnClickListener(v -> {
                tapHaptic(v);
                advanceOrFinish();
            });
            actions.addView(nextBtn, -2, -2);
            card.addView(actions);

            card.setVisibility(View.INVISIBLE);
            addView(card, new FrameLayout.LayoutParams(-1, -2));
        }

        static SpotlightGuideView show(FrameLayout host, ScrollView scroller,
                                       List<GuideStep> rawSteps, Runnable onFinish) {
            return show(host, scroller, rawSteps, 0, onFinish);
        }

        static SpotlightGuideView show(FrameLayout host, ScrollView scroller,
                                       List<GuideStep> rawSteps, int initialStep, Runnable onFinish) {
            if (host == null || rawSteps == null || rawSteps.isEmpty()) {
                if (onFinish != null) onFinish.run();
                return null;
            }
            List<GuideStep> valid = new ArrayList<>();
            for (GuideStep s : rawSteps) {
                if (s != null && s.target() != null && s.target().isAttachedToWindow()
                        && s.target().getVisibility() == View.VISIBLE) {
                    valid.add(s);
                }
            }
            if (valid.isEmpty()) {
                if (onFinish != null) onFinish.run();
                return null;
            }
            int clampedStart = Math.max(0, Math.min(initialStep, valid.size() - 1));
            SpotlightGuideView view = new SpotlightGuideView(host, scroller, valid, onFinish);
            host.addView(view, new FrameLayout.LayoutParams(-1, -1));
            view.requestFocus();
            host.post(() -> view.showStep(clampedStart, true));
            return view;
        }

        int currentStepIndex() {
            return currentIndex;
        }

        void dismiss() {
            if (finished) return;
            finished = true;
            detachFromHost();
            if (onFinish != null) onFinish.run();
        }

        void dismissWithoutFinish() {
            if (finished) return;
            finished = true;
            detachFromHost();
        }

        private void detachFromHost() {
            ViewParent parent = getParent();
            if (parent instanceof ViewGroup vg) vg.removeView(this);
        }

        private void advanceOrFinish() {
            int nextIdx = currentIndex + 1;
            while (nextIdx < steps.size()) {
                View t = steps.get(nextIdx).target();
                if (t != null && t.isAttachedToWindow() && t.getVisibility() == View.VISIBLE) break;
                nextIdx++;
            }
            if (nextIdx >= steps.size()) {
                dismiss();
            } else {
                showStep(nextIdx, true);
            }
        }

        private void showStep(int index, boolean allowScroll) {
            if (finished || index < 0 || index >= steps.size()) {
                dismiss();
                return;
            }
            currentIndex = index;
            layoutRetryCount = 0;
            GuideStep step = steps.get(index);

            mascotImg.setImageResource(step.mascotRes());
            stepBadgeTv.setText(steps.size() > 1 ? (index + 1) + " / " + steps.size() : "小提示");
            titleTv.setText(step.title());
            bodyTv.setText(step.body());
            nextBtn.setText(index + 1 < steps.size() ? "下一步" : "我知道啦！");
            skipTv.setVisibility(steps.size() > 1 ? View.VISIBLE : View.INVISIBLE);

            positionCurrentStep(allowScroll);
        }

        private void positionCurrentStep(boolean allowScroll) {
            if (finished || !isAttachedToWindow()) return;
            GuideStep step = steps.get(currentIndex);
            View target = step.target();
            if (target == null || !target.isAttachedToWindow() || target.getVisibility() != View.VISIBLE) {
                advanceOrFinish();
                return;
            }
            int hostW = getWidth() > 0 ? getWidth() : host.getWidth();
            int hostH = getHeight() > 0 ? getHeight() : host.getHeight();
            if (hostW <= 0 || hostH <= 0 || target.getWidth() <= 0 || target.getHeight() <= 0) {
                if (layoutRetryCount++ < 8) post(() -> positionCurrentStep(allowScroll));
                else dismiss();
                return;
            }

            Context ctx = getContext();
            if (allowScroll && scroller != null && isDescendantOf(target, scroller)) {
                int[] scrollerLoc = new int[2];
                int[] targetLoc = new int[2];
                scroller.getLocationInWindow(scrollerLoc);
                target.getLocationInWindow(targetLoc);
                int scrollerH = scroller.getHeight();
                int relTop = targetLoc[1] - scrollerLoc[1];
                int relBottom = relTop + target.getHeight();
                int safeTop = dp(ctx, 78);
                int safeBottom = Math.max(safeTop + dp(ctx, 40), scrollerH - dp(ctx, 56));
                if (relTop < safeTop || relBottom > safeBottom) {
                    View contentChild = scroller.getChildCount() > 0 ? scroller.getChildAt(0) : null;
                    int maxScroll = contentChild == null ? 0 : Math.max(0, contentChild.getHeight() - scrollerH);
                    int contentTop = scroller.getScrollY() + relTop;
                    int desiredScroll = Math.max(0, Math.min(maxScroll, contentTop - dp(ctx, 92)));
                    if (Math.abs(desiredScroll - scroller.getScrollY()) > dp(ctx, 4)) {
                        scroller.scrollTo(0, desiredScroll);
                        post(() -> positionCurrentStep(false));
                        return;
                    }
                }
            }

            int[] hostLoc = new int[2];
            int[] targetLoc = new int[2];
            host.getLocationInWindow(hostLoc);
            target.getLocationInWindow(targetLoc);

            float pad = dp(ctx, step.padDp());
            float left = targetLoc[0] - hostLoc[0] - pad;
            float top = targetLoc[1] - hostLoc[1] - pad;
            float right = left + target.getWidth() + pad * 2f;
            float bottom = top + target.getHeight() + pad * 2f;

            left = Math.max(dp(ctx, 4), left);
            top = Math.max(dp(ctx, 4), top);
            right = Math.min(hostW - dp(ctx, 4), right);
            bottom = Math.min(hostH - dp(ctx, 4), bottom);

            cutoutRect.set(left, top, right, bottom);
            float dashOut = dp(ctx, 2.5f);
            dashRect.set(left - dashOut, top - dashOut, right + dashOut, bottom + dashOut);
            currentRadiusPx = dp(ctx, step.cornerRadiusDp());
            hasTargetRect = cutoutRect.width() > 0 && cutoutRect.height() > 0;

            int sideMargin = dp(ctx, 16);
            int cardMaxW = Math.max(dp(ctx, 200), hostW - sideMargin * 2);
            card.measure(
                    MeasureSpec.makeMeasureSpec(cardMaxW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.AT_MOST));
            int cardH = card.getMeasuredHeight();

            int topSafe = dp(ctx, 36);
            int bottomSafe = dp(ctx, 24);
            int gapPx = dp(ctx, 14);

            float spaceBelow = (hostH - bottomSafe) - cutoutRect.bottom;
            float spaceAbove = cutoutRect.top - topSafe;

            int cardTop;
            if (spaceBelow >= cardH + gapPx || spaceBelow >= spaceAbove) {
                cardTop = Math.round(cutoutRect.bottom + gapPx);
                cardTop = Math.min(cardTop, hostH - bottomSafe - cardH);
            } else {
                cardTop = Math.round(cutoutRect.top - gapPx - cardH);
            }
            cardTop = Math.max(topSafe, Math.min(cardTop, Math.max(topSafe, hostH - bottomSafe - cardH)));

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.width = cardMaxW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = sideMargin;
            lp.topMargin = cardTop;
            card.setLayoutParams(lp);
            card.setVisibility(View.VISIBLE);
            invalidate();
        }

        private static boolean isDescendantOf(View child, View parent) {
            ViewParent p = child.getParent();
            while (p != null) {
                if (p == parent) return true;
                p = p.getParent();
            }
            return false;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            scrimPath.reset();
            scrimPath.setFillType(Path.FillType.EVEN_ODD);
            scrimPath.addRect(0f, 0f, w, h, Path.Direction.CW);
            if (hasTargetRect) {
                scrimPath.addRoundRect(cutoutRect, currentRadiusPx, currentRadiusPx, Path.Direction.CW);
            }
            canvas.drawPath(scrimPath, scrimPaint);

            if (hasTargetRect) {
                float dashRadius = currentRadiusPx + dp(getContext(), 2);
                canvas.drawRoundRect(dashRect, dashRadius, dashRadius, dashPaint);
            }
        }
    }
}
