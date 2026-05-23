package via.fuckcustomtab.frisk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomTabHookGlobal implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        hookCustomTabsIntent(lpparam);
        hookStartActivity(lpparam.packageName);
        hookViaTrampoline(lpparam);
    }

    private void hookCustomTabsIntent(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cct = XposedHelpers.findClassIfExists(
                "androidx.browser.customtabs.CustomTabsIntent",
                lpparam.classLoader
        );
        if (cct != null) {
            XposedBridge.hookAllMethods(cct, "launchUrl", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    Uri uri = (Uri) param.args[1];

                    if (isWebScheme(uri)) {
                        logInfo("CustomTabsIntent.launchUrl -> ACTION_VIEW",
                                "pkg=" + safePackageName(ctx) + " url=" + uri);
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                        param.setResult(null);
                    }
                }
            });
        }

        Class<?> cctSupport = XposedHelpers.findClassIfExists(
                "android.support.customtabs.CustomTabsIntent",
                lpparam.classLoader
        );
        if (cctSupport != null) {
            XposedBridge.hookAllMethods(cctSupport, "launchUrl", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context ctx = (Context) param.args[0];
                    Uri uri = (Uri) param.args[1];

                    if (isWebScheme(uri)) {
                        logInfo("CustomTabsIntent(support).launchUrl -> ACTION_VIEW",
                                "pkg=" + safePackageName(ctx) + " url=" + uri);
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(i);
                        param.setResult(null);
                    }
                }
            });
        }
    }

    private void hookStartActivity(final String currentPackageName) {
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class,
                int.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;

                        if (isCustomTabIntent(intent) && !isInCurrentPackage(intent, currentPackageName)) {
                            logInfo("startActivityForResult CustomTab intent",
                                    buildIntentSummary(intent));
                            intent.setComponent((ComponentName) null);
                            intent.setAction(Intent.ACTION_VIEW);
                            stripCustomTabExtras(intent);
                            param.args[0] = intent;
                        }
                    }
                }
        );
    }

    private void hookViaTrampoline(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"mark.via".equals(lpparam.packageName) && !"mark.via.gp".equals(lpparam.packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "mark.via.Trampoline",
                    lpparam.classLoader,
                    "onCreate",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity act = (Activity) param.thisObject;
                            Intent intent = act.getIntent();
                            if (intent == null) return;

                            Uri url = intent.getData();
                            boolean isCustomTab = intent.getBooleanExtra("CUSTOM_TAB", false)
                                    || hasCustomTabExtras(intent);

                            if (isCustomTab && isWebScheme(url)) {
                                logInfo("Trampoline opened via CustomTab",
                                        "url=" + url + " " + buildIntentSummary(intent));

                                Intent newIntent = new Intent(intent);
                                newIntent.setComponent(new ComponentName(lpparam.packageName, "mark.via.Shell"));
                                newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                newIntent.setData(url);

                                act.startActivity(newIntent);
                                act.finish();
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("Trampoline hook skipped (Class not found or method changed)");
        }
    }

    private boolean isWebScheme(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isInCurrentPackage(Intent intent, String currentPackageName) {
        if (currentPackageName == null) return false;

        ComponentName component = intent.getComponent();
        if (component != null && currentPackageName.equals(component.getPackageName())) {
            return true;
        }

        return currentPackageName.equals(intent.getPackage());
    }

    private boolean isCustomTabIntent(Intent intent) {
        if (!isWebScheme(intent.getData())) {
            return false;
        }

        ComponentName component = intent.getComponent();
        if (component != null) {
            String className = component.getClassName();
            if (className != null && className.contains("CustomTab")) {
                return true;
            }
        }

        Bundle extras = intent.getExtras();
        if (extras == null) return false;

        for (String key : extras.keySet()) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                return true;
            }
        }
        return false;
    }

    private void stripCustomTabExtras(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;

        for (String key : extras.keySet().toArray(new String[0])) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                intent.removeExtra(key);
            }
        }
    }

    private boolean hasCustomTabExtras(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return false;

        for (String key : extras.keySet()) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                return true;
            }
        }
        return false;
    }

    private String buildIntentSummary(Intent intent) {
        StringBuilder sb = new StringBuilder(128);
        ComponentName component = intent.getComponent();
        sb.append("act=").append(intent.getAction());
        if (intent.getData() != null) {
            sb.append(" dat=").append(intent.getData());
        }
        if (component != null) {
            sb.append(" cmp=").append(component.flattenToShortString());
        }
        sb.append(" flg=0x").append(Integer.toHexString(intent.getFlags()));

        String extraKeys = findCustomTabExtraKeys(intent);
        if (extraKeys != null) {
            sb.append(" extras=[").append(extraKeys).append("]");
        }
        return sb.toString();
    }

    private String findCustomTabExtraKeys(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return null;

        StringBuilder sb = new StringBuilder(64);
        int count = 0;
        for (String key : extras.keySet()) {
            if (key.startsWith("androidx.browser.customtabs.extra.")
                    || key.startsWith("android.support.customtabs.extra.")) {
                if (count > 0) sb.append(",");
                if (count >= 5) {
                    sb.append("...");
                    break;
                }
                sb.append(key);
                count++;
            }
        }
        return count == 0 ? null : sb.toString();
    }

    private void logInfo(String tag, String msg) {
        XposedBridge.log("[FuckCustomTab] " + tag + ": " + msg);
    }

    private String safePackageName(Context ctx) {
        try {
            return ctx.getPackageName();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
