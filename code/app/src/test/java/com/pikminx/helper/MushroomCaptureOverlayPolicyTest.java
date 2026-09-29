package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class MushroomCaptureOverlayPolicyTest {
    @Test
    public void hidesEveryOverlayViewWithoutChangingSavedVisibility() {
        MushroomCaptureOverlayPolicy.Snapshot saved =
                new MushroomCaptureOverlayPolicy.Snapshot(0, 8, 4);

        MushroomCaptureOverlayPolicy.Snapshot hidden =
                MushroomCaptureOverlayPolicy.hidden(saved);

        assertEquals(MushroomCaptureOverlayPolicy.INVISIBLE, hidden.icon());
        assertEquals(MushroomCaptureOverlayPolicy.INVISIBLE, hidden.settings());
        assertEquals(MushroomCaptureOverlayPolicy.INVISIBLE, hidden.notice());
        assertEquals(saved, MushroomCaptureOverlayPolicy.restore(saved));
    }

    @Test
    public void nullCaptureStateIsIdempotent() {
        assertNull(MushroomCaptureOverlayPolicy.hidden(null));
        assertNull(MushroomCaptureOverlayPolicy.restore(null));
    }
}
