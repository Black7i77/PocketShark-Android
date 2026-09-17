package com.scarfaceos.pocketshark.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BLACK = 0xff05080c;
    public static final int PANEL = 0xff0d151e;
    public static final int PANEL_ALT = 0xff111d28;
    public static final int CYAN = 0xff37d9ff;
    public static final int GREEN = 0xff50e3a4;
    public static final int AMBER = 0xffffc857;
    public static final int RED = 0xffff5d73;
    public static final int TEXT = 0xffe9f3fa;
    public static final int MUTED = 0xff8ca3b5;

    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    public static TextView title(Context context, String value) {
        TextView view = text(context, value, 21, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static Button button(Context context, String value, int color) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextColor(BLACK);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(color, 10, color));
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        button.setMinHeight(dp(context, 46));
        return button;
    }

    public static GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radiusDp * 2.5f);
        shape.setStroke(1, stroke);
        return shape;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(PANEL, 12, 0xff243442));
        return card;
    }

    public static LinearLayout.LayoutParams margins(Context context, int width, int height,
                                                      int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(context, left), dp(context, top), dp(context, right), dp(context, bottom));
        return params;
    }

    public static void center(TextView view) { view.setGravity(Gravity.CENTER); }

    public static void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Android 15+ enforces edge-to-edge drawing for apps targeting modern SDKs.
     * Keep PocketShark's content clear of status bars, camera cut-outs and the
     * gesture/navigation area while retaining the deliberate dark system bars.
     */
    public static void applySystemBarInsets(Activity activity, View root) {
        Window window = activity.getWindow();
        window.setStatusBarColor(BLACK);
        window.setNavigationBarColor(BLACK);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }

        if (Build.VERSION.SDK_INT < 35) {
            root.setFitsSystemWindows(true);
            return;
        }

        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(baseLeft + bars.left, baseTop + bars.top,
                    baseRight + bars.right, baseBottom + bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
