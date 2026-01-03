package via.fuckcustomtab.frisk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class CustomTabHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals("mark.via.gp") && !lpparam.packageName.equals("mark.via"))
            return;

        XposedBridge.log("Via CCT hook loaded for: " + lpparam.packageName);

        // 尝试禁用 CustomTabs 服务 (可选，防止部分 App 认为 Via 支持 CCT)
        try {
            Class<?> svc = XposedHelpers.findClass("mark.via.service.CustomTabsConnectionService", lpparam.classLoader);
            safeReturnConstant(svc, "d", false);
            safeReturnConstant(svc, "e", false);
            safeReturnConstant(svc, "l", false);
            safeReturnConstant(svc, "m", false);
        } catch (Throwable t) {
            XposedBridge.log("CustomTabsConnectionService hooks skipped (Class not found or method changed)");
        }

        // 全局拦截 startActivity，实现无缝重定向 (替代 Trampoline hook)
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "startActivityForResult",
                Intent.class,
                int.class,
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;

                        ComponentName component = intent.getComponent();
                        // 检查 Intent 是否显式指向了 CustomTab
                        if (component != null &&
                                (component.getClassName().contains("CustomTab") ||
                                        "mark.via.CustomTab".equals(component.getClassName()))) {

                            XposedBridge.log("Intercepted start intent for CustomTab, redirecting to Shell...");

                            // 重定向到主界面 (Shell)
                            intent.setComponent(new ComponentName(lpparam.packageName, "mark.via.Shell"));

                            // 确保保留数据 (URL)
                            if (intent.getData() == null && intent.getDataString() != null) {
                                intent.setData(Uri.parse(intent.getDataString()));
                            }

                            // 添加 New Task 标志，确保在浏览器主进程中打开
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            // 修改参数，让系统启动 Shell
                            param.args[0] = intent;
                        }
                    }
                }
        );

        // 如果上面的拦截漏了，CustomTab 还是启动了，就在 onCreate 里关掉它
        XposedHelpers.findAndHookMethod(
                "mark.via.CustomTab",
                lpparam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 不要在这里调用 param.setResult(null)
                        // 必须让 super.onCreate() 执行，否则会报 SuperNotCalledException。

                        Activity act = (Activity) param.thisObject;
                        Intent intent = act.getIntent();

                        // 检查是否是被外部调起的 Custom Tab
                        boolean isCustomTab = intent.getBooleanExtra("CUSTOM_TAB", true);
                        Uri url = intent.getData();

                        if (url != null && isCustomTab) {
                            XposedBridge.log("CustomTab opened via fallback hook. Redirecting now.");

                            Intent newIntent = new Intent(intent);
                            newIntent.setComponent(new ComponentName(lpparam.packageName, "mark.via.Shell"));
                            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            newIntent.setData(url);

                            act.startActivity(newIntent);
                            act.finish(); // 启动新 Activity 后立即结束当前 Activity
                        }
                    }
                }
        );
    }

    // 安全地 Hook 并返回常量，防止因方法名变动导致崩溃
    private void safeReturnConstant(Class<?> clazz, String methodName, Object ret) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, XC_MethodReplacement.returnConstant(ret));
        } catch (Throwable t) {
            // 忽略方法未找到的错误
        }
    }
}
