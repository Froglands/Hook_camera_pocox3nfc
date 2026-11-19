package dev.ultrawide.camerahook;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Menentukan kamera ultrawide dari data CameraCharacteristics dan memutuskan
 * ID kamera mana yang harus dialihkan ke kamera tersebut.
 *
 * Kasus utama (Poco X3 NFC dengan kamera utama rusak): aplikasi kamera selalu meminta
 * kamera belakang virtual "0", padahal ID itu tidak ada di sistem (daftar hanya 1, 21, 22, 25, 101).
 * Permintaan ke ID yang tidak ada itu dialihkan ke kamera belakang terlebar (ultrawide).
 */
final class UltrawideResolver {

    static final class Result {
        /** ID kamera ultrawide tujuan pengalihan; null jika tidak ada kamera belakang. */
        final String ultraId;
        /** Semua ID kamera yang benar-benar ada di sistem. */
        final Set<String> knownIds;
        /** ID yang dipaksa dialihkan walau ada di sistem (opsional). */
        final String forcedMain;
        /** false = hasil belum final (daftar kamera masih kosong), coba lagi nanti. */
        final boolean isFinal;
        /** Baris laporan untuk log. */
        final List<String> report;

        Result(String ultraId, Set<String> knownIds, String forcedMain, boolean isFinal,
               List<String> report) {
            this.ultraId = ultraId;
            this.knownIds = knownIds;
            this.forcedMain = forcedMain;
            this.isFinal = isFinal;
            this.report = report;
        }

        /** Mengembalikan ID tujuan jika permintaan ke {@code requested} harus dialihkan, selain itu null. */
        String redirect(String requested) {
            if (ultraId == null || requested == null || requested.equals(ultraId)) return null;
            if (!isEmpty(forcedMain) && requested.equals(forcedMain)) return ultraId;
            if (knownIds.contains(requested)) return null;
            if (isLegacyBackId(requested)) return ultraId;
            return null;
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
        Set<String> known = new HashSet<>();
        String[] ids = mgr.getCameraIdList();
        if (ids == null || ids.length == 0) {
            report.add("daftar kamera kosong, akan dicoba lagi");
            return new Result(null, known, forcedMain, false, report);
        }

        List<Cam> backs = new ArrayList<>();
        for (String id : ids) {
            known.add(id);
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

        Cam ultra = null;
        if (!isEmpty(forcedUltra)) {
            ultra = find(backs, forcedUltra);
            if (ultra == null) ultra = new Cam(forcedUltra, null);
        } else {
            // Kamera belakang dengan field of view terlebar = ultrawide (0.6x).
            for (Cam c : backs) {
                if (ultra == null) {
                    ultra = c;
                } else if (c.fov != null && (ultra.fov == null || c.fov > ultra.fov)) {
                    ultra = c;
                }
            }
        }

        if (ultra == null) {
            report.add("tidak ada kamera belakang yang terlihat");
            return new Result(null, known, forcedMain, true, report);
        }
        report.add("ultrawide dipilih: id=" + ultra.id
                + "; permintaan ke id yang tidak ada (mis. 0) akan dialihkan ke sini");
        return new Result(ultra.id, known, forcedMain, true, report);
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

    /** ID kamera belakang gaya lama: angka 0..99 (kamera depan MIUI memakai 1 dan 100+). */
    private static boolean isLegacyBackId(String id) {
        try {
            int n = Integer.parseInt(id);
            return n >= 0 && n < 100;
        } catch (NumberFormatException e) {
            return false;
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
