package com.picoxr.appmanagerfix;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class AppManagerLibraryFix implements IXposedHookLoadPackage {
    private static final String TAG = "PicoAppManagerFix";
    private static final String TARGET_PACKAGE = "com.pvr.appmanager";
    private static final String UNKNOWN_TAB = "unknown";
    private static final String APPS_TAB = "app";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            hookCategoryMapping(lpparam.classLoader);
            hookUnknownNavigation(lpparam.classLoader);
            hookUnknownTabRedirect(lpparam.classLoader);
            // A/B switch: comment out the line below to build the "apps-mode" (pure remap) variant.
            hookStoreSourceBypass(lpparam.classLoader);
            log("loaded into " + lpparam.processName);
        } catch (Throwable throwable) {
            log("initialization failed: " + throwable);
        }
    }

    private static void hookCategoryMapping(ClassLoader classLoader) {
        Class<?> filter = XposedHelpers.findClass("com.pvr.appmanager.Filter", classLoader);
        Class<?> appData = XposedHelpers.findClass("com.pvr.appmanager.entity.AppData", classLoader);
        XposedHelpers.findAndHookMethod(filter, "judgeSourceType", appData, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object data = param.args[0];
                String source = (String) XposedHelpers.callMethod(data, "getSource");
                String subSource = (String) XposedHelpers.callMethod(data, "getSubSource");
                if (AppCategoryMapper.shouldMoveToApps(source, subSource)) {
                    XposedHelpers.callMethod(data, "setSubSource", AppCategoryMapper.NORMAL_APP);
                    Object app = XposedHelpers.callMethod(data, "getApp");
                    String packageName = (String) XposedHelpers.callMethod(app, "v");
                    log("moved " + packageName + " from unknown to apps");
                }
            }
        });
    }

    private static void hookUnknownNavigation(ClassLoader classLoader) {
        Class<?> helper = XposedHelpers.findClass("com.pvr.appmanager.utils.menu.AppMenuTabHelper", classLoader);
        Class<?> navigation = XposedHelpers.findClass("com.bytedance.osui.grouplist.OSUISideNavigation", classLoader);
        Class<?> toolTips = XposedHelpers.findClass("com.pvr.appmanager.utils.ToolTipsHelper", classLoader);
        XposedHelpers.findAndHookMethod(helper, "addMenuTabs", Context.class, navigation, toolTips,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        hideUnknownTab((View) param.args[1]);
                    }
                });
    }

    private static void hookUnknownTabRedirect(ClassLoader classLoader) {
        Class<?> activity = XposedHelpers.findClass("com.pvr.appmanager.AllAppActivity", classLoader);
        XposedHelpers.findAndHookMethod(activity, "changeRecyclerViewType", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (UNKNOWN_TAB.equals(param.args[0])) {
                            param.args[0] = APPS_TAB;
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(activity, "getCategoryIndex", boolean.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (UNKNOWN_TAB.equals(param.args[1])) {
                            param.args[1] = APPS_TAB;
                        }
                    }
                });
    }

    private static void hideUnknownTab(View view) {
        if (UNKNOWN_TAB.equals(view.getTag())) {
            view.setVisibility(View.GONE);
            log("hidden unknown navigation item");
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            hideUnknownTab(group.getChildAt(index));
        }
    }

    // Merged addition (from thoricelli's no_unknown_sources fix): force Filter.isStoreSource()
    // to true so entries never fall through to the "other/sdk_app" classification that feeds
    // the Unknown section. Remove this call to fall back to the pure remap-only behavior.
    private static void hookStoreSourceBypass(ClassLoader classLoader) {
        try {
            Class<?> filter = XposedHelpers.findClass("com.pvr.appmanager.Filter", classLoader);
            Class<?> appDataClass = XposedHelpers.findClass("com.pvr.appmanager.entity.AppData", classLoader);
            XposedHelpers.findAndHookMethod(filter, "isStoreSource", appDataClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(Boolean.TRUE);
                }
            });
            log("store-source bypass installed");
        } catch (Throwable throwable) {
            log("store-source bypass failed: " + throwable);
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
