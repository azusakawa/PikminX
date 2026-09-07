package com.pikminx.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Uses the first visible nectar card as visual corroboration for OCR selection. */
final class NectarTemplateMatcher {
    enum Status { MATCHED, CONFLICT, UNKNOWN, UNAVAILABLE }

    record Evidence(Status status, float expectedScore, float bestScore) {}

    private record Template(String assetName, int[] pixels, float[] centered, double energy) {}

    private static final String TAG = "PikminX";
    private static final String ASSET_DIRECTORY = "nectar_templates";
    private static final int TEMPLATE_SIZE = 80;
    private static final float ICON_SIZE_RATIO = 0.19f;
    private static final float MIN_MATCH_SCORE = 0.64f;
    private static final float MAX_EXPECTED_GAP = 0.07f;
    private static final float MIN_CONFLICT_SCORE = 0.72f;
    private static final float MIN_CONFLICT_GAP = 0.10f;
    private static final int[] SHIFTS = {-4, 0, 4};
    private static final int[] MASK_OFFSETS = createMaskOffsets();

    private final Context context;
    private volatile Map<String, Template> templates;
    private volatile boolean loadFailed;

    NectarTemplateMatcher(Context context) {
        this.context = context.getApplicationContext();
    }

    Evidence match(Bitmap screen, String target, int centerX, int centerY) {
        Map<String, Template> loaded = templates();
        String expectedAsset = assetNameFor(target);
        Template expected = loaded.get(expectedAsset);
        if (expected == null) {
            return new Evidence(Status.UNAVAILABLE, 0f, 0f);
        }

        int side = Math.max(TEMPLATE_SIZE, Math.round(screen.getWidth() * ICON_SIZE_RATIO));
        float expectedScore = 0f;
        float bestScore = 0f;
        String bestAsset = "";
        for (int shiftY : SHIFTS) {
            for (int shiftX : SHIFTS) {
                int left = centerX - side / 2 + Math.round(side * shiftX / 80f);
                int top = centerY - side / 2 + Math.round(side * shiftY / 80f);
                if (left < 0 || top < 0
                        || left + side > screen.getWidth()
                        || top + side > screen.getHeight()) {
                    continue;
                }
                Bitmap crop = Bitmap.createBitmap(screen, left, top, side, side);
                Bitmap normalized = Bitmap.createScaledBitmap(
                        crop, TEMPLATE_SIZE, TEMPLATE_SIZE, true);
                int[] pixels = new int[TEMPLATE_SIZE * TEMPLATE_SIZE];
                normalized.getPixels(
                        pixels, 0, TEMPLATE_SIZE, 0, 0, TEMPLATE_SIZE, TEMPLATE_SIZE);
                if (normalized != crop) {
                    normalized.recycle();
                }
                crop.recycle();

                for (Template template : loaded.values()) {
                    float score = score(pixels, template);
                    if (template.assetName().equals(expectedAsset)) {
                        expectedScore = Math.max(expectedScore, score);
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestAsset = template.assetName();
                    }
                }
            }
        }
        Status status = classify(
                true, expectedScore, bestScore, expectedAsset.equals(bestAsset));
        return new Evidence(status, expectedScore, bestScore);
    }

    static String assetNameFor(String target) {
        String displayName = FeedScreenAnalyzer.nectarDisplayName(target);
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        if ("白色精華".equals(displayName)) {
            return "精華.png";
        }
        return displayName + ".png";
    }

    static Status classify(
            boolean expectedAvailable,
            float expectedScore,
            float bestScore,
            boolean expectedIsBest) {
        if (!expectedAvailable) {
            return Status.UNAVAILABLE;
        }
        if (expectedScore >= MIN_MATCH_SCORE
                && (expectedIsBest || bestScore - expectedScore <= MAX_EXPECTED_GAP)) {
            return Status.MATCHED;
        }
        if (!expectedIsBest
                && bestScore >= MIN_CONFLICT_SCORE
                && bestScore - expectedScore >= MIN_CONFLICT_GAP) {
            return Status.CONFLICT;
        }
        return Status.UNKNOWN;
    }

    static int requiredStableFrames(Status status, int normalFrames) {
        return status == Status.UNKNOWN ? normalFrames + 1 : normalFrames;
    }

    private Map<String, Template> templates() {
        Map<String, Template> current = templates;
        if (current != null || loadFailed) {
            return current == null ? Map.of() : current;
        }
        synchronized (this) {
            if (templates == null && !loadFailed) {
                try {
                    templates = Map.copyOf(loadTemplates());
                } catch (Exception error) {
                    loadFailed = true;
                    Log.w(TAG, "FEED_NECTAR_TEMPLATE event=load-failed", error);
                }
            }
            return templates == null ? Map.of() : templates;
        }
    }

    private Map<String, Template> loadTemplates() throws Exception {
        String[] names = context.getAssets().list(ASSET_DIRECTORY);
        if (names == null || names.length == 0) {
            throw new IllegalStateException("No nectar templates packaged");
        }
        Map<String, Template> result = new HashMap<>();
        for (String name : names) {
            if (!name.endsWith(".png")) {
                continue;
            }
            Bitmap bitmap;
            try (InputStream stream = context.getAssets().open(
                    ASSET_DIRECTORY + "/" + name)) {
                bitmap = BitmapFactory.decodeStream(stream);
            }
            if (bitmap == null
                    || bitmap.getWidth() != TEMPLATE_SIZE
                    || bitmap.getHeight() != TEMPLATE_SIZE) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                throw new IllegalStateException("Invalid nectar template dimensions");
            }
            int[] pixels = new int[TEMPLATE_SIZE * TEMPLATE_SIZE];
            bitmap.getPixels(
                    pixels, 0, TEMPLATE_SIZE, 0, 0, TEMPLATE_SIZE, TEMPLATE_SIZE);
            bitmap.recycle();
            result.put(name, createTemplate(name, pixels));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No PNG nectar templates packaged");
        }
        return result;
    }

    private static Template createTemplate(String name, int[] pixels) {
        double red = 0d;
        double green = 0d;
        double blue = 0d;
        for (int offset : MASK_OFFSETS) {
            int pixel = pixels[offset];
            red += red(pixel);
            green += green(pixel);
            blue += blue(pixel);
        }
        red /= MASK_OFFSETS.length;
        green /= MASK_OFFSETS.length;
        blue /= MASK_OFFSETS.length;
        float[] centered = new float[MASK_OFFSETS.length * 3];
        double energy = 0d;
        for (int index = 0; index < MASK_OFFSETS.length; index++) {
            int pixel = pixels[MASK_OFFSETS[index]];
            int channel = index * 3;
            centered[channel] = (float) (red(pixel) - red);
            centered[channel + 1] = (float) (green(pixel) - green);
            centered[channel + 2] = (float) (blue(pixel) - blue);
            energy += centered[channel] * centered[channel]
                    + centered[channel + 1] * centered[channel + 1]
                    + centered[channel + 2] * centered[channel + 2];
        }
        return new Template(name, pixels, centered, Math.sqrt(energy));
    }

    private static float score(int[] pixels, Template template) {
        double red = 0d;
        double green = 0d;
        double blue = 0d;
        for (int offset : MASK_OFFSETS) {
            int pixel = pixels[offset];
            red += red(pixel);
            green += green(pixel);
            blue += blue(pixel);
        }
        red /= MASK_OFFSETS.length;
        green /= MASK_OFFSETS.length;
        blue /= MASK_OFFSETS.length;

        double sourceEnergy = 0d;
        double correlation = 0d;
        double difference = 0d;
        for (int index = 0; index < MASK_OFFSETS.length; index++) {
            int offset = MASK_OFFSETS[index];
            int pixel = pixels[offset];
            float centeredRed = (float) (red(pixel) - red);
            float centeredGreen = (float) (green(pixel) - green);
            float centeredBlue = (float) (blue(pixel) - blue);
            int channel = index * 3;
            correlation += template.centered()[channel] * centeredRed
                    + template.centered()[channel + 1] * centeredGreen
                    + template.centered()[channel + 2] * centeredBlue;
            sourceEnergy += centeredRed * centeredRed
                    + centeredGreen * centeredGreen
                    + centeredBlue * centeredBlue;
            int expected = template.pixels()[offset];
            difference += (Math.abs(red(pixel) - red(expected))
                    + Math.abs(green(pixel) - green(expected))
                    + Math.abs(blue(pixel) - blue(expected))) / 765d;
        }
        float correlationScore = (float) ((correlation
                / (Math.sqrt(sourceEnergy) * template.energy() + 1e-6d) + 1d) / 2d);
        float differenceScore = Math.max(
                0f, 1f - (float) (difference / MASK_OFFSETS.length) / 0.35f);
        return Math.max(0f, Math.min(1f,
                correlationScore * 0.70f + differenceScore * 0.30f));
    }

    private static int[] createMaskOffsets() {
        int count = 0;
        for (int y = 8; y <= 58; y++) {
            for (int x = 8; x <= 72; x++) {
                int dx = x - 40;
                int dy = y - 40;
                if (dx * dx + dy * dy <= 32 * 32) {
                    count++;
                }
            }
        }
        int[] offsets = new int[count];
        int index = 0;
        for (int y = 8; y <= 58; y++) {
            for (int x = 8; x <= 72; x++) {
                int dx = x - 40;
                int dy = y - 40;
                if (dx * dx + dy * dy <= 32 * 32) {
                    offsets[index++] = y * TEMPLATE_SIZE + x;
                }
            }
        }
        return offsets;
    }

    private static int red(int color) {
        return color >>> 16 & 0xff;
    }

    private static int green(int color) {
        return color >>> 8 & 0xff;
    }

    private static int blue(int color) {
        return color & 0xff;
    }
}
