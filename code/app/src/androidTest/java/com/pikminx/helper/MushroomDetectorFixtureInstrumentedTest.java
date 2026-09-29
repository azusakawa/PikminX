package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Device-backed fixture tests for the recovered AutoCool v1.2.9 detector path. */
@RunWith(AndroidJUnit4.class)
public final class MushroomDetectorFixtureInstrumentedTest {
    private static final String TAG = "MushroomFixtureTest";
    private static final String FIXTURE_MANIFEST = "mushroom_fixtures/fixtures.json";

    @Test
    public void realScreenshotFixturesHaveStableDimensionsAndDeterministicResults() throws Exception {
        Context target = InstrumentationRegistry.getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        MushroomDetector detector = new MushroomDetector(target);
        List<Fixture> fixtures = readFixtures(test);
        assertTrue("fixture inventory must not be empty", fixtures.size() >= 5);
        assertTrue("templates must load", detector.templateVariantCount() > 0);
        for (Fixture fixture : fixtures) {
            Bitmap bitmap = decodeFixture(test, fixture.file);
            assertNotNull("fixture decode failed: " + fixture.id, bitmap);
            try {
                assertEquals(fixture.id + " width", fixture.width, bitmap.getWidth());
                assertEquals(fixture.id + " height", fixture.height, bitmap.getHeight());
                MushroomDetectionResult first = detector.detect(bitmap);
                MushroomDetectionResult second = detector.detect(bitmap);
                assertEquivalent(fixture.id, first, second);
                logResult(fixture, first);
                // The raw detector intentionally classifies a fixed-catalog mismatch as an
                // activity mushroom.  Unrelated screens are protected by MushroomPageGate before
                // this raw result can reach the user-facing state.
                assertTrue("detector output must stay bounded for " + fixture.id,
                        first.hits().size() <= 12);
            } finally {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }

    @Test
    public void fixtureInventoryCoversPositiveNegativeMultiAndScaledScenes() throws Exception {
        List<Fixture> fixtures = readFixtures(
                InstrumentationRegistry.getInstrumentation().getContext());
        boolean hasRealPositive = false;
        boolean hasRealMulti = false;
        boolean hasNegative = false;
        boolean hasNonCanonicalDimensions = false;
        for (Fixture fixture : fixtures) {
            assertTrue("fixture role missing: " + fixture.id, !fixture.role.isBlank());
            assertTrue("fixture source missing: " + fixture.id, !fixture.source.isBlank());
            hasRealPositive |= fixture.role.contains("real_positive")
                    || fixture.role.contains("real_multi");
            hasRealMulti |= fixture.role.contains("multi");
            hasNegative |= fixture.role.startsWith("negative_");
            hasNonCanonicalDimensions |= fixture.width != 1280 || fixture.height != 2772;
        }
        assertTrue("real Xiaomi positive/multi fixture required", hasRealPositive);
        assertTrue("multi-candidate fixture required", hasRealMulti);
        assertTrue("negative fixture required", hasNegative);
        assertTrue("at least one differently scaled real fixture required",
                hasNonCanonicalDimensions);
    }

    @Test
    public void multiCandidateFixturesRemainDeterministicAndBounded() throws Exception {
        Context target = InstrumentationRegistry.getTargetContext();
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        MushroomDetector detector = new MushroomDetector(target);
        for (Fixture fixture : readFixtures(test)) {
            if (!fixture.role.contains("multi")) {
                continue;
            }
            Bitmap bitmap = decodeFixture(test, fixture.file);
            assertNotNull(fixture.id, bitmap);
            try {
                MushroomDetectionResult first = detector.detect(bitmap);
                MushroomDetectionResult second = detector.detect(bitmap);
                assertEquivalent(fixture.id, first, second);
                assertTrue("maximum output count for " + fixture.id,
                        first.hits().size() <= 12);
                logResult(fixture, first);
            } finally {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }

    @Test
    public void redPointCandidatesMatchIndependentV129ReferenceForRealFixtures() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        for (Fixture fixture : readFixtures(test)) {
            Bitmap bitmap = decodeFixture(test, fixture.file);
            assertNotNull(fixture.id, bitmap);
            try {
                List<Point> expected = ReferenceRedPointPolicy.find(bitmap);
                List<Point> actual = invokePikminXRedPoints(bitmap);
                assertEquals("raw red-point mismatch for " + fixture.id, expected, actual);
            } finally {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }
    }

    @Test
    public void recordedPositiveSampleMetadataIsExplicitlyNotUsedAsAnOracleForOverlayPixels()
            throws Exception {
        Fixture fixture = findFixture(
                readFixtures(InstrumentationRegistry.getInstrumentation().getContext()),
                "xiaomi_raw_map_before_interrupt");
        assertNotNull(fixture);
        assertEquals("UNRESOLVED", fixture.oracleStatus);
        assertEquals(-1, fixture.visibleCount);
    }

    private static Fixture findFixture(List<Fixture> fixtures, String id) {
        for (Fixture fixture : fixtures) {
            if (fixture.id.equals(id)) {
                return fixture;
            }
        }
        return null;
    }

    private static void assertEquivalent(
            String id, MushroomDetectionResult first, MushroomDetectionResult second) {
        assertEquals(id + " hit count", first.hits().size(), second.hits().size());
        assertEquals(id + " red points", first.stats().redPointCount(), second.stats().redPointCount());
        assertEquals(
                id + " comparisons",
                first.stats().variantComparisons(),
                second.stats().variantComparisons());
        assertEquals(
                id + " accepted candidates",
                first.stats().acceptedCandidates(),
                second.stats().acceptedCandidates());
        for (int index = 0; index < first.hits().size(); index++) {
            MushroomHit left = first.hits().get(index);
            MushroomHit right = second.hits().get(index);
            assertEquals(id + " type", left.type(), right.type());
            assertEquals(id + " size family", left.sizeFamily(), right.sizeFamily());
            assertEquals(id + " size decision", left.sizeDecision(), right.sizeDecision());
            assertEquals(id + " box", left.box(), right.box());
            assertEquals(id + " confidence", left.confidence(), right.confidence(), 0.000001f);
        }
    }

    private static void logResult(Fixture fixture, MushroomDetectionResult result) {
        MushroomDetectionResult.Stats stats = result.stats();
        StringBuilder hits = new StringBuilder();
        for (MushroomHit hit : result.hits()) {
            if (hits.length() > 0) {
                hits.append(';');
            }
            hits.append(hit.type())
                    .append('|').append(hit.sizeDecision())
                    .append('|').append(hit.confidence())
                    .append('|').append(hit.centerX()).append(',').append(hit.centerY())
                    .append('|').append(hit.box());
        }
        Log.i(TAG, "FIXTURE id=" + fixture.id
                + " source=" + fixture.source
                + " dimensions=" + fixture.width + "x" + fixture.height
                + " redPoints=" + stats.redPointCount()
                + " comparisons=" + stats.variantComparisons()
                + " accepted=" + stats.acceptedCandidates()
                + " hits=" + result.hits().size()
                + " detectorMs=" + stats.detectorMillis()
                + " hitsDetail=" + hits);
    }

    private static List<Fixture> readFixtures(Context context) throws Exception {
        String json;
        try (InputStream input = context.getAssets().open(FIXTURE_MANIFEST)) {
            byte[] bytes = new byte[input.available()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            json = new String(bytes, 0, offset, java.nio.charset.StandardCharsets.UTF_8);
        }
        JSONArray array = new JSONArray(json);
        List<Fixture> fixtures = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            fixtures.add(new Fixture(
                    item.getString("id"),
                    item.getString("file"),
                    item.getString("source"),
                    item.getInt("width"),
                    item.getInt("height"),
                    item.getInt("visibleCount"),
                    item.getString("role"),
                    item.optString("oracleStatus", "UNRESOLVED")));
        }
        return fixtures;
    }

    private static Bitmap decodeFixture(Context context, String file) throws Exception {
        try (InputStream input = context.getAssets().open("mushroom_fixtures/" + file)) {
            return BitmapFactory.decodeStream(input);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Point> invokePikminXRedPoints(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        Method method = MushroomDetector.class.getDeclaredMethod(
                "redPoints", int[].class, int.class, int.class);
        method.setAccessible(true);
        List<?> points = (List<?>) method.invoke(null, pixels, width, height);
        List<Point> result = new ArrayList<>();
        for (Object point : points) {
            Field x = point.getClass().getDeclaredField("x");
            Field y = point.getClass().getDeclaredField("y");
            x.setAccessible(true);
            y.setAccessible(true);
            result.add(new Point(((Number) x.get(point)).floatValue(),
                    ((Number) y.get(point)).floatValue()));
        }
        return result;
    }

    private record Fixture(
            String id,
            String file,
            String source,
            int width,
            int height,
            int visibleCount,
            String role,
            String oracleStatus) {}

    private record Point(float x, float y) {}

    /** Independent translation of the recoverable v1.2.9 smali red-point policy. */
    private static final class ReferenceRedPointPolicy {
        static List<Point> find(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            List<Point> result = new ArrayList<>();
            addUnique(result, find(pixels, width, height, false));
            addUnique(result, find(pixels, width, height, true));
            result.sort(Comparator.comparingDouble(point -> point.y * 10000.0 + point.x));
            return result;
        }

        private static void addUnique(List<Point> destination, List<Point> source) {
            for (Point point : source) {
                boolean duplicate = false;
                for (Point existing : destination) {
                    if (Math.hypot(existing.x - point.x, existing.y - point.y) < 8.0) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    destination.add(point);
                }
            }
        }

        private static List<Point> find(int[] pixels, int width, int height, boolean strictRed) {
            byte[] redMask = new byte[pixels.length];
            float[] hsv = new float[3];
            for (int index = 0; index < pixels.length; index++) {
                android.graphics.Color.colorToHSV(pixels[index], hsv);
                if ((hsv[0] <= 20f || hsv[0] >= 340f)
                        && hsv[1] >= 75f / 255f
                        && hsv[2] >= 105f / 255f) {
                    redMask[index] = 1;
                }
            }
            if (strictRed) {
                for (int index = 0; index < pixels.length; index++) {
                    int pixel = pixels[index];
                    int red = android.graphics.Color.red(pixel);
                    int green = android.graphics.Color.green(pixel);
                    int blue = android.graphics.Color.blue(pixel);
                    if (red < 225
                            || green < 55
                            || green > 140
                            || blue < green * 0.92f
                            || blue > green + 45) {
                        redMask[index] = 0;
                    }
                }
            }
            byte[] blocks = new byte[pixels.length];
            for (int y = 0; y < height - 1; y++) {
                for (int x = 0; x < width - 1; x++) {
                    int index = y * width + x;
                    if (redMask[index] != 0
                            && redMask[index + 1] != 0
                            && redMask[index + width] != 0
                            && redMask[index + width + 1] != 0) {
                        blocks[index] = 1;
                        blocks[index + 1] = 1;
                        blocks[index + width] = 1;
                        blocks[index + width + 1] = 1;
                    }
                }
            }
            boolean[] visited = new boolean[pixels.length];
            int[] neighborX = {1, -1, 0, 0, 1, 1, -1, -1};
            int[] neighborY = {0, 0, 1, -1, 1, -1, 1, -1};
            List<Point> result = new ArrayList<>();
            for (int startY = 0; startY < height; startY++) {
                for (int startX = 0; startX < width; startX++) {
                    int start = startY * width + startX;
                    if (blocks[start] == 0 || visited[start]) {
                        continue;
                    }
                    ArrayList<Integer> queue = new ArrayList<>();
                    queue.add(start);
                    visited[start] = true;
                    int count = 0;
                    int minX = startX;
                    int maxX = startX;
                    int minY = startY;
                    int maxY = startY;
                    double sumX = 0.0;
                    double sumY = 0.0;
                    for (int head = 0; head < queue.size(); head++) {
                        int index = queue.get(head);
                        int x = index % width;
                        int y = index / width;
                        count++;
                        sumX += x;
                        sumY += y;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        for (int direction = 0; direction < neighborX.length; direction++) {
                            int nextX = x + neighborX[direction];
                            int nextY = y + neighborY[direction];
                            if (nextX < 0 || nextX >= width || nextY < 0 || nextY >= height) {
                                continue;
                            }
                            int next = nextY * width + nextX;
                            if (blocks[next] != 0 && !visited[next]) {
                                visited[next] = true;
                                queue.add(next);
                            }
                        }
                    }
                    int componentWidth = maxX - minX + 1;
                    int componentHeight = maxY - minY + 1;
                    if (count < 8 || count > 350
                            || componentWidth < 2 || componentWidth > 24
                            || componentHeight < 2 || componentHeight > 28) {
                        continue;
                    }
                    float ratio = componentWidth / (float) Math.max(1, componentHeight);
                    float fill = count / (float) Math.max(1, componentWidth * componentHeight);
                    int sampleLeft = Math.max(0, minX - 4);
                    int sampleRight = Math.min(width - 1, maxX + 4);
                    int sampleTop = Math.max(0, minY - 4);
                    int sampleBottom = Math.min(height - 1, maxY + 4);
                    int sampleCount = 0;
                    int paleCount = 0;
                    int brightCount = 0;
                    for (int y = sampleTop; y <= sampleBottom; y++) {
                        for (int x = sampleLeft; x <= sampleRight; x++) {
                            android.graphics.Color.colorToHSV(pixels[y * width + x], hsv);
                            sampleCount++;
                            if (hsv[1] < 0.24f && hsv[2] > 0.76f) {
                                paleCount++;
                            }
                            if (hsv[1] < 0.36f && hsv[2] > 0.70f) {
                                brightCount++;
                            }
                        }
                    }
                    boolean edgeLarge = minX <= 1
                            && count >= 24
                            && componentWidth >= 7
                            && componentHeight >= 4
                            && ratio >= 0.70f
                            && ratio <= 2.20f
                            && fill >= 0.48f;
                    boolean large = (count >= 28
                            && componentWidth >= 6
                            && componentHeight >= 6
                            && ratio >= 0.68f
                            && ratio <= 1.45f
                            && fill >= 0.52f) || edgeLarge;
                    int blueOrDark = 0;
                    int blueSampleCount = 0;
                    for (int y = Math.max(0, minY - 4); y <= Math.min(height - 1, maxY + 5); y++) {
                        for (int x = Math.min(width - 1, maxX + 1);
                                x <= Math.min(width - 1, maxX + 28); x++) {
                            android.graphics.Color.colorToHSV(pixels[y * width + x], hsv);
                            blueSampleCount++;
                            boolean blueLike = hsv[0] >= 130f && hsv[0] <= 210f
                                    && hsv[1] > 0.18f
                                    && hsv[2] >= 0.18f && hsv[2] <= 0.72f;
                            boolean dark = hsv[1] < 0.35f
                                    && hsv[2] >= 0.18f && hsv[2] <= 0.55f;
                            if (blueLike || dark) {
                                blueOrDark++;
                            }
                        }
                    }
                    boolean blueEvidence = blueSampleCount > 0
                            && blueOrDark / (float) blueSampleCount >= 0.12f;
                    boolean dense = count >= 70
                            && fill >= 0.74f
                            && ratio >= 0.78f
                            && ratio <= 1.28f;
                    boolean compact = strictRed
                            && componentWidth <= 16
                            && componentHeight <= 16
                            && (dense || (count >= 56
                                    && fill >= 0.78f
                                    && ratio >= 0.86f
                                    && ratio <= 1.16f));
                    boolean compactPale = compact
                            && componentWidth <= 12
                            && componentHeight <= 12
                            && sampleCount > 0
                            && brightCount / (float) sampleCount >= 0.04f;
                    if (!large || sampleCount == 0) {
                        continue;
                    }
                    if (!compactPale
                            && paleCount / (float) sampleCount < (compact ? 0.02f : 0.035f)) {
                        continue;
                    }
                    float centerX = (float) (sumX / count);
                    float centerY = (float) (sumY / count);
                    if (centerX < 22f || centerX > width - 22f
                            || (!edgeLarge && centerY < 35f)
                            || centerY > height - 35f
                            || (centerX > width * 0.83f && centerY < height * 0.18f)) {
                        continue;
                    }
                    if (blueEvidence || dense || compact || edgeLarge) {
                        result.add(new Point(centerX, centerY));
                    }
                }
            }
            result.sort(Comparator.comparingDouble(point -> point.y * 10000.0 + point.x));
            return result;
        }
    }
}
