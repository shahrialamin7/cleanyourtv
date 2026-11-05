package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Recent apps overlay: a horizontal shelf of apps Home knows about, with one best-effort
 * "Clear all" action. Opened from the Settings hold menu; touch and D-pad both work.
 */
final class RecentAppsDialog {
    private final Activity activity;
    private final Dialog dialog;
    private final List<RecentApps.Entry> entries = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private RecyclerView list;
    private Adapter adapter;
    private TextView empty;
    private TextView clear;
    private TextView close;
    private View root;
    private boolean closed;

    private RecentAppsDialog(Activity activity) {
        this.activity = activity;
        this.dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) dismiss();
                return true;
            }
            return false;
        });
        dialog.setOnDismissListener(d -> cleanup());
        build();
    }

    static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        new RecentAppsDialog(activity).open();
    }

    private void build() {
        root = new FrameLayout(activity);
        root.setBackgroundColor(0xCC000000);
        root.setClickable(true);
        root.setOnClickListener(v -> dismiss());

        FrameLayout panel = new FrameLayout(activity);
        panel.setClickable(true);
        GradientDrawable panelBg = round(0xF218181C, 26);
        panel.setBackground(panelBg);
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min((int) (screen * 0.84f), dp(1120));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(width,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, panelLp);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(18), dp(24), dp(14));
        panel.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(activity.getString(R.string.recent_apps_title), 24, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        close = pill(activity.getString(R.string.recent_apps_close));
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = text(activity.getString(RecentApps.usageAccess(activity)
                        ? R.string.recent_apps_hint : R.string.recent_apps_usage_hint),
                13, 0x99FFFFFF);
        hint.setPadding(0, dp(4), 0, dp(4));
        body.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout shelf = new FrameLayout(activity);
        shelf.setClipChildren(false);
        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        list.setItemAnimator(null);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setFocusable(false);
        list.setHasFixedSize(true);
        list.setPadding(0, dp(8), 0, dp(8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        shelf.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = text(activity.getString(R.string.recent_apps_empty), 15, 0x99FFFFFF);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), 0, dp(24), 0);
        empty.setVisibility(View.GONE);
        shelf.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(shelf, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(208)));

        LinearLayout footer = new LinearLayout(activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);
        if (!RecentApps.usageAccess(activity)) {
            TextView allow = pill(activity.getString(R.string.recent_apps_usage_access));
            allow.setOnClickListener(v -> {
                dismiss();
                try {
                    activity.startActivity(RecentApps.usageAccessIntent());
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            });
            footer.addView(allow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView note = text(activity.getString(R.string.recent_apps_clear_note), 12, 0x80FFFFFF);
        note.setMaxLines(2);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        noteLp.leftMargin = dp(12);
        noteLp.rightMargin = dp(12);
        footer.addView(note, noteLp);
        clear = pill(activity.getString(R.string.recent_apps_clear));
        clear.setId(View.generateViewId());
        clear.setOnClickListener(v -> clearAll());
        footer.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void open() {
        refresh();
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }
        dialog.show();
        list.post(this::focusFirst);
    }

    private void focusFirst() {
        if (closed) return;
        if (entries.isEmpty()) {
            close.requestFocus();
            return;
        }
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        if (holder != null) holder.itemView.requestFocus();
        else list.post(this::focusFirst);
    }

    private void refresh() {
        entries.clear();
        entries.addAll(RecentApps.list(activity));
        boolean has = !entries.isEmpty();
        adapter.notifyDataSetChanged();
        list.setVisibility(has ? View.VISIBLE : View.GONE);
        empty.setVisibility(has ? View.GONE : View.VISIBLE);
        clear.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private void clearAll() {
        int asked = RecentApps.clearAll(activity);
        Toast.makeText(activity, asked > 0
                ? activity.getString(R.string.recent_apps_cleared, asked)
                : activity.getString(R.string.recent_apps_cleared_none), Toast.LENGTH_SHORT).show();
        refresh();
        close.requestFocus();
    }

    private void launch(String pkg) {
        if (activity instanceof MainActivity && ((MainActivity) activity).openRecent(pkg)) {
            dismiss();
            return;
        }
        PackageManager pm = activity.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        RecentApps.record(activity, pkg);
        dismiss();
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void dismiss() {
        if (closed) return;
        closed = true;
        dialog.dismiss();
    }

    private void cleanup() {
        closed = true;
        loader.shutdownNow();
        icons.clear();
        adapter = null;
    }

    private String label(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), pkg);
        } catch (Exception e) {
            return pkg;
        }
    }

    private void loadIcon(ImageView view, String pkg) {
        view.setTag(pkg);
        Drawable cached = icons.get(pkg);
        if (cached != null) {
            view.setImageDrawable(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_apps);
        if (closed || loader.isShutdown()) return;
        loader.execute(() -> {
            Drawable loaded = null;
            try {
                PackageManager pm = activity.getPackageManager();
                loaded = pm.getApplicationInfo(pkg, 0).loadIcon(pm);
            } catch (Exception ignored) {
            }
            Drawable result = loaded;
            main.post(() -> {
                if (closed) return;
                if (result != null) icons.put(pkg, result);
                if (pkg.equals(view.getTag())) {
                    if (result != null) view.setImageDrawable(result);
                    else view.setImageResource(R.drawable.ic_apps);
                }
            });
        });
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(false);
        return view;
    }

    private TextView pill(String value) {
        TextView view = text(value, 14, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(16), dp(8), dp(16), dp(8));
        view.setBackground(round(0x26FFFFFF, 40));
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnFocusChangeListener((v, has) ->
                v.setBackground(round(has ? 0x55FFFFFF : 0x26FFFFFF, 40)));
        return view;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(10));
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(round(0x1FFFFFFF, 18));
            card.setClipChildren(false);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(dp(150), dp(190));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            card.setLayoutParams(lp);

            ImageView icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            card.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

            TextView name = text("", 14, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(8), 0, 0);
            card.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView when = text("", 11, 0x80FFFFFF);
            when.setGravity(Gravity.CENTER);
            when.setMaxLines(1);
            when.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(when, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            card.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                        .setDuration(120).start();
                v.setBackground(round(has ? 0x40FFFFFF : 0x1FFFFFFF, 18));
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_UP) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    v.performClick();
                    return true;
                }
                return false;
            });
            return new Holder(card, icon, name, when);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            RecentApps.Entry entry = entries.get(position);
            String name = label(entry.pkg);
            holder.name.setText(name);
            holder.when.setText(LastOpened.describe(activity, entry.at));
            loadIcon(holder.icon, entry.pkg);
            holder.itemView.setContentDescription(name + ", "
                    + LastOpened.describe(activity, entry.at));
            if (clear != null) holder.itemView.setNextFocusDownId(clear.getId());
            holder.itemView.setOnClickListener(v -> launch(entry.pkg));
            holder.itemView.setOnLongClickListener(v -> {
                RecentApps.forget(activity, entry.pkg);
                Toast.makeText(activity, R.string.recent_apps_removed, Toast.LENGTH_SHORT).show();
                refresh();
                focusFirst();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView when;

        Holder(@NonNull View itemView, ImageView icon, TextView name, TextView when) {
            super(itemView);
            this.icon = icon;
            this.name = name;
            this.when = when;
        }
    }
}
