package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Device-backed regression tests for the v1.2.9 overlap suppression policy.
 *
 * <p>The production method is private on purpose. Reflection keeps this validation-only suite
 * from changing the production API while still exercising the final suppression boundary.</p>
 */
@RunWith(AndroidJUnit4.class)
public final class MushroomDuplicateSuppressionInstrumentedTest {
    @Test
    public void higherConfidenceCandidateReplacesSamePhysicalCandidate() throws Exception {
        List<MushroomHit> hits = new ArrayList<>();
        MushroomHit first = hit("一般火蘑菇", 0.60f, new Rect(100, 100, 200, 200));
        MushroomHit lower = hit("大火蘑菇", 0.59f, new Rect(100, 100, 200, 200));
        MushroomHit higher = hit("大火蘑菇", 0.61f, new Rect(100, 100, 200, 200));

        add(hits, first);
        add(hits, lower);
        assertEquals(1, hits.size());
        assertSame(first, hits.get(0));

        add(hits, higher);
        assertEquals(1, hits.size());
        assertSame(higher, hits.get(0));
    }

    @Test
    public void differentTemplateNamesForOneMushroomUseTheSameFamilyRule() throws Exception {
        List<MushroomHit> hits = new ArrayList<>();
        MushroomHit normal = hit("一般灰色蘑菇", 0.56f, new Rect(300, 400, 400, 500));
        MushroomHit small = hit("小灰色蘑菇", 0.58f, new Rect(301, 401, 401, 501));

        add(hits, normal);
        add(hits, small);

        assertEquals(1, hits.size());
        assertSame(small, hits.get(0));
    }

    @Test
    public void separateNearbyMushroomsAreNotCollapsed() throws Exception {
        List<MushroomHit> hits = new ArrayList<>();
        MushroomHit left = hit("一般灰色蘑菇", 0.70f, new Rect(100, 100, 180, 180));
        MushroomHit right = hit("一般灰色蘑菇", 0.69f, new Rect(180, 100, 260, 180));

        add(hits, left);
        add(hits, right);

        assertEquals(2, hits.size());
        assertSame(left, hits.get(0));
        assertSame(right, hits.get(1));
    }

    @Test
    public void strictOverlapAndDistanceBoundariesMatchReferencePolicy() throws Exception {
        // Equal-size boxes shifted by 20 pixels overlap exactly 0.80; the reference uses >.
        List<MushroomHit> sameFamilyAtBoundary = new ArrayList<>();
        add(sameFamilyAtBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));
        add(sameFamilyAtBoundary, hit("一般火蘑菇", 0.61f, new Rect(20, 0, 120, 100)));
        assertEquals(2, sameFamilyAtBoundary.size());

        List<MushroomHit> sameFamilyInsideBoundary = new ArrayList<>();
        add(sameFamilyInsideBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));
        add(sameFamilyInsideBoundary, hit("一般火蘑菇", 0.61f, new Rect(19, 0, 119, 100)));
        assertEquals(1, sameFamilyInsideBoundary.size());

        // Different families use the stricter >0.82 branch.
        List<MushroomHit> differentFamilyAtBoundary = new ArrayList<>();
        add(differentFamilyAtBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));
        add(differentFamilyAtBoundary, hit("一般灰色蘑菇", 0.61f, new Rect(18, 0, 118, 100)));
        assertEquals(2, differentFamilyAtBoundary.size());

        List<MushroomHit> differentFamilyInsideBoundary = new ArrayList<>();
        add(differentFamilyInsideBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));
        add(differentFamilyInsideBoundary, hit("一般灰色蘑菇", 0.61f, new Rect(17, 0, 117, 100)));
        assertEquals(1, differentFamilyInsideBoundary.size());

        // The center-distance guard is also strict: distance 26 is not suppressed.
        List<MushroomHit> distanceAtBoundary = new ArrayList<>();
        add(distanceAtBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 500, 100)));
        add(distanceAtBoundary, hit("一般火蘑菇", 0.61f, new Rect(26, 0, 526, 100)));
        assertEquals(2, distanceAtBoundary.size());

        List<MushroomHit> distanceInsideBoundary = new ArrayList<>();
        add(distanceInsideBoundary, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 500, 100)));
        add(distanceInsideBoundary, hit("一般火蘑菇", 0.61f, new Rect(25, 0, 525, 100)));
        assertEquals(1, distanceInsideBoundary.size());
    }

    @Test
    public void equalConfidenceKeepsEarlierCandidateAndResultIsDeterministic() throws Exception {
        List<MushroomHit> firstRun = new ArrayList<>();
        MushroomHit first = hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100));
        MushroomHit equal = hit("大火蘑菇", 0.60f, new Rect(0, 0, 100, 100));
        add(firstRun, first);
        add(firstRun, equal);

        List<MushroomHit> secondRun = new ArrayList<>();
        add(secondRun, hit("一般火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));
        add(secondRun, hit("大火蘑菇", 0.60f, new Rect(0, 0, 100, 100)));

        assertEquals(1, firstRun.size());
        assertSame(first, firstRun.get(0));
        assertEquals(signature(firstRun), signature(secondRun));
    }

    @Test
    public void suppressionKeepsTheReferenceMaximumOfTwelveHits() throws Exception {
        List<MushroomHit> hits = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            int left = index * 120;
            add(hits, hit("一般灰色蘑菇", 0.60f, new Rect(left, 0, left + 80, 80)));
        }
        assertEquals(12, hits.size());
    }

    private static MushroomHit hit(String type, float confidence, Rect box) {
        return new MushroomHit(box, type, confidence, "火蘑菇", "normal", 0.2f, 0.6f, 0.4f);
    }

    private static void add(List<MushroomHit> hits, MushroomHit candidate) throws Exception {
        Method method = MushroomDetector.class.getDeclaredMethod(
                "addWithOverlapSuppression", List.class, MushroomHit.class);
        method.setAccessible(true);
        method.invoke(null, hits, candidate);
    }

    private static String signature(List<MushroomHit> hits) {
        StringBuilder result = new StringBuilder();
        for (MushroomHit hit : hits) {
            result.append(hit.type()).append('|')
                    .append(hit.confidence()).append('|')
                    .append(hit.box()).append(';');
        }
        return result.toString();
    }
}
