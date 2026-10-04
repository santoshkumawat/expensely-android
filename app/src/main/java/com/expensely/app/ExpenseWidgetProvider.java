package com.expensely.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Home screen widget: this month's spend, what is left to pay, and the next dues.
 *
 * The web app pushes a small summary through the file bridge each time Home loads
 * (see {@link #save}); the widget only draws that saved copy. No login token or
 * network access is involved, so it can be a little behind until the app is opened.
 */
public class ExpenseWidgetProvider extends AppWidgetProvider {

    private static final String PREFS = "expensely_widget";
    private static final String KEY_SNAPSHOT = "snapshot";
    private static final int MAX_SNAPSHOT_CHARS = 8000;
    private static final String MASK = "₹ ••••";

    private static final int[] ROWS = {R.id.widget_row1, R.id.widget_row2, R.id.widget_row3};
    private static final int[] ROW_NAMES = {R.id.widget_row1_name, R.id.widget_row2_name, R.id.widget_row3_name};
    private static final int[] ROW_AMOUNTS = {R.id.widget_row1_amount, R.id.widget_row2_amount, R.id.widget_row3_amount};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    /** Stores a snapshot from the web app (JSON text) and redraws every widget. */
    static void save(Context context, String json) {
        if (json == null || json.length() > MAX_SNAPSHOT_CHARS) return;
        try {
            new JSONObject(json);                       // reject anything that is not JSON
        } catch (Exception e) {
            return;
        }
        prefs(context).edit().putString(KEY_SNAPSHOT, json).apply();
        refresh(context);
    }

    /** Forgets the saved numbers (logout) and redraws every widget. */
    static void clear(Context context) {
        prefs(context).edit().remove(KEY_SNAPSHOT).apply();
        refresh(context);
    }

    private static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ExpenseWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_expense);

        PendingIntent open = PendingIntent.getActivity(
                context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, open);

        for (int row : ROWS) views.setViewVisibility(row, View.GONE);
        views.setTextViewText(R.id.widget_updated, "");

        JSONObject data = null;
        try {
            String json = prefs(context).getString(KEY_SNAPSHOT, null);
            if (json != null) data = new JSONObject(json);
        } catch (Exception ignored) {
            // fall through to the empty state
        }

        if (data == null) {
            views.setTextViewText(R.id.widget_title, "Expensely");
            views.setTextViewText(R.id.widget_spent, "—");
            views.setTextViewText(R.id.widget_topay, "—");
            views.setTextViewText(R.id.widget_empty, "Open Expensely and log in to load your month.");
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            return views;
        }

        boolean hidden = data.optBoolean("hidden", false);
        views.setTextViewText(R.id.widget_title, "Expensely · " + data.optString("month", "This month"));
        views.setTextViewText(R.id.widget_spent, money(data.optDouble("spent", 0), hidden));
        views.setTextViewText(R.id.widget_topay, money(data.optDouble("toPay", 0), hidden));

        long updated = data.optLong("updated", 0);
        if (updated > 0) {
            views.setTextViewText(R.id.widget_updated, DateUtils.getRelativeTimeSpanString(
                    updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
        }

        JSONArray dues = data.optJSONArray("dues");
        int shown = dues == null ? 0 : Math.min(dues.length(), ROWS.length);
        for (int i = 0; i < shown; i++) {
            JSONObject due = dues.optJSONObject(i);
            if (due == null) continue;
            String name = due.optString("name", "") + " · " + due.optString("info", "");
            views.setTextViewText(ROW_NAMES[i], name);
            views.setTextViewText(ROW_AMOUNTS[i], money(due.optDouble("amount", 0), hidden));
            views.setViewVisibility(ROWS[i], View.VISIBLE);
        }

        if (shown == 0) {
            views.setTextViewText(R.id.widget_empty, "Nothing due this month — you're all caught up");
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE);
        }
        return views;
    }

    private static String money(double amount, boolean hidden) {
        if (hidden) return MASK;
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        return "₹" + format.format(Math.round(amount));
    }
}
