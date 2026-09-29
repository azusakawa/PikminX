package com.pikminx.helper;

import static org.junit.Assert.*;
import java.util.function.IntBinaryOperator;
import org.junit.Test;

/** Geometry-only regression. These generated pixels do not establish gameplay acceptance. */
public final class ExpeditionVisionTest {
    @Test
    public void mapTextAbovePanelDoesNotMakeTheSolidHandleAmbiguous() {
        var surface = ExpeditionVision.detect(432, 936, (x, y) -> {
            if (x >= 240 && x < 320 && y >= 874 && y < 904) return 0xff00ac90;
            if (x >= 172 && x < 260 && y >= 856 && y < 862) return 0xffe5e5e5;
            if (x >= 172 && x < 260 && y >= 831 && y < 839
                    && (x % 8 < 2 || y >= 837)) return 0xffe5e5e5;
            return 0xffffffff;
        });
        assertNotNull(surface);
        assertNotNull(surface.handle());
        assertTrue(surface.handle().centerY() >= 856);
    }

    @Test
    public void activeCardOutlineExcludesItsContentsButNotAdjacentAvailableCards() {
        for (int scale : new int[] {1, 2, 3}) {
            IntBinaryOperator base = scene(scale, false, false);
            var surface = ExpeditionVision.detect(432 * scale, 936 * scale, (rawX, rawY) -> {
                int x = rawX / scale, y = rawY / scale;
                if (x >= 30 && x < 170 && y >= 220 && y < 410
                        && (x < 33 || x >= 167 || y < 223 || y >= 407)) return 0xffffe0e3;
                // A separate available icon remains outside the outlined active card.
                if (x >= 260 && x < 315 && y >= 250 && y < 320) return 0xffbf244c;
                return base.applyAsInt(rawX, rawY);
            });
            assertNotNull(surface);
            assertEquals(1, surface.icons().size());
            assertTrue(surface.icons().get(0).bounds().left() >= 250 * scale);
        }
    }

    @Test
    public void proposesIconsWithoutOcrAndUsesTheirCurrentBoundsAcrossScales() {
        for (int scale : new int[] {1, 2, 3}) {
            ExpeditionVision.Surface surface = ExpeditionVision.detect(432 * scale, 936 * scale,
                    scene(scale, false, false));
            assertNotNull(surface);
            assertEquals(1, surface.icons().size());
            ExpeditionVision.Icon icon = surface.icons().get(0);
            assertTrue(icon.cardContext());
            assertTrue(Math.abs(icon.bounds().left() - 60 * scale) <= scale);
            assertTrue(Math.abs(icon.bounds().top() - 250 * scale) <= scale);
            assertTrue(icon.cardText().bottom() > icon.bounds().bottom());
        }
    }

    @Test
    public void oneRowGlyphInterruptionDoesNotEraseAnOtherwiseValidCardTitle() {
        IntBinaryOperator base = scene(1, false, true);
        var surface = ExpeditionVision.detect(432, 936, (x, y) -> {
            if (x >= 45 && x < 125 && x % 8 < 2
                    && (y >= 340 && y < 347 && y != 343 || y >= 365 && y < 375))
                return 0xff444444;
            return base.applyAsInt(x, y);
        });
        assertNotNull(surface);
        assertTrue(surface.icons().get(0).cardContext());
    }

    @Test
    public void viewportSignatureIncludesTextNotOnlyRepeatedIconTypes() {
        IntBinaryOperator pixels = scene(1, false, false);
        ExpeditionVision.Surface surface = ExpeditionVision.detect(432, 936, pixels);
        long first = ExpeditionVision.viewportSignature(surface, pixels);
        assertEquals(first, ExpeditionVision.viewportSignature(surface, pixels));
        long changed = ExpeditionVision.viewportSignature(surface,
                (x, y) -> x >= 140 && x < 165 && y >= 350 && y < 370 ? 0xff111111 : pixels.applyAsInt(x, y));
        assertNotEquals(first, changed);
        assertEquals(first, ExpeditionVision.viewportSignature(surface,
                (x, y) -> x >= 200 && x < 240 && y >= 500 && y < 540
                        ? ((x + y) % 2 == 0 ? 0xffff4040 : 0xff40c0ff)
                        : pixels.applyAsInt(x, y)));
    }

    @Test
    public void minimizedPanelRetainsItsDetectedHandleWithoutInventingCardGeometry() {
        var surface = ExpeditionVision.detect(432, 936, (x, y) -> {
            if (x >= 240 && x < 320 && y >= 874 && y < 904) return 0xff00ac90;
            if (x >= 172 && x < 260 && y >= 856 && y < 862) return 0xffe5e5e5;
            return 0xffffffff;
        });
        assertNotNull(surface);
        assertNotNull(surface.handle());
        assertTrue(surface.icons().isEmpty());
        assertEquals(1, surface.content().height());
        assertTrue(surface.handle().centerY() < surface.selectedTab().top());
    }

    @Test
    public void absentOrAmbiguousSelectedTabCannotBecomeAListSurface() {
        assertNull(ExpeditionVision.detect(432, 936, (x, y) -> 0xffffffff));
        assertNull(ExpeditionVision.detect(432, 936, scene(1, true, false)));
        assertNull(ExpeditionVision.detect(936, 432, scene(1, false, false)));
    }

    @Test
    public void aDimmedListBehindAModalCannotProposeGameplayTargets() {
        IntBinaryOperator pixels = scene(1, false, false);
        assertNull(ExpeditionVision.detect(432, 936, (x, y) -> {
            int c = pixels.applyAsInt(x, y);
            return 0xff000000 | (((c >>> 16) & 255) / 2) << 16
                    | (((c >>> 8) & 255) / 2) << 8 | (c & 255) / 2;
        }));
    }

    @Test
    public void anIconWithoutItsCardTextIsOnlyAnUnverifiedProposal() {
        ExpeditionVision.Surface surface = ExpeditionVision.detect(432, 936, scene(1, false, true));
        assertNotNull(surface);
        assertEquals(1, surface.icons().size());
        assertFalse(surface.icons().get(0).cardContext());
    }

    @Test
    public void scrollbarDistinguishesTopMiddleBottomAndRejectsBrokenTrack() {
        IntBinaryOperator base = scene(1, false, false);
        for (int thumbTop : new int[] {200, 400, 700}) {
            var surface = ExpeditionVision.detect(432, 936, (x, y) -> {
                if (x >= 424 && x < 428 && y >= 200 && y < 800)
                    return y >= thumbTop && y < thumbTop + 100 ? 0xffbdbdf0 : 0xffe6e6ff;
                return base.applyAsInt(x, y);
            });
            assertNotNull(surface.scrollbar());
            assertEquals(thumbTop == 200, surface.scrollbar().atTop());
            assertEquals(thumbTop == 700, surface.scrollbar().atBottom());
        }
        var broken = ExpeditionVision.detect(432, 936, (x, y) -> {
            if (x >= 424 && x < 428 && y >= 200 && y < 800 && y % 100 < 70)
                return y < 300 ? 0xffbdbdf0 : 0xffe6e6ff;
            return base.applyAsInt(x, y);
        });
        assertNull(broken.scrollbar());
        assertNull(ExpeditionVision.detect(432, 936, base).scrollbar());
    }

    private static IntBinaryOperator scene(int scale, boolean secondTab, boolean omitText) {
        return (rawX, rawY) -> {
            int x = rawX / scale, y = rawY / scale;
            if (x >= 240 && x < 320 && y >= 122 && y < 152) return 0xff00ac90;
            if (secondTab && x >= 120 && x < 200 && y >= 122 && y < 152) return 0xff00ac90;
            if (x >= 60 && x < 115 && y >= 250 && y < 320) return 0xffbf244c;
            if (!omitText && x >= 45 && x < 125 && x % 8 < 2
                    && (y >= 340 && y < 350 || y >= 365 && y < 375)) return 0xff444444;
            return 0xffffffff;
        };
    }
}
