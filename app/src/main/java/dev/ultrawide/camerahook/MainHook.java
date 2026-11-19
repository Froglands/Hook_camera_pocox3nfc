package dev.ultrawide.camerahook;

import android.hardware.camera2.CameraManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Modul LSPosed: saat com.android.camera membuka kamera belakang utama,
 * alihkan ke kamera ultrawide (0.6x). Karena kamera utama tidak pernah dibuka,
 * aplikasi kamera tidak freeze lagi.
 *
 * Pengalihan dilakukan di level framework Camera2 (CameraManager), jadi tidak
 * bergantung pada nama class/method internal aplikasi kamera yang berbeda
 * di tiap versi MIUI/HyperOS.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.android.camera";
    private static final String TAG = "[UltrawideCam] ";

    /**
     * Isi manual jika deteksi otomatis salah. Contoh: FORCED_ULTRAWIDE_ID = "2".
     * Kosong ("") = deteksi otomatis. Lihat log LSPosed untuk daftar ID kamera.
     */
    private static final String FORCED_ULTRAWIDE_ID = "";
    /** Kosong = pakai ID kamera belakang terendah (biasanya "0"). */
    private static final String FORCED_MAIN_ID = "";

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    private static volatile boolean resolved;
    private static volatile String mainId;
    private static volatile String ultraId;

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

    /** Ganti ID kamera utama dengan ID ultrawide saat kamera dibuka. */
    private static final XC_MethodHook OPEN_HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null || param.args.length == 0) return;
                if (!(param.args[0] instanceof String)) return;
                if (!(param.thisObject instanceof CameraManager)) return;

                resolve((CameraManager) param.thisObject);
                String main = mainId;
                String ultra = ultraId;
                if (ultra != null && param.args[0].equals(main)) {
                    log("openCamera " + main + " -> " + ultra);
                    param.args[0] = ultra;
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    /**
     * Saat aplikasi menanyakan spesifikasi kamera utama, berikan spesifikasi ultrawide
     * supaya ukuran preview/foto yang dipilih aplikasi cocok dengan kamera yang benar-benar dibuka.
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
                resolve(mgr);
                String main = mainId;
                String ultra = ultraId;
                if (ultra != null && param.args[0].equals(main)) {
                    param.setResult(mgr.getCameraCharacteristics(ultra));
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

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
                    mainId = r.mainId;
                    ultraId = r.ultraId;
                    resolved = true;
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            } finally {
                RESOLVING.remove();
            }
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + msg);
    }
}
