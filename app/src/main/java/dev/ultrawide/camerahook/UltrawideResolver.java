package dev.ultrawide.camerahook;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menentukan ID kamera belakang utama dan ID kamera ultrawide
 * dari data CameraCharacteristics (tanpa bergantung pada nama class
 * aplikasi kamera yang di-obfuscate).
 */
final class UltrawideResolver {

    /** Kamera ultrawide dianggap valid jika FOV-nya minimal segini derajat lebih lebar dari kamera utama. */
    private static final double MIN_FOV_GAIN_DEG = 15.0;

    static final class Result {
        /** ID kamera belakang utama (yang akan dialihkan). */
        final String mainId;
        /** ID kamera ultrawide; null jika tidak ditemukan. */
        final String ultraId;
        /** false = hasil belum final (mis. daftar kamera masih kosong), coba lagi nanti. */
        final boolean isFinal;
        /** Baris laporan untuk log. */
        final List<String> report;

        Result(String mainId, String ultraId, boolean isFinal, List<String> report) {
            this.mainId = mainId;
            this.ultraId = ultraId;
            this.isFinal = isFinal;
            this.report = report;
        }
    }

    private static final class Cam {
        final String id;
        final Double fov;

        Cam(String id, Double fov) {
            this.id = id;
            this.fov = fov;
        }
    }

    private UltrawideResolver() {
    }

    static Result resolve(CameraManager mgr, String forcedMain, String forcedUltra)
            throws CameraAccessException {
        List<String> report = new ArrayList<>();
        String[] ids = mgr.getCameraIdList();
        if (ids == null || ids.length == 0) {
            report.add("daftar kamera kosong, akan dicoba lagi");
            return new Result(null, null, false, report);
        }

        List<Cam> backs = new ArrayList<>();
        for (String id : ids) {
            CameraCharacteristics c = mgr.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            Double fov = horizontalFov(c);
            report.add("id=" + id
                    + " facing=" + facingName(facing)
                    + " hFov=" + (fov == null ? "?" : String.format(Locale.US, "%.1f", fov)));
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                backs.add(new Cam(id, fov));
            }
        }

        if (backs.isEmpty() && isEmpty(forcedMain)) {
            report.add("tidak ada kamera belakang yang terlihat");
            return new Result(null, null, true, report);
        }

        Cam main;
        if (!isEmpty(forcedMain)) {
            main = find(backs, forcedMain);
            if (main == null) main = new Cam(forcedMain, null);
        } else {
            main = lowestId(backs);
        }

        Cam ultra = null;
        if (!isEmpty(forcedUltra)) {
            ultra = find(backs, forcedUltra);
            if (ultra == null) ultra = new Cam(forcedUltra, null);
        } else {
            for (Cam c : backs) {
                if (c.id.equals(main.id) || c.fov == null) continue;
                if (main.fov != null && c.fov < main.fov + MIN_FOV_GAIN_DEG) continue;
                if (ultra == null || c.fov > ultra.fov) ultra = c;
            }
        }

        if (ultra == null || ultra.id.equals(main.id)) {
            report.add("kamera ultrawide terpisah tidak ditemukan (utama=" + main.id + ")");
            return new Result(main.id, null, true, report);
        }
        report.add("dipilih: utama=" + main.id + " -> ultrawide=" + ultra.id);
        return new Result(main.id, ultra.id, true, report);
    }

    /** Field of view horizontal (derajat) dari panjang fokus terpendek dan lebar sensor. */
    static Double horizontalFov(CameraCharacteristics c) {
        float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focals == null || focals.length == 0 || size == null || size.getWidth() <= 0f) {
            return null;
        }
        float min = focals[0];
        for (float f : focals) {
            if (f < min) min = f;
        }
        if (min <= 0f) return null;
        return Math.toDegrees(2.0 * Math.atan(size.getWidth() / (2.0 * min)));
    }

    private static Cam lowestId(List<Cam> cams) {
        Cam best = null;
        for (Cam c : cams) {
            if (best == null || order(c.id) < order(best.id)
                    || (order(c.id) == order(best.id) && c.id.compareTo(best.id) < 0)) {
                best = c;
            }
        }
        return best;
    }

    private static int order(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Cam find(List<Cam> cams, String id) {
        for (Cam c : cams) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String facingName(Integer facing) {
        if (facing == null) return "?";
        if (facing == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        return "external";
    }
}
