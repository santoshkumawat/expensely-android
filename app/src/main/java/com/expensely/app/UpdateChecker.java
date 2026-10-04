package com.expensely.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks the web app's /app-version.json on launch. When it lists a newer
 * versionCode than the installed one, shows an Update banner at the top of the app that downloads
 * the APK inside the app and hands it to the system installer.
 */
class UpdateChecker {

    private static final String VERSION_URL = "https://expensely-app.netlify.app/app-version.json";
    /** The APK may only come from this project's GitHub releases. */
    private static final String APK_HOST = "github.com";
    private static final String APK_PATH_PREFIX = "/santoshkumawat/expensely-android/releases/download/";

    private final Activity activity;
    private volatile boolean downloading;

    UpdateChecker(Activity activity) {
        this.activity = activity;
    }

    /** Fire and forget; fails silently (offline, bad JSON...) since an update is never urgent. */
    void check() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(fetch(VERSION_URL));
                long latest = json.getLong("versionCode");
                if (latest <= installedVersionCode()) return;

                String apkUrl = json.getString("apkUrl");
                if (!isTrustedApkUrl(apkUrl)) return;

                String name = json.optString("versionName", String.valueOf(latest));
                                activity.runOnUiThread(() -> showUpdateBanner(name, apkUrl));
            } catch (Exception ignored) {
                // no update info: carry on
            }
        }).start();
    }

    private long installedVersionCode() throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        return PackageInfoCompat.getLongVersionCode(info);
    }

    private static boolean isTrustedApkUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            return "https".equals(uri.getScheme())
                    && APK_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(APK_PATH_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** A slim bar above the app instead of a popup: it never blocks the screen. */
    private void showUpdateBanner(String versionName, String apkUrl) {
        if (activity.isFinishing()) return;
        View banner = activity.findViewById(R.id.update_banner);
        TextView text = activity.findViewById(R.id.update_banner_text);
        TextView action = activity.findViewById(R.id.update_banner_action);
        View close = activity.findViewById(R.id.update_banner_close);

        text.setText("Expensely v" + versionName + " is available");
        action.setOnClickListener(v -> startUpdate(apkUrl, banner, text, action, close));
        close.setOnClickListener(v -> banner.setVisibility(View.GONE));
        banner.setVisibility(View.VISIBLE);
    }

    private void startUpdate(String apkUrl, View banner, TextView text, TextView action, View close) {
        if (downloading) return;

        // Android 8+: the user must allow this app to install packages (one-time)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "Allow installs from Expensely, then tap Update again",
                    Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return;
        }

        downloading = true;
        String original = text.getText().toString();
        text.setText("Downloading update…");
        action.setVisibility(View.GONE);
        close.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                File apk = download(apkUrl, percent -> activity.runOnUiThread(
                        () -> text.setText("Downloading update… " + percent + "%")));
                activity.runOnUiThread(() -> {
                    downloading = false;
                    resetBanner(text, original, action, close);
                    install(apk);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    downloading = false;
                    resetBanner(text, original, action, close);
                    Toast.makeText(activity, "Update download failed. Check your connection and try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void resetBanner(TextView text, String original, View action, View close) {
        text.setText(original);
        action.setVisibility(View.VISIBLE);
        close.setVisibility(View.VISIBLE);
    }

    private interface Progress { void onPercent(int percent); }

    private File download(String address, Progress progress) throws Exception {
        File dir = new File(activity.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create folder");
        File apk = new File(dir, "expensely-update.apk");

        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
            long total = conn.getContentLengthLong();
            long done = 0;
            int last = -1;
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent != last) { last = percent; progress.onPercent(percent); }
                }
            }
            if (done == 0) throw new Exception("Empty download");
        } finally {
            conn.disconnect();
        }
        return apk;
    }

    private void install(File apk) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Could not open the installer", Toast.LENGTH_LONG).show();
        }
    }
}
