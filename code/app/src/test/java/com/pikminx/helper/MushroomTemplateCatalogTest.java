package com.pikminx.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.List;

import org.junit.Test;

public final class MushroomTemplateCatalogTest {
    @Test
    public void acceptsIntentionalBaseAndFieldPair() {
        List<MushroomTemplateCatalog.Entry> entries = List.of(
                new MushroomTemplateCatalog.Entry(
                        "一般火蘑菇", "template_04.png", 96, 50),
                new MushroomTemplateCatalog.Entry(
                        "一般火蘑菇", "template_normal_fire_field.png", 96, 50));

        MushroomTemplateCatalog.Validation validation =
                MushroomTemplateCatalog.validateManifest(
                        entries,
                        Set.of("template_04.png", "template_normal_fire_field.png"));

        assertTrue(validation.valid());
    }

    @Test
    public void rejectsDuplicateFile() {
        List<MushroomTemplateCatalog.Entry> entries = List.of(
                new MushroomTemplateCatalog.Entry("一般火蘑菇", "same.png", 10, 10),
                new MushroomTemplateCatalog.Entry("大火蘑菇", "same.png", 10, 10));

        assertFalse(MushroomTemplateCatalog.validateManifest(
                entries, Set.of("same.png")).valid());
    }

    @Test
    public void rejectsDuplicateNameAndRole() {
        List<MushroomTemplateCatalog.Entry> entries = List.of(
                new MushroomTemplateCatalog.Entry("一般火蘑菇", "one.png", 10, 10),
                new MushroomTemplateCatalog.Entry("一般火蘑菇", "two.png", 10, 10));

        assertFalse(MushroomTemplateCatalog.validateManifest(
                entries, Set.of("one.png", "two.png")).valid());
    }

    @Test
    public void rejectsMissingAsset() {
        List<MushroomTemplateCatalog.Entry> entries = List.of(
                new MushroomTemplateCatalog.Entry("一般火蘑菇", "missing.png", 10, 10));

        assertFalse(MushroomTemplateCatalog.validateManifest(entries, Set.of()).valid());
    }

    @Test
    public void rejectsInvalidPathAndDimensions() {
        List<MushroomTemplateCatalog.Entry> entries = List.of(
                new MushroomTemplateCatalog.Entry("一般火蘑菇", "../escape.png", 10, 10),
                new MushroomTemplateCatalog.Entry("小火蘑菇", "small.png", 0, -1));

        assertFalse(MushroomTemplateCatalog.validateManifest(
                entries, Set.of("../escape.png", "small.png")).valid());
    }

    @Test
    public void classifiesTemplateSizes() {
        assertTrue("small".equals(new MushroomTemplateCatalog.Entry(
                "小白蘑菇", "small.png", 10, 10).sizeCategory()));
        assertTrue("big".equals(new MushroomTemplateCatalog.Entry(
                "大白蘑菇", "large.png", 10, 10).sizeCategory()));
        assertTrue("event".equals(new MushroomTemplateCatalog.Entry(
                "一般活動蘑菇", "event.png", 10, 10).sizeCategory()));
    }
}
