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
import android.text.Layout;
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
import java.util.Locale;

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

    static FocusSheets.LiveRefresh showDisclaimerDialog(MainActivity activity, FocusState state,
                                                        Runnable onAccepted) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        FrameLayout backdrop = new FrameLayout(activity);
        int hPad = dp(activity, 20);
        backdrop.setPadding(hPad, dp(activity, 24), hPad, dp(activity, 24));

        LinearLayout card = column(activity);
        card.setBackground(cardBg(activity, CARD, BORDER, 24));
        card.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        elevate(card, 12);

        LinearLayout header = row(activity);
        header.addView(iconBadge(activity, R.drawable.ic_info, BLUE_SOFT, BLUE, 36, 18, 11));
        space(header, 10);
        header.addView(text(activity, "开源及版权声明", 18, INK, true));
        card.addView(header);
        gap(card, 12);

        FrameLayout scrollBox = new FrameLayout(activity);
        scrollBox.setBackground(cardBg(activity, SURFACE_SUBTLE, BORDER, 16));
        scrollBox.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 10), dp(activity, 12));

        int viewH = Math.max(dp(activity, 165),
                Math.min(dp(activity, 225),
                        Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.25f)));
        ScrollView scroller = new ScrollView(activity);
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setScrollbarFadingEnabled(false);

        LinearLayout content = column(activity);
        content.setPadding(0, dp(activity, 2), dp(activity, 6), dp(activity, 6));
        String[] paragraphs = {
                "本项目免费提供并公开源代码，旨在为用户提供便捷的使用体验，鼓励学习、交流与共同完善。",
                "未经授权，禁止以任何形式对本项目进行商业转售、付费分发或其他营利性销售。若您通过第三方付费渠道获取本项目，请及时联系卖家申请退款，谨防上当受骗。",
                "本项目所使用的部分图片及其他素材来源于互联网，相关版权归原作者或权利人所有。如有任何版权争议或侵权问题，请联系项目开发者，我们将在核实后及时进行删除或替换处理。",
                "感谢您的理解、支持与监督。"
        };
        for (int i = 0; i < paragraphs.length; i++) {
            TextView pTv = text(activity, paragraphs[i], 14, INK_SUB, false);
            pTv.setLineSpacing(dp(activity, 4), 1.08f);
            content.addView(pTv, -1, -2);
            if (i + 1 < paragraphs.length) gap(content, 12);
        }
        gap(content, 14);
        TextView endMark = text(activity, "— 已阅读至底部 —", 12, MUTED, false);
        endMark.setGravity(Gravity.CENTER);
        content.addView(endMark, -1, -2);
        scroller.addView(content, -1, -2);
        scrollBox.addView(scroller, new FrameLayout.LayoutParams(-1, viewH));
        card.addView(scrollBox, -1, -2);
        gap(card, 12);

        TextView nextBtn = pillButton(activity, "请先阅读至底部", BLUE_DISABLED_BG, BLUE_DISABLED_TEXT, 14, 46);
        card.addView(nextBtn, -1, -2);

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

        final boolean[] reachedBottom = {false};
        final boolean[] accepted = {false};

        Runnable checkAtBottom = () -> {
            if (reachedBottom[0]) return;
            View child = scroller.getChildCount() > 0 ? scroller.getChildAt(0) : null;
            boolean atBottom = child == null
                    || !scroller.canScrollVertically(1)
                    || (scroller.getScrollY() + scroller.getHeight() >= child.getHeight() - dp(activity, 8));
            if (atBottom) {
                reachedBottom[0] = true;
                nextBtn.setText("我已阅读，进入下一步");
                nextBtn.setTextColor(ON_ACCENT);
                nextBtn.setBackground(touch(activity, BLUE, 14));
            }
        };

        scroller.setOnScrollChangeListener((v, sx, sy, osx, osy) -> checkAtBottom.run());

        nextBtn.setOnClickListener(v -> {
            if (!reachedBottom[0]) {
                haptic(v, state.hapticFeedback, HapticKind.REJECT);
                Toast.makeText(activity, "请先向下滑动阅读至声明底部哦～", Toast.LENGTH_SHORT).show();
                return;
            }
            if (accepted[0]) return;
            accepted[0] = true;
            tapHaptic(v);
            state.markGuideCompleted(FocusState.GUIDE_DISCLAIMER);
            FocusStore.get(activity).save();
            dialog.dismiss();
            if (onAccepted != null) onAccepted.run();
        });

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                Toast.makeText(activity, "请先阅读开源及版权声明后进入下一步", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        if (!RedeemUi.reducedMotion(activity)) {
            card.setScaleX(0.95f);
            card.setScaleY(0.95f);
            card.setAlpha(0f);
            card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        dialog.show();
        scroller.post(checkAtBottom);

        return new FocusSheets.LiveRefresh() {
            @Override public void refresh() {}
            @Override public boolean isShowing() {
                return dialog.isShowing();
            }
        };
    }

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

    private static final String REST_PROMPT = "已进入睡眠保护时段，期间自律将不再产生时间收益（跨时段自律仅结算非睡眠保护时段的收益）。"
            + "熬夜不利于身体健康，开启早睡模式，好好休息吧！";

    static void showSleepScheduleError(Activity activity, FocusState.SleepScheduleIssue issue) {
        switch (issue) {
            case TOO_SHORT -> showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                    "再留一点休息时间吧", "睡眠时段至少需要 6 小时，试试把起床时间调晚一些。", null, "知道啦", null);
            case TOO_LONG -> showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                    "休息时间有点长啦", "睡眠时段最长 10 小时，试试调整入睡或起床时间。", null, "知道啦", null);
            case INVALID -> showMascotPrompt(activity, R.drawable.dialog_mascot_sleep_pause,
                    "请重新选个作息吧", "请选择不同的入睡和起床时间。跨午夜时，会在次日解锁。", null, "知道啦", null);
            case NONE -> { }
        }
    }

    static Dialog showSleepProtectionPrompt(Activity activity, java.util.function.Consumer<Boolean> choice) {
        Dialog dialog = openMascotPrompt(activity, R.drawable.guide_mascot_calm, "该休息啦！", REST_PROMPT,
                "继续自律（无收益）", "开启早睡模式", () -> choice.accept(false), () -> choice.accept(true));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    static View sleepProtectionPromptView(Context context, Runnable continueAction, Runnable sleepAction) {
        View body = mascotPromptView(context, R.drawable.guide_mascot_calm, "该休息啦！", REST_PROMPT,
                "继续自律（无收益）", "开启早睡模式", continueAction, sleepAction);
        body.setBackgroundColor(isDark() ? 0xa6000000 : 0x75000000);
        return body;
    }

    static void showMascotPrompt(Activity activity, int imageRes, String title, String detail,
                                 String cancel, String confirm, Runnable onConfirm) {
        openMascotPrompt(activity, imageRes, title, detail, cancel, confirm, null, onConfirm);
    }

    private static Dialog openMascotPrompt(Activity activity, int imageRes, String title, String detail,
                                           String cancel, String confirm, Runnable onCancel, Runnable onConfirm) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        View content = mascotPromptView(activity, imageRes, title, detail, cancel, confirm,
                () -> {
                    dialog.dismiss();
                    if (onCancel != null) onCancel.run();
                }, () -> {
                    dialog.dismiss();
                    if (onConfirm != null) onConfirm.run();
                });
        dialog.setContentView(content);

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            win.setGravity(Gravity.CENTER);
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(isDark() ? 0.65f : 0.46f);
        }

        dialog.show();
        return dialog;
    }

    private static View mascotPromptView(Context activity, int imageRes, String title, String detail,
                                         String cancel, String confirm, Runnable onCancel, Runnable onConfirm) {
        FrameLayout backdrop = new FrameLayout(activity);
        backdrop.setClipChildren(false);
        backdrop.setClipToPadding(false);
        int hPad = dp(activity, 20);
        backdrop.setPadding(hPad, dp(activity, 16), hPad, dp(activity, 16));

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int stageW = Math.min(dp(activity, 344), Math.max(1, screenW - dp(activity, 40)));
        FrameLayout stage = new FrameLayout(activity);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);

        LinearLayout card = column(activity);
        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(CARD);
        cardDrawable.setCornerRadius(dp(activity, 24));
        cardDrawable.setStroke(dp(activity, 1.5f), blueStroke());
        card.setBackground(cardDrawable);
        card.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        elevate(card, 12);

        LinearLayout copyCol = column(activity);
        copyCol.setPadding(dp(activity, 116), dp(activity, 2), dp(activity, 2), dp(activity, 2));
        copyCol.setMinimumHeight(dp(activity, 82));
        copyCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = text(activity, title, 19, INK, true);
        titleTv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        copyCol.addView(titleTv);
        gap(copyCol, 6);

        TextView subTv = text(activity, detail, 13, INK_SUB, false);
        subTv.setGravity(Gravity.CENTER);
        subTv.setLineSpacing(dp(activity, 2.5f), 1.08f);
        subTv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        boolean wideCopy = detail.length() > 49;
        if (!wideCopy) copyCol.addView(subTv);
        card.addView(copyCol, -1, -2);
        if (wideCopy) {
            gap(card, 8);
            card.addView(subTv, -1, -2);
        }
        gap(card, 16);

        TextView okBtn = pillButton(activity, confirm, BLUE, ON_ACCENT, 14, 46);
        okBtn.setTextSize(14f);
        okBtn.setOnClickListener(v -> {
            tapHaptic(v);
            onConfirm.run();
        });
        if (cancel == null) {
            card.addView(okBtn, -1, -2);
        } else {
            LinearLayout actions = row(activity);
            TextView cancelBtn = pillButton(activity, cancel, SURFACE_SUBTLE, INK_SUB, 14, 46);
            cancelBtn.setSingleLine(false);
            cancelBtn.setOnClickListener(v -> onCancel.run());
            weight(actions, cancelBtn);
            space(actions, 8);
            weight(actions, okBtn);
            card.addView(actions);
        }

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(activity, 24);
        stage.addView(card, cardLp);

        View footShadow = new View(activity);
        GradientDrawable shadowOval = new GradientDrawable();
        shadowOval.setShape(GradientDrawable.OVAL);
        shadowOval.setColor(isDark() ? 0x40000000 : 0x1A182218);
        footShadow.setBackground(shadowOval);
        footShadow.setOutlineProvider(null);
        footShadow.setElevation(dp(activity, 13));
        FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(dp(activity, 74), dp(activity, 10),
                Gravity.TOP | Gravity.START);
        shadowLp.leftMargin = dp(activity, 28);
        shadowLp.topMargin = dp(activity, 102);
        stage.addView(footShadow, shadowLp);

        ImageView mascotImg = new ImageView(activity);
        mascotImg.setImageResource(imageRes);
        mascotImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mascotImg.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mascotImg.setOutlineProvider(null);
        mascotImg.setElevation(dp(activity, 14));
        FrameLayout.LayoutParams mascotLp = new FrameLayout.LayoutParams(dp(activity, 122), dp(activity, 112),
                Gravity.TOP | Gravity.START);
        mascotLp.leftMargin = dp(activity, 6);
        mascotLp.topMargin = 0;
        stage.addView(mascotImg, mascotLp);

        backdrop.addView(stage, new FrameLayout.LayoutParams(stageW, -2, Gravity.CENTER));
        if (!RedeemUi.reducedMotion(activity)) {
            stage.setScaleX(0.94f);
            stage.setScaleY(0.94f);
            stage.setAlpha(0f);
            stage.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        return backdrop;
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
    // Static Theme-Aware Dashed Spotlight Overlay (Pop-Out Mascot Bubble)
    // =========================================================================
    static final class SpotlightGuideView extends FrameLayout {
        private final FrameLayout host;
        private final ScrollView scroller;
        private final List<GuideStep> steps;
        private final Runnable onFinish;

        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mascotShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path scrimPath = new Path();
        private final Path bubblePath = new Path();
        private final RectF cutoutRect = new RectF();
        private final RectF cardRect = new RectF();
        private final RectF arcOval = new RectF();
        private final RectF mascotFootShadowRect = new RectF();

        private final LinearLayout cardContent;
        private final LinearLayout copyCol;
        private final TextView stepEyebrowTv;
        private final TextView titleTv;
        private final TextView bodyTv;
        private final TextView skipTv;
        private final StepDotsView dotsView;
        private final TextView nextBtn;
        private final ImageView mascotImg;

        private int currentIndex;
        private int currentRadiusPx;
        private boolean hasTargetRect;
        private boolean hasCardRect;
        private boolean bubbleArrowUp = true;
        private float bubbleArrowCenterX;
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
            setClipChildren(false);
            setClipToPadding(false);
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);

            scrimPaint.setStyle(Paint.Style.FILL);
            scrimPaint.setColor(isDark() ? 0xCC060911 : 0xAD0E1418);

            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeWidth(dp(ctx, 1.5f));
            dashPaint.setStrokeCap(Paint.Cap.ROUND);
            dashPaint.setColor(isDark() ? BLUE : BLUE_LIGHT);
            dashPaint.setPathEffect(new DashPathEffect(new float[]{dp(ctx, 5.5f), dp(ctx, 4f)}, 0f));

            bubbleShadowPaint.setStyle(Paint.Style.FILL);
            bubbleShadowPaint.setColor(isDark() ? 0x55000000 : 0x260E1418);

            bubbleFillPaint.setStyle(Paint.Style.FILL);
            bubbleFillPaint.setColor(CARD);

            bubbleStrokePaint.setStyle(Paint.Style.STROKE);
            bubbleStrokePaint.setStrokeWidth(dp(ctx, 1f));
            bubbleStrokePaint.setColor(blueStroke());

            mascotShadowPaint.setStyle(Paint.Style.FILL);
            mascotShadowPaint.setColor(isDark() ? 0x40000000 : 0x1A182218);

            cardContent = column(ctx);
            cardContent.setPadding(dp(ctx, 12), dp(ctx, 11), dp(ctx, 12), dp(ctx, 10));

            copyCol = column(ctx);
            copyCol.setPadding(dp(ctx, 98), 0, dp(ctx, 2), 0);
            copyCol.setMinimumHeight(dp(ctx, 80));

            stepEyebrowTv = text(ctx, "", 10.5f, MUTED, true);
            stepEyebrowTv.setSingleLine(true);
            stepEyebrowTv.setLetterSpacing(0.06f);
            copyCol.addView(stepEyebrowTv);
            gap(copyCol, 2);

            titleTv = text(ctx, "", 15.5f, INK, true);
            copyCol.addView(titleTv);
            gap(copyCol, 3);

            bodyTv = text(ctx, "", 12.5f, INK_SUB, false);
            bodyTv.setLineSpacing(dp(ctx, 1.5f), 1.05f);
            bodyTv.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
            copyCol.addView(bodyTv);
            cardContent.addView(copyCol, -1, -2);
            gap(cardContent, 6);

            LinearLayout actions = row(ctx);
            skipTv = text(ctx, "跳过引导", 12f, MUTED, true);
            skipTv.setGravity(Gravity.CENTER_VERTICAL);
            skipTv.setMinimumHeight(dp(ctx, 32));
            skipTv.setPadding(dp(ctx, 4), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3));
            skipTv.setOnClickListener(v -> {
                tapHaptic(v);
                dismiss();
            });
            actions.addView(skipTv);

            FrameLayout dotsSlot = new FrameLayout(ctx);
            dotsView = new StepDotsView(ctx);
            dotsSlot.addView(dotsView, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            actions.addView(dotsSlot, new LinearLayout.LayoutParams(0, -2, 1f));

            nextBtn = pillButton(ctx, "下一步 →", BLUE, ON_ACCENT, 16, 32);
            nextBtn.setTextSize(12f);
            nextBtn.setSingleLine(true);
            nextBtn.setPadding(dp(ctx, 14), dp(ctx, 4), dp(ctx, 14), dp(ctx, 4));
            nextBtn.setOnClickListener(v -> {
                tapHaptic(v);
                advanceOrFinish();
            });
            actions.addView(nextBtn, -2, -2);
            cardContent.addView(actions, -1, -2);

            mascotImg = new ImageView(ctx);
            mascotImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mascotImg.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            cardContent.setVisibility(View.INVISIBLE);
            mascotImg.setVisibility(View.INVISIBLE);
            addView(cardContent, new FrameLayout.LayoutParams(-1, -2));
            addView(mascotImg, new FrameLayout.LayoutParams(dp(ctx, 106), dp(ctx, 118)));
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
            stepEyebrowTv.setText(steps.size() > 1
                    ? String.format(Locale.ROOT, "STEP %02d / %02d", index + 1, steps.size())
                    : "小贴士");
            titleTv.setText(step.title());
            bodyTv.setText(step.body());
            nextBtn.setText(index + 1 < steps.size() ? "下一步 →" : "我知道啦！");
            skipTv.setVisibility(steps.size() > 1 ? View.VISIBLE : View.INVISIBLE);
            dotsView.setStep(index, steps.size());

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
            currentRadiusPx = dp(ctx, step.cornerRadiusDp());
            hasTargetRect = cutoutRect.width() > 0 && cutoutRect.height() > 0;

            int sideMargin = dp(ctx, 16);
            int cardMaxW = Math.min(dp(ctx, 356), Math.max(dp(ctx, 220), hostW - sideMargin * 2));
            int cardLeft = Math.round((hostW - cardMaxW) * 0.5f);
            cardContent.measure(
                    MeasureSpec.makeMeasureSpec(cardMaxW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.AT_MOST));
            int cardH = cardContent.getMeasuredHeight();

            int maxProtrude = dp(ctx, 22);
            int minProtrude = dp(ctx, 14);
            int copyH = copyCol.getMeasuredHeight();
            int textBottomInCard = cardContent.getPaddingTop() + copyH;
            int maxMascotBottomInCard = textBottomInCard + dp(ctx, 2);
            int mascotH = Math.max(dp(ctx, 114), Math.min(dp(ctx, 130), maxMascotBottomInCard + maxProtrude));
            int mascotW = dp(ctx, 106);
            int actualProtrude = Math.max(minProtrude, Math.min(maxProtrude, mascotH - maxMascotBottomInCard));

            int arrowH = dp(ctx, 10);
            int pointerGap = dp(ctx, 20);
            int topSafe = dp(ctx, 36) + maxProtrude;
            int bottomSafe = dp(ctx, 24) + arrowH;

            float spaceBelow = (hostH - bottomSafe) - cutoutRect.bottom;
            float spaceAbove = cutoutRect.top - topSafe - arrowH;

            int cardTop;
            if (spaceBelow >= cardH + pointerGap || spaceBelow >= spaceAbove) {
                bubbleArrowUp = true;
                cardTop = Math.round(cutoutRect.bottom + pointerGap);
                cardTop = Math.min(cardTop, hostH - bottomSafe - cardH);
            } else {
                bubbleArrowUp = false;
                cardTop = Math.round(cutoutRect.top - pointerGap - cardH);
            }
            cardTop = Math.max(topSafe, Math.min(cardTop, Math.max(topSafe, hostH - bottomSafe - cardH)));

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cardContent.getLayoutParams();
            lp.width = cardMaxW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = cardLeft;
            lp.topMargin = cardTop;
            cardContent.setLayoutParams(lp);
            cardContent.setVisibility(View.VISIBLE);

            int mascotLeft = cardLeft + dp(ctx, 2);
            int mascotTop = cardTop - actualProtrude;
            FrameLayout.LayoutParams mLp = (FrameLayout.LayoutParams) mascotImg.getLayoutParams();
            mLp.width = mascotW;
            mLp.height = mascotH;
            mLp.gravity = Gravity.TOP | Gravity.START;
            mLp.leftMargin = mascotLeft;
            mLp.topMargin = mascotTop;
            mascotImg.setLayoutParams(mLp);
            mascotImg.setVisibility(View.VISIBLE);

            cardRect.set(cardLeft, cardTop, cardLeft + cardMaxW, cardTop + cardH);
            float footCenterX = mascotLeft + mascotW * 0.5f;
            float footY = Math.min(cardRect.bottom - dp(ctx, 38), mascotTop + mascotH - dp(ctx, 4));
            mascotFootShadowRect.set(
                    footCenterX - dp(ctx, 28),
                    footY - dp(ctx, 4),
                    footCenterX + dp(ctx, 28),
                    footY + dp(ctx, 4));

            float r = dp(ctx, 20);
            float halfArrowW = dp(ctx, 9);
            float minArrowX = bubbleArrowUp
                    ? (cardLeft + dp(ctx, 114))
                    : (cardLeft + r + halfArrowW + dp(ctx, 6));
            float maxArrowX = cardRect.right - r - halfArrowW - dp(ctx, 6);
            if (minArrowX > maxArrowX) minArrowX = (cardRect.left + cardRect.right) * 0.5f;
            bubbleArrowCenterX = Math.max(minArrowX, Math.min(maxArrowX, cutoutRect.centerX()));
            hasCardRect = true;

            buildBubblePath(ctx, r, halfArrowW, arrowH);
            invalidate();
        }

        private void buildBubblePath(Context ctx, float r, float halfArrowW, float arrowH) {
            float l = cardRect.left;
            float t = cardRect.top;
            float right = cardRect.right;
            float b = cardRect.bottom;
            float d = r * 2f;
            float ax = bubbleArrowCenterX;
            float tipInset = dp(ctx, 1.5f);

            bubblePath.reset();
            bubblePath.moveTo(l + r, t);
            if (bubbleArrowUp) {
                bubblePath.lineTo(ax - halfArrowW, t);
                bubblePath.lineTo(ax - tipInset, t - arrowH + tipInset);
                bubblePath.quadTo(ax, t - arrowH, ax + tipInset, t - arrowH + tipInset);
                bubblePath.lineTo(ax + halfArrowW, t);
            }
            bubblePath.lineTo(right - r, t);
            arcOval.set(right - d, t, right, t + d);
            bubblePath.arcTo(arcOval, -90f, 90f, false);
            bubblePath.lineTo(right, b - r);
            arcOval.set(right - d, b - d, right, b);
            bubblePath.arcTo(arcOval, 0f, 90f, false);
            if (!bubbleArrowUp) {
                bubblePath.lineTo(ax + halfArrowW, b);
                bubblePath.lineTo(ax + tipInset, b + arrowH - tipInset);
                bubblePath.quadTo(ax, b + arrowH, ax - tipInset, b + arrowH - tipInset);
                bubblePath.lineTo(ax - halfArrowW, b);
            }
            bubblePath.lineTo(l + r, b);
            arcOval.set(l, b - d, l + d, b);
            bubblePath.arcTo(arcOval, 90f, 90f, false);
            bubblePath.lineTo(l, t + r);
            arcOval.set(l, t, l + d, t + d);
            bubblePath.arcTo(arcOval, 180f, 90f, false);
            bubblePath.close();
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
                canvas.drawRoundRect(cutoutRect, currentRadiusPx, currentRadiusPx, dashPaint);
            }

            if (hasCardRect) {
                canvas.save();
                canvas.translate(0f, dp(getContext(), 4));
                canvas.drawPath(bubblePath, bubbleShadowPaint);
                canvas.restore();
                canvas.drawPath(bubblePath, bubbleFillPaint);
                canvas.drawPath(bubblePath, bubbleStrokePaint);
                canvas.drawOval(mascotFootShadowRect, mascotShadowPaint);
            }
        }
    }

    private static final class StepDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF capsule = new RectF();
        private int activeIndex;
        private int total;

        StepDotsView(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setStep(int index, int count) {
            this.activeIndex = index;
            this.total = count;
            setVisibility(count > 1 ? VISIBLE : GONE);
            requestLayout();
            invalidate();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Context ctx = getContext();
            if (total <= 1) {
                setMeasuredDimension(0, 0);
                return;
            }
            int dot = dp(ctx, 6);
            int activeW = dp(ctx, 18);
            int gapPx = dp(ctx, 5);
            int w = activeW + (total - 1) * dot + (total - 1) * gapPx;
            setMeasuredDimension(w, dot + dp(ctx, 2));
        }

        @Override protected void onDraw(Canvas canvas) {
            if (total <= 1) return;
            Context ctx = getContext();
            float dot = dp(ctx, 6);
            float activeW = dp(ctx, 18);
            float gapPx = dp(ctx, 5);
            float y = (getHeight() - dot) * 0.5f;
            float r = dot * 0.5f;
            float x = 0f;
            for (int i = 0; i < total; i++) {
                boolean active = i == activeIndex;
                float itemW = active ? activeW : dot;
                paint.setColor(active ? BLUE : mutedCircleBorder());
                capsule.set(x, y, x + itemW, y + dot);
                canvas.drawRoundRect(capsule, r, r, paint);
                x += itemW + gapPx;
            }
        }
    }
}
