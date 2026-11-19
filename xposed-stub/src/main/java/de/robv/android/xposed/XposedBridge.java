package de.robv.android.xposed;

import java.util.Set;

/** Stub compile-only dari Xposed API. */
public final class XposedBridge {

    private XposedBridge() {
    }

    public static synchronized void log(String text) {
    }

    public static synchronized void log(Throwable t) {
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName,
                                                          XC_MethodHook callback) {
        return null;
    }
}
