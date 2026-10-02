package com.expensely.app;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;

import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    /** The only address that loads inside the app. */
    private static final String START_URL = "https://expensely-app.netlify.app";
    private static final String APP_HOST = Uri.parse(START_URL).getHost();

    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Marks this WebView as the Expensely v2.1 Android app.
        settings.setUserAgentString(
                settings.getUserAgentString() + " ExpenselyApp/2.1"
        );

        // File downloads (the Excel export): a WebView ignores download links,
        // so the web app hands the file to this bridge instead.
        webView.addJavascriptInterface(new FileBridge(), "ExpenselyAndroid");

        webView.setWebViewClient(new WebViewClient() {
            // Keep outside pages out of the WebView: they could call the file bridge.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_HOST != null && APP_HOST.equalsIgnoreCase(uri.getHost())) {
                    return false;                       // our own site: load it here
                }
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)
                        || "mailto".equals(scheme) || "tel".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                        // no app can open it: just do nothing
                    }
                }
                return true;                            // never load it in the WebView
            }
        });
        webView.loadUrl(START_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    /** Receives a file from the web page and saves it where the user can find it. */
    private class FileBridge {

        @JavascriptInterface
        public void saveFile(String fileName, String mimeType, String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToDownloads(safeName, mimeType, bytes);
                    toast("Saved to Downloads: " + safeName);
                } else {
                    openWithChooser(writeToCache(safeName, bytes), mimeType);
                }
            } catch (Exception e) {
                toast("Could not save the file");
            }
        }

        /** Android 10+: the shared Downloads folder, no storage permission needed. */
        @RequiresApi(Build.VERSION_CODES.Q)
        private void saveToDownloads(String name, String mime, byte[] bytes) throws IOException {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create the file");
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open the file");
                out.write(bytes);
            }
        }

        /** Android 9 and older: save privately, then let the user open or share it. */
        private File writeToCache(String name, byte[] bytes) throws IOException {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return file;
        }

        private void openWithChooser(File file, String mime) {
            Uri uri = FileProvider.getUriForFile(
                    MainActivity.this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Save or open report")));
        }

        private void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
        }
    }
}
