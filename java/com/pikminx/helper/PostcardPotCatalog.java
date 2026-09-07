package com.pikminx.helper;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** APK 內建的明信片花盆目錄；選單不依賴 OCR 掃描或使用者輸入。 */
final class PostcardPotCatalog {
    enum Color {
        WHITE("白色"),
        YELLOW("黃色"),
        RED("紅色"),
        BLUE("藍色");

        private final String label;

        Color(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Category(Color color, List<String> names) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category(Color.WHITE, pots(Color.WHITE,
                    "百合", "彼岸花", "美人蕉", "梅花", "牡丹", "風信子",
                    "風鈴草", "扶桑花", "大波斯菊", "大理花", "曇花", "天堂鳥",
                    "鐵線蓮", "兔耳花", "鈴蘭", "龍膽", "康乃馨", "海芋",
                    "花瓣", "蝴蝶蘭", "雞冠花", "金魚草", "九重葛", "菊花",
                    "牽牛花", "小蒼蘭", "繡球花", "雪花蓮", "週年紀念玫瑰",
                    "山茶花", "石竹", "鼠尾草", "水仙花", "睡蓮", "聖誕紅",
                    "聖誕玫瑰", "洋桔梗", "櫻花", "櫻草花", "鸚鵡鬱金香", "銀蓮花",
                    "油菜花", "豌豆花", "萬壽菊", "勿忘草", "鳶尾花", "矮牽牛")),
            new Category(Color.YELLOW, pots(Color.YELLOW,
                    "百合", "美人蕉", "梅花", "牡丹", "風信子", "扶桑花", "大理花", "曇花", "天堂鳥",
                    "鐵線蓮", "康乃馨", "海芋", "花瓣", "蝴蝶蘭", "雞冠花", "金魚草", "九重葛", "菊花",
                    "牽牛花", "小蒼蘭", "雪花蓮", "週年紀念玫瑰", "山茶花", "鼠尾草", "水仙花", "睡蓮",
                    "聖誕紅", "聖誕玫瑰", "洋桔梗", "櫻草花", "鸚鵡鬱金香", "銀蓮花", "油菜花", "豌豆花", "萬壽菊",
                    "勿忘草", "鳶尾花", "矮牽牛")),
            new Category(Color.RED, pots(Color.RED,
                    "彼岸花", "美人蕉", "梅花", "風信子", "風鈴草", "扶桑花", "大波斯菊", "大理花", "曇花",
                    "天堂鳥", "鐵線蓮", "兔耳花", "鈴蘭", "龍膽", "康乃馨", "海芋", "花瓣", "蝴蝶蘭", "雞冠花",
                    "金魚草", "九重葛", "菊花", "牽牛花", "小蒼蘭", "雪花蓮", "週年紀念玫瑰", "山茶花", "石竹",
                    "鼠尾草", "睡蓮", "聖誕紅", "聖誕玫瑰", "洋桔梗", "櫻草花", "鸚鵡鬱金香", "銀蓮花", "豌豆花",
                    "萬壽菊", "勿忘草", "鳶尾花", "矮牽牛")),
            new Category(Color.BLUE, pots(Color.BLUE,
                    "風鈴草", "扶桑花", "大理花", "曇花", "鐵線蓮", "康乃馨", "海芋", "花瓣", "蝴蝶蘭", "雞冠花",
                    "金魚草", "九重葛", "菊花", "牽牛花", "小蒼蘭", "週年紀念玫瑰", "山茶花", "石竹",
                    "鼠尾草", "聖誕紅", "洋桔梗", "櫻草花", "鸚鵡鬱金香", "銀蓮花", "油菜花", "豌豆花", "勿忘草",
                    "鳶尾花", "矮牽牛")));

    private static final List<String> ALL_NAMES = allNames(CATEGORIES);
    private static final Set<String> NAME_SET = Set.copyOf(ALL_NAMES);

    private PostcardPotCatalog() {}

    static List<Category> categories() {
        return CATEGORIES;
    }

    static List<String> allNames() {
        return ALL_NAMES;
    }

    static List<String> namesForColor(Color color) {
        if (color == null) {
            return List.of();
        }
        for (Category category : CATEGORIES) {
            if (category.color() == color) {
                return category.names();
            }
        }
        return List.of();
    }

    /** 只接受目錄內的完整單列名稱，並校正實機 OCR 的常見字形誤識。 */
    static String canonicalName(String value) {
        if (value == null || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return null;
        }
        String name = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("[\\p{Z}\\s]+", "")
                .replace('夭', '天')
                .replace('島', '鳥')
                .replace('黄', '黃')
                .replace('红', '紅')
                .replace('蓝', '藍')
                .replace('鸟', '鳥');
        return NAME_SET.contains(name) ? name : null;
    }

    static boolean contains(String value) {
        return canonicalName(value) != null;
    }

    static Color colorOf(String value) {
        String canonical = canonicalName(value);
        if (canonical == null) {
            return null;
        }
        for (Color color : Color.values()) {
            if (canonical.startsWith(color.label())) {
                return color;
            }
        }
        return null;
    }

    /** 遊戲搜尋框要求顏色與花名之間保留一個半形空格。 */
    static String searchQuery(String value) {
        String canonical = canonicalName(value);
        Color color = colorOf(canonical);
        if (canonical == null || color == null) {
            return "";
        }
        return color.label() + " " + canonical.substring(color.label().length());
    }

    private static List<String> pots(Color color, String... flowers) {
        List<String> names = new ArrayList<>(flowers.length);
        for (String flower : flowers) {
            names.add(color.label() + flower);
        }
        return List.copyOf(names);
    }

    private static List<String> allNames(List<Category> categories) {
        List<String> names = new ArrayList<>();
        for (Category category : categories) {
            names.addAll(category.names());
        }
        return List.copyOf(names);
    }
}
