package com.pikminx.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** One lazily loaded pair of banks; scores only, no uncalibrated visual admission. */
final class ExpeditionTemplateMatcher {
    private final Context context;
    private List<ExpeditionRecognition.Template> fruits, seedlings;
    private final ExpeditionRecognition.ScoreCache observations = new ExpeditionRecognition.ScoreCache();
    synchronized void clearObservationCache() { observations.clear(); }
    synchronized String observationStats() { return observations.summary(); }
    ExpeditionTemplateMatcher(Context context) { this.context = context.getApplicationContext(); }

    synchronized ExpeditionRecognition.Scores[] match(ExpeditionVision.Bounds bounds, IntBinaryOperator pixelAt) {
        if (fruits == null) fruits = load("fruit_templates", 17);
        if (seedlings == null) seedlings = load("seedling_templates", 8);
        int[] currentPixels = new int[bounds.width() * bounds.height()];
        for (int y = 0; y < bounds.height(); y++) for (int x = 0; x < bounds.width(); x++)
            currentPixels[y * bounds.width() + x] = pixelAt.applyAsInt(bounds.left() + x, bounds.top() + y);
        ExpeditionRecognition.Scores[] cached = observations.find(bounds, currentPixels);
        if (cached != null) return cached;
        ExpeditionRecognition.Scores[] scores = {
            ExpeditionRecognition.rank("FRUIT", fruits, bounds, pixelAt),
            ExpeditionRecognition.rank("SEEDLING", seedlings, bounds, pixelAt)
        };
        observations.put(bounds, currentPixels, scores);
        return scores;
    }

    private List<ExpeditionRecognition.Template> load(String directory, int expectedCount) {
        List<ExpeditionRecognition.Template> result = new ArrayList<>();
        try {
            String[] names = context.getAssets().list(directory);
            if (names == null) throw new IOException("Missing bank " + directory);
            Arrays.sort(names);
            for (String name : names) {
                if (!name.endsWith(".png")) continue;
                try (InputStream stream = context.getAssets().open(directory + "/" + name)) {
                    Bitmap decoded = BitmapFactory.decodeStream(stream);
                    if (decoded == null) throw new IOException("Invalid template " + name);
                    try {
                        int[] pixels = new int[decoded.getWidth() * decoded.getHeight()];
                        decoded.getPixels(pixels, 0, decoded.getWidth(), 0, 0, decoded.getWidth(), decoded.getHeight());
                        result.add(new ExpeditionRecognition.Template(name, decoded.getWidth(), decoded.getHeight(), pixels));
                    } finally {
                        decoded.recycle();
                    }
                }
            }
            if (result.size() != expectedCount) throw new IOException("Incomplete bank " + directory);
            return List.copyOf(result);
        } catch (IOException | RuntimeException error) {
            Log.e("PikminX", "Expedition bank unavailable: " + directory, error);
            return List.of();
        }
    }

}
