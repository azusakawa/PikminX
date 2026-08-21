package com.pikminx.helper;

import java.util.ArrayList;
import java.util.List;

/** 將自動換花介面接到明信片花盆目錄，避免維護兩份名稱與 OCR 校正規則。 */
final class PetalCatalog {
    record Category(String name, List<String> petals) {}

    private static final List<Category> CATEGORIES = categoriesFromPostcardCatalog();

    private PetalCatalog() {}

    static List<Category> categories() {
        return CATEGORIES;
    }

    static boolean contains(String petal) {
        return PostcardPotCatalog.contains(petal);
    }

    static String canonicalName(String value) {
        return PostcardPotCatalog.canonicalName(value);
    }

    static int categoryIndexOf(String value) {
        String canonical = canonicalName(value);
        if (canonical == null) {
            return -1;
        }
        for (int index = 0; index < CATEGORIES.size(); index++) {
            if (CATEGORIES.get(index).petals().contains(canonical)) {
                return index;
            }
        }
        return -1;
    }

    static List<String> petals() {
        return PostcardPotCatalog.allNames();
    }

    static int size() {
        return PostcardPotCatalog.allNames().size();
    }

    /** 自動換花仍以花種搜尋；基礎花瓣只輸入顏色。 */
    static String searchQuery(String value) {
        String canonical = canonicalName(value);
        PostcardPotCatalog.Color color = PostcardPotCatalog.colorOf(canonical);
        if (canonical == null || color == null) {
            return "";
        }
        String flower = canonical.substring(color.label().length());
        return "花瓣".equals(flower) ? color.label() : flower;
    }

    private static List<Category> categoriesFromPostcardCatalog() {
        List<Category> categories = new ArrayList<>();
        for (PostcardPotCatalog.Category category : PostcardPotCatalog.categories()) {
            String color = category.color().label();
            List<String> names = new ArrayList<>(category.names());
            String basicPetal = color + "花瓣";
            if (names.remove(basicPetal)) {
                names.add(0, basicPetal);
            }
            categories.add(new Category(color, List.copyOf(names)));
        }
        return List.copyOf(categories);
    }
}
