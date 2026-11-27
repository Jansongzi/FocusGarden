package com.focusgarden.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Renders a 3D spherical lunar companion with crater bump-mapping relief,
 * limb darkening, and Earthshine shading from {@code R.drawable.moon_surface}.
 */
final class MoonPhaseRenderer {
    static final int SIZE = 420;

    private static volatile SourceData cachedSource;
    private static volatile Bitmap cachedLightThumb;
    private static volatile Bitmap cachedDarkThumb;

    private int[] pixels;
    private Bitmap outBitmap;
    private float lastPhase = -1f;
    private boolean lastDark;

    static String phaseName(float ratio) {
        if (ratio < 0.12f) return "新月";
        if (ratio < 0.38f) return "娥眉月";
        if (ratio < 0.58f) return "上弦月";
        if (ratio < 0.88f) return "盈凸月";
        return "满月";
    }

    static Bitmap staticThumb(Context context, boolean dark) {
        Bitmap existing = dark ? cachedDarkThumb : cachedLightThumb;
        if (existing != null && !existing.isRecycled()) return existing;
        synchronized (MoonPhaseRenderer.class) {
            existing = dark ? cachedDarkThumb : cachedLightThumb;
            if (existing != null && !existing.isRecycled()) return existing;
            MoonPhaseRenderer renderer = new MoonPhaseRenderer();
            Bitmap rendered = renderer.render(context, 0.58f, dark);
            if (rendered == null) return null;
            Bitmap copy = rendered.copy(Bitmap.Config.ARGB_8888, false);
            if (dark) cachedDarkThumb = copy;
            else cachedLightThumb = copy;
            return copy;
        }
    }

    Bitmap render(Context context, float displayedRatio, boolean dark) {
        float clampedRatio = Math.max(0f, Math.min(1f, displayedRatio));
        float phase = 0.08f + 0.92f * clampedRatio;
        if (outBitmap != null && !outBitmap.isRecycled()
                && Math.abs(phase - lastPhase) < 0.004f && dark == lastDark) {
            return outBitmap;
        }
        SourceData src = source(context);
        if (src == null) return null;

        if (pixels == null) pixels = new int[SIZE * SIZE];
        if (outBitmap == null || outBitmap.isRecycled()) {
            outBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        }

        // Waxing solar illumination direction from right-front -> full frontal
        double theta = Math.PI * (1.0 - phase);
        float lx = (float) Math.sin(theta);
        float ly = -0.14f * lx;
        float lz = (float) Math.cos(theta);
        float lLen = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (lLen > 1e-4f) {
            lx /= lLen;
            ly /= lLen;
            lz /= lLen;
        }

        float fullFactor = Math.max(0f, Math.min(1f, (phase - 0.80f) / 0.20f));
        float bumpBlend = 0.45f * fullFactor;
        float earth = dark ? 0.11f : 0.14f;
        float floorR = dark ? 10f : 18f;
        float floorG = dark ? 14f : 24f;
        float floorB = dark ? 24f : 36f;

        int n = src.count;
        int[] idx = src.idx;
        int[] alphaMask = src.alphaMask;
        short[] ar = src.r;
        short[] ag = src.g;
        short[] ab = src.b;
        float[] su = src.su;
        float[] sv = src.sv;
        float[] sz = src.sz;
        float[] nx = src.nx;
        float[] ny = src.ny;
        float[] nz = src.nz;
        float[] limb = src.limb;

        for (int k = 0; k < n; k++) {
            float dotSphere = su[k] * lx + sv[k] * ly + sz[k] * lz;
            float dotBump = nx[k] * lx + ny[k] * ly + nz[k] * lz;
            float dotEff = dotBump + (dotSphere - dotBump) * bumpBlend;

            // Soft anti-aliased solar terminator
            float t = (dotSphere + 0.032f) / 0.145f;
            if (t < 0f) t = 0f;
            else if (t > 1f) t = 1f;
            float litMask = t * t * (3f - 2f * t);

            float lunarDiffuse = dotEff > 0f ? (float) Math.pow(dotEff, 0.32) : 0f;
            float rimLift = Math.max(0f, dotBump - dotSphere) * 0.42f * (1f - 0.55f * fullFactor);
            float sunlit = litMask * (0.42f + 0.92f * lunarDiffuse + rimLift) * (1.0f + 0.08f * fullFactor);
            float darkWeight = 1f - litMask;
            float limbK = limb[k];

            float shadeR = (earth * 0.80f + sunlit * (1.0f - earth * 0.80f)) * limbK;
            float shadeG = (earth * 0.87f + sunlit * (1.0f - earth * 0.87f)) * limbK;
            float shadeB = (earth * 1.00f + sunlit * (1.0f - earth * 1.00f)) * limbK;

            int outR = clampByte(ar[k] * shadeR + floorR * darkWeight * limbK);
            int outG = clampByte(ag[k] * shadeG + floorG * darkWeight * limbK);
            int outB = clampByte(ab[k] * shadeB + floorB * darkWeight * limbK);
            pixels[idx[k]] = alphaMask[k] | (outR << 16) | (outG << 8) | outB;
        }

        outBitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        lastPhase = phase;
        lastDark = dark;
        return outBitmap;
    }

    private static int clampByte(float v) {
        int iv = Math.round(v);
        return iv < 0 ? 0 : Math.min(255, iv);
    }

    private static SourceData source(Context context) {
        SourceData s = cachedSource;
        if (s != null) return s;
        synchronized (MoonPhaseRenderer.class) {
            if (cachedSource != null) return cachedSource;
            cachedSource = buildSource(context.getApplicationContext());
            return cachedSource;
        }
    }

    private static SourceData buildSource(Context context) {
        Bitmap decoded = BitmapFactory.decodeResource(context.getResources(), R.drawable.moon_surface);
        if (decoded == null) return null;
        Bitmap scaled = decoded.getWidth() == SIZE && decoded.getHeight() == SIZE
                ? decoded
                : Bitmap.createScaledBitmap(decoded, SIZE, SIZE, true);
        int[] raw = new int[SIZE * SIZE];
        scaled.getPixels(raw, 0, SIZE, 0, 0, SIZE, SIZE);
        if (scaled != decoded) decoded.recycle();
        scaled.recycle();

        float[] lum = new float[SIZE * SIZE];
        for (int i = 0; i < raw.length; i++) {
            int c = raw[i];
            int r = (c >> 16) & 0xff;
            int g = (c >> 8) & 0xff;
            int b = c & 0xff;
            lum[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        }

        float half = (SIZE - 1) * 0.5f;
        float rad = SIZE * 0.5f - 1.5f;
        int cap = 0;
        for (int i = 0; i < raw.length; i++) {
            if ((raw[i] >>> 24) > 0) cap++;
        }

        int[] idx = new int[cap];
        int[] alphaMask = new int[cap];
        short[] rArr = new short[cap];
        short[] gArr = new short[cap];
        short[] bArr = new short[cap];
        float[] su = new float[cap];
        float[] sv = new float[cap];
        float[] sz = new float[cap];
        float[] nx = new float[cap];
        float[] ny = new float[cap];
        float[] nz = new float[cap];
        float[] limb = new float[cap];

        int count = 0;
        for (int y = 0; y < SIZE; y++) {
            float v = (y - half) / rad;
            int ym = Math.max(0, y - 1);
            int yp = Math.min(SIZE - 1, y + 1);
            for (int x = 0; x < SIZE; x++) {
                int c = raw[y * SIZE + x];
                int a = (c >>> 24) & 0xff;
                if (a == 0) continue;

                float u = (x - half) / rad;
                float r2 = u * u + v * v;
                float z = r2 < 1f ? (float) Math.sqrt(1f - r2) : 0f;

                int xm = Math.max(0, x - 1);
                int xp = Math.min(SIZE - 1, x + 1);
                float gx = (lum[ym * SIZE + xp] + 2f * lum[y * SIZE + xp] + lum[yp * SIZE + xp])
                        - (lum[ym * SIZE + xm] + 2f * lum[y * SIZE + xm] + lum[yp * SIZE + xm]);
                float gy = (lum[yp * SIZE + xm] + 2f * lum[yp * SIZE + x] + lum[yp * SIZE + xp])
                        - (lum[ym * SIZE + xm] + 2f * lum[ym * SIZE + x] + lum[ym * SIZE + xp]);
                // Softly compress large maria albedo steps while accentuating small crater rims
                gx = gx / (1f + 2.2f * Math.abs(gx));
                gy = gy / (1f + 2.2f * Math.abs(gy));

                float bx = u - gx * 0.36f;
                float by = v - gy * 0.36f;
                float bz = Math.max(0.08f, z);
                float bLen = (float) Math.sqrt(bx * bx + by * by + bz * bz);

                idx[count] = y * SIZE + x;
                alphaMask[count] = a << 24;
                rArr[count] = (short) ((c >> 16) & 0xff);
                gArr[count] = (short) ((c >> 8) & 0xff);
                bArr[count] = (short) (c & 0xff);
                su[count] = u;
                sv[count] = v;
                sz[count] = z;
                nx[count] = bx / bLen;
                ny[count] = by / bLen;
                nz[count] = bz / bLen;
                limb[count] = 0.91f + 0.09f * (float) Math.pow(Math.max(0.02f, z), 0.35);
                count++;
            }
        }
        return new SourceData(count, idx, alphaMask, rArr, gArr, bArr, su, sv, sz, nx, ny, nz, limb);
    }

    private record SourceData(
            int count,
            int[] idx,
            int[] alphaMask,
            short[] r,
            short[] g,
            short[] b,
            float[] su,
            float[] sv,
            float[] sz,
            float[] nx,
            float[] ny,
            float[] nz,
            float[] limb) {}
}
