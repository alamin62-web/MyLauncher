package com.alamin.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends Activity {
    private WebView webView;
    private String pendingPackage = "";
    private static final int PICK_IMAGE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new LauncherBridge(), "AndroidLauncher");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.evaluateJavascript("if(typeof onHomeResume === 'function') onHomeResume();", null);
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("handleBackPress()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                    if (selectedImage != null) {
                        Bitmap resized = Bitmap.createScaledBitmap(selectedImage, 144, 144, true);
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        resized.compress(Bitmap.CompressFormat.PNG, 90, stream);
                        String base64Icon = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);

                        final String js = "saveCustomIcon('" + pendingPackage + "', '" + base64Icon + "')";
                        webView.post(() -> webView.evaluateJavascript(js, null));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public class LauncherBridge {
        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray appList = new JSONArray();
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo info : apps) {
                try {
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;

                    JSONObject obj = new JSONObject();
                    obj.put("name", info.loadLabel(pm).toString());
                    obj.put("package", pkg);
                    obj.put("icon", drawableToBase64(info.loadIcon(pm)));
                    appList.put(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return appList.toString();
        }

        @JavascriptInterface
        public void launchApp(String packageName) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                ActivityOptions options = ActivityOptions.makeCustomAnimation(
                    MainActivity.this, android.R.anim.fade_in, android.R.anim.fade_out
                );
                startActivity(launchIntent, options.toBundle());
            }
        }

        @JavascriptInterface
        public void pickIconForApp(String packageName) {
            pendingPackage = packageName;
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        }

        @JavascriptInterface
        public void openAppInfo(String packageName) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }

        @JavascriptInterface
        public void uninstallApp(String packageName) {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        }

        @JavascriptInterface
        public int getBatteryLevel() {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }

        private String drawableToBase64(Drawable drawable) {
            int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 108;
            int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 108;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, stream);
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);
        }
    }
}
