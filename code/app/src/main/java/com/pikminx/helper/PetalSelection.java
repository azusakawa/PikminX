package com.pikminx.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 管理使用者輸入的花朵順序，並在載入時保留未知名稱供提示使用。 */
final class PetalSelection {
    private final List<String> selected = new ArrayList<>();
    private final List<String> ignored = new ArrayList<>();

    /** 建立去除空白與重複項目的選擇順序。 */
    PetalSelection(List<String> initialSelection) {
        Set<String> seen = new HashSet<>();
        for (String value : initialSelection) {
            String trimmed = value.trim();
            if (PetalCatalog.contains(trimmed) && seen.add(trimmed)) {
                selected.add(trimmed);
            } else if (!trimmed.isEmpty()) {
                ignored.add(trimmed);
            }
        }
    }

    /** 回傳目錄中不存在的名稱。 */
    List<String> ignored() {
        return List.copyOf(ignored);
    }

    List<String> available(int categoryIndex) {
        List<String> result = new ArrayList<>();
        for (String petal : PetalCatalog.categories().get(categoryIndex).petals()) {
            if (!selected.contains(petal)) {
                result.add(petal);
            }
        }
        return result;
    }

    /** 將尚未加入的花朵附加到序列尾端。 */
    boolean add(String petal) {
        return PetalCatalog.contains(petal) && !selected.contains(petal) && selected.add(petal);
    }

    /** 移除指定位置的花朵。 */
    void remove(int index) {
        selected.remove(index);
    }

    /** 在序列中上下移動花朵。 */
    void move(int index, int offset) {
        Collections.swap(selected, index, index + offset);
    }

    /** 回傳目前選擇數量。 */
    int size() {
        return selected.size();
    }

    /** 讀取指定位置的花朵。 */
    String get(int index) {
        return selected.get(index);
    }

    /** 將選擇順序序列化為可儲存的換行文字。 */
    String text() {
        return String.join("\n", selected);
    }
}
