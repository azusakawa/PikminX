package com.pikminx.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Device-backed coverage checks for the recovered v1.2.9 template catalog. */
@RunWith(AndroidJUnit4.class)
public final class MushroomTemplateCatalogInstrumentedTest {
    private static final Set<String> ELEMENTAL_FAMILIES = Set.of(
            "毒", "水晶", "水", "火", "電", "冰藍");
    private static final Set<String> COLOR_FAMILIES = Set.of(
            "灰色", "白色", "紅色", "粉紅色", "紫色", "藍色", "黃色");

    @Test
    public void everyCatalogEntryLoadsWithDeclaredMetadata() throws Exception {
        Context target = InstrumentationRegistry.getTargetContext();
        List<MushroomTemplateCatalog.Entry> entries =
                MushroomTemplateCatalog.readManifest(target);
        assertEquals("fixed mushroom template catalog entry count", 34, entries.size());

        String[] listed = target.getAssets().list(MushroomTemplateCatalog.ASSET_DIRECTORY);
        assertNotNull(listed);
        Set<String> available = new HashSet<>(Arrays.asList(listed));
        MushroomTemplateCatalog.Validation validation =
                MushroomTemplateCatalog.validateManifest(entries, available);
        assertTrue(String.join("; ", validation.errors()), validation.valid());

        int fieldCount = 0;
        int smallCount = 0;
        int normalCount = 0;
        int bigCount = 0;
        int elementalCount = 0;
        int colorCount = 0;
        for (MushroomTemplateCatalog.Entry entry : entries) {
            try (InputStream input = target.getAssets().open(
                    MushroomTemplateCatalog.ASSET_DIRECTORY + "/" + entry.file())) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                assertNotNull("decode " + entry.file(), bitmap);
                try {
                    assertEquals(entry.file() + " width", entry.width(), bitmap.getWidth());
                    assertEquals(entry.file() + " height", entry.height(), bitmap.getHeight());
                } finally {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }
            if (entry.fieldView()) {
                fieldCount++;
            }
            switch (entry.sizeCategory()) {
                case "small" -> smallCount++;
                case "normal" -> normalCount++;
                case "big" -> bigCount++;
                case "event" -> throw new AssertionError(
                        "event mushrooms are classified only after a fixed-template mismatch");
                default -> throw new AssertionError("unknown size category: "
                        + entry.sizeCategory());
            }
            for (String family : ELEMENTAL_FAMILIES) {
                if (entry.name().contains(family)) {
                    elementalCount++;
                    break;
                }
            }
            for (String family : COLOR_FAMILIES) {
                if (entry.name().contains(family)) {
                    colorCount++;
                    break;
                }
            }
        }

        assertEquals("fixed catalog field-view variants", 0, fieldCount);
        assertTrue("small family coverage", smallCount > 0);
        assertTrue("normal family coverage", normalCount > 0);
        assertTrue("large family coverage", bigCount > 0);
        assertTrue("elemental family coverage", elementalCount > 0);
        assertTrue("color family coverage", colorCount > 0);
    }

    @Test
    public void resourcesAreLoadedAsImmutableDetectorInputsAndExpandToVariants() throws Exception {
        Context target = InstrumentationRegistry.getTargetContext();
        List<MushroomTemplateCatalog.Entry> entries =
                MushroomTemplateCatalog.readManifest(target);
        List<MushroomTemplateCatalog.Resource> resources =
                MushroomTemplateCatalog.loadResources(target);
        try {
            assertEquals(entries.size(), resources.size());
            for (int index = 0; index < entries.size(); index++) {
                MushroomTemplateCatalog.Entry entry = entries.get(index);
                MushroomTemplateCatalog.Resource resource = resources.get(index);
                assertEquals(entry, resource.entry());
                assertNotNull(resource.bitmap());
                assertFalse("template bitmap must be immutable: " + entry.file(),
                        resource.bitmap().isMutable());
                assertEquals(entry.width(), resource.bitmap().getWidth());
                assertEquals(entry.height(), resource.bitmap().getHeight());
            }

            MushroomDetector detector = new MushroomDetector(target);
            assertTrue("catalog load error: " + detector.loadError(),
                    detector.templateVariantCount() > entries.size());
            assertTrue("detector catalog must load without error", detector.loadError().isEmpty());
        } finally {
            for (MushroomTemplateCatalog.Resource resource : resources) {
                if (resource.bitmap() != null && !resource.bitmap().isRecycled()) {
                    resource.bitmap().recycle();
                }
            }
        }
    }
}
