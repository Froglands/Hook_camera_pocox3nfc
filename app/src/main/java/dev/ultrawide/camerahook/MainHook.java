package dev.ultrawide.camerahook;

import android.hardware.camera2.CameraManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Modul LSPosed: saat com.android.camera meminta kamera belakang utama (ID "0" yang tidak ada
 * karena kamera utama rusak), permintaan dialihkan ke kamera ultrawide (0.6x).
 *
 * Pengalihan dilakukan di level framework Camera2 (CameraManager), jadi tidak
 * bergantung pada nama class/method internal aplikasi kamera yang berbeda
 * di tiap versi MIUI/HyperOS.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.android.camera";
    private static final String TAG = "[UltrawideCam] ";

    /**
     * Isi manual jika deteksi otomatis salah. Contoh: FORCED_ULTRAWIDE_ID = "21".
     * Kosong ("") = deteksi otomatis (kamera belakang dengan FOV terlebar).
     */
    private static final String FORCED_ULTRAWIDE_ID = "";
    /**
     * Opsional: ID kamera yang ADA di sistem tetapi ingin dipaksa dialihkan ke ultrawide.
     * Kosong ("") = hanya ID yang tidak ada di sistem (mis. "0") yang dialihkan.
     */
    private static final String FORCED_MAIN_ID = "";

    private static final int MAX_REDIRECT_LOGS = 30;

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    private static volatile boolean resolved;
    private static volatile UltrawideResolver.Result result;
    private static volatile int redirectLogs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        try {
            XposedBridge.hookAllMethods(CameraManager.class, "openCamera", OPEN_HOOK);
            XposedBridge.hookAllMethods(CameraManager.class, "openCameraForUid", OPEN_HOOK);
            XposedBridge.hookAllMethods(CameraManager.class, "openCameraDeviceUserAsync", OPEN_HOOK);
            XposedBridge.hookAllMethods(CameraManager.class, "getCameraCharacteristics", CHARACTERISTICS_HOOK);
            log("hook terpasang di " + lpparam.packageName);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    /** Ganti ID kamera utama (yang tidak ada/rusak) dengan ID ultrawide saat kamera dibuka. */
    private static final XC_MethodHook OPEN_HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null || param.args.length == 0) return;
                if (!(param.args[0] instanceof String)) return;
                if (!(param.thisObject instanceof CameraManager)) return;

                String target = targetFor((CameraManager) param.thisObject, (String) param.args[0]);
                if (target != null) {
                    logRedirect("openCamera " + param.args[0] + " -> " + target);
                    param.args[0] = target;
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    /**
     * Saat aplikasi menanyakan spesifikasi kamera utama (ID "0" yang tidak ada, sehingga biasanya
     * melempar IllegalArgumentException), berikan spesifikasi ultrawide supaya aplikasi
     * bisa lanjut dan memilih ukuran preview/foto yang cocok dengan kamera yang dibuka.
     */
    private static final XC_MethodHook CHARACTERISTICS_HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (RESOLVING.get() != null) return;
                if (param.args == null || param.args.length == 0) return;
                if (!(param.args[0] instanceof String)) return;
                if (!(param.thisObject instanceof CameraManager)) return;

                CameraManager mgr = (CameraManager) param.thisObject;
                String target = targetFor(mgr, (String) param.args[0]);
                if (target != null) {
                    logRedirect("getCameraCharacteristics " + param.args[0] + " -> " + target);
                    param.setResult(mgr.getCameraCharacteristics(target));
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    /** ID tujuan jika permintaan ke {@code requested} harus dialihkan, selain itu null. */
    private static String targetFor(CameraManager mgr, String requested) {
        resolve(mgr);
        UltrawideResolver.Result r = result;
        return r == null ? null : r.redirect(requested);
    }

    private static void resolve(CameraManager mgr) {
        if (resolved) return;
        synchronized (LOCK) {
            if (resolved) return;
            RESOLVING.set(Boolean.TRUE);
            try {
                UltrawideResolver.Result r =
                        UltrawideResolver.resolve(mgr, FORCED_MAIN_ID, FORCED_ULTRAWIDE_ID);
                for (String line : r.report) log(line);
                if (r.isFinal) {
                    result = r;
                    resolved = true;
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            } finally {
                RESOLVING.remove();
            }
        }
    }

    private static void logRedirect(String msg) {
        if (redirectLogs < MAX_REDIRECT_LOGS) {
            redirectLogs++;
            log(msg);
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
