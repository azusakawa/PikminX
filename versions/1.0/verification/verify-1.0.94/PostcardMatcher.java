package com.pikminx.helper;

import com.android.tools.r8.RecordTag;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/* JADX INFO: loaded from: classes.dex */
final class PostcardMatcher {
    private static final Pattern SELECTED_COUNT = Pattern.compile("(\\d)\\s*/\\s*5");

    enum Page {
        MAP,
        FLOWER_DETAIL,
        WARNING,
        PETAL_SELECTION,
        PIKMIN_SELECTION,
        POSTCARD_RECEIVED,
        UNKNOWN
    }

    private enum WritingSystem {
        NONE,
        HAN,
        KANA,
        LATIN,
        OTHER
    }

    static /* synthetic */ boolean lambda$findMapPostcardName$2(PetalMatcher.Token token, PetalMatcher.Token token2) {
        return token2 != token;
    }

    PostcardMatcher() {
    }

    static final class Target extends RecordTag {
        private final String text;
        private final int x;
        private final int y;

        private /* synthetic */ boolean $record$equals(Object obj) {
            if (!(obj instanceof Target)) {
                return false;
            }
            Target target = (Target) obj;
            return this.x == target.x && this.y == target.y && Objects.equals(this.text, target.text);
        }

        private /* synthetic */ Object[] $record$getFieldsAsObjects() {
            return new Object[]{this.text, Integer.valueOf(this.x), Integer.valueOf(this.y)};
        }

        Target(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }

        public final boolean equals(Object obj) {
            return $record$equals(obj);
        }

        public final int hashCode() {
            return MainActivity$$ExternalSyntheticBackport0.m(this.x, this.y, this.text);
        }

        public String text() {
            return this.text;
        }

        public final String toString() {
            return MainActivity$$ExternalSyntheticBackport0.m($record$getFieldsAsObjects(), Target.class, "text;x;y");
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }
    }

    static final class PetalPot extends RecordTag {
        private final int count;
        private final String name;
        private final int x;
        private final int y;

        private /* synthetic */ boolean $record$equals(Object obj) {
            if (!(obj instanceof PetalPot)) {
                return false;
            }
            PetalPot petalPot = (PetalPot) obj;
            return this.count == petalPot.count && this.x == petalPot.x && this.y == petalPot.y && Objects.equals(this.name, petalPot.name);
        }

        private /* synthetic */ Object[] $record$getFieldsAsObjects() {
            return new Object[]{this.name, Integer.valueOf(this.count), Integer.valueOf(this.x), Integer.valueOf(this.y)};
        }

        PetalPot(String name, int count, int x, int y) {
            this.name = name;
            this.count = count;
            this.x = x;
            this.y = y;
        }

        public int count() {
            return this.count;
        }

        public final boolean equals(Object obj) {
            return $record$equals(obj);
        }

        public final int hashCode() {
            return MainActivity$$ExternalSyntheticBackport0.m(this.count, this.x, this.y, this.name);
        }

        public String name() {
            return this.name;
        }

        public final String toString() {
            return MainActivity$$ExternalSyntheticBackport0.m($record$getFieldsAsObjects(), PetalPot.class, "name;count;x;y");
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }
    }

    static Page detectPage(List<PetalMatcher.Token> list, int i, int i2) {
        if (hasText(list, "持有的明信片") && hasText(list, "接收")) {
            return Page.POSTCARD_RECEIVED;
        }
        if (hasText(list, "選擇皮克敏出去取回明信片") || ((hasText(list, "取回明信片") && findGo(list) != null) || ((hasSelectedPikminCounter(list) && findSortControl(list, i2) != null) || isSortMenuVisible(list, i2)))) {
            return Page.PIKMIN_SELECTION;
        }
        if (hasText(list, "選擇要使用的花瓣") || hasText(list, "下一步")) {
            return Page.PETAL_SELECTION;
        }
        if (hasText(list, "接受並繼續") && hasText(list, "注意")) {
            return Page.WARNING;
        }
        if (findUsePetals(list) != null) {
            return Page.FLOWER_DETAIL;
        }
        if (hasConfirmedMapFlowerBubble(list, i, i2)) {
            return Page.MAP;
        }
        return Page.UNKNOWN;
    }

    static Target findUsePetals(List<PetalMatcher.Token> list) {
        return findText(list, "使用花瓣就能獲得明信片");
    }

    static Target findAcceptContinue(List<PetalMatcher.Token> list) {
        return findText(list, "接受並繼續");
    }

    static Target findNext(List<PetalMatcher.Token> list) {
        return findExactText(list, "下一步");
    }

    static Target findGo(List<PetalMatcher.Token> list) {
        return (Target) exactTokens(list, "go", "g0", "60").stream().findFirst().map(new PostcardMatcher$$ExternalSyntheticLambda24()).orElse(null);
    }

    static Target findReceive(List<PetalMatcher.Token> list) {
        return findExactText(list, "接收");
    }

    static Target findDiscard(List<PetalMatcher.Token> list) {
        Target targetFindExactText = findExactText(list, "捨棄");
        return targetFindExactText != null ? targetFindExactText : findExactText(list, "舍棄");
    }

    static Target findDiscard(List<PetalMatcher.Token> list, int i, int i2) {
        Target targetFindDiscard = findDiscard(list);
        if (targetFindDiscard != null) {
            return targetFindDiscard;
        }
        Target targetFindReceive = findReceive(list);
        if (detectPage(list, i, i2) != Page.POSTCARD_RECEIVED || targetFindReceive == null) {
            return null;
        }
        float f = i;
        if (targetFindReceive.x() <= 0.5f * f || targetFindReceive.x() >= f * 0.92f) {
            return null;
        }
        float f2 = i2;
        if (targetFindReceive.y() <= 0.55f * f2 || targetFindReceive.y() >= f2 * 0.9f) {
            return null;
        }
        return new Target("捨棄", i - targetFindReceive.x(), targetFindReceive.y());
    }

    static Target findMapFlowerName(List<PetalMatcher.Token> list, final int i, final int i2) {
        String strTrim;
        ArrayList arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            String strNormalize = normalize(token.text());
            if (strNormalize.contains("花朵") && !strNormalize.contains("花瓣")) {
                double d = i;
                if (token.centerX() > 0.1d * d && token.centerX() < d * 0.9d) {
                    double d2 = i2;
                    if (token.centerY() > 0.12d * d2 && token.centerY() < d2 * 0.82d) {
                        arrayList.add(token);
                    }
                }
            }
        }
        PetalMatcher.Token token2 = (PetalMatcher.Token) arrayList.stream().min(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda26
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findMapFlowerName$0(i, i2, (PetalMatcher.Token) obj);
            }
        })).orElse(null);
        if (token2 == null) {
            return null;
        }
        int iIndexOf = token2.text().indexOf("花朵") + "花朵".length();
        if (iIndexOf >= "花朵".length()) {
            strTrim = token2.text().substring(0, iIndexOf).trim();
        } else {
            strTrim = token2.text().trim();
        }
        return new Target(strTrim, token2.centerX(), token2.centerY());
    }

    static /* synthetic */ int lambda$findMapFlowerName$0(int i, int i2, PetalMatcher.Token token) {
        return Math.abs(token.centerX() - (i / 2)) + Math.abs(token.centerY() - (i2 / 2));
    }

    static Target findMapPostcardName(List<PetalMatcher.Token> list, final int i, final int i2) {
        Target targetFindSplitMapPostcardName = findSplitMapPostcardName(list, i, i2);
        if (targetFindSplitMapPostcardName != null) {
            return targetFindSplitMapPostcardName;
        }
        Iterator<PetalMatcher.Token> it = list.iterator();
        while (it.hasNext()) {
            Target targetFindMergedMapPostcardName = findMergedMapPostcardName(it.next(), i, i2);
            if (targetFindMergedMapPostcardName != null) {
                return targetFindMergedMapPostcardName;
            }
        }
        ArrayList arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            String strNormalize = normalize(token.text());
            if (strNormalize.contains("花朵") && !strNormalize.contains("花瓣")) {
                double d = i;
                if (token.centerX() > 0.1d * d && token.centerX() < d * 0.9d) {
                    double d2 = i2;
                    if (token.centerY() > 0.12d * d2 && token.centerY() < d2 * 0.82d) {
                        arrayList.add(token);
                    }
                }
            }
        }
        final PetalMatcher.Token token2 = (PetalMatcher.Token) arrayList.stream().min(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda4
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findMapPostcardName$1(i, i2, (PetalMatcher.Token) obj);
            }
        })).orElse(null);
        if (token2 == null) {
            return null;
        }
        String strTrim = token2.text().trim();
        String[] strArrSplit = strTrim.split("\\s+");
        if (strArrSplit.length > 1) {
            String strTrim2 = String.join(" ", (CharSequence[]) Arrays.copyOfRange(strArrSplit, 1, strArrSplit.length)).trim();
            if (isDetailPostcardNameText(strTrim2)) {
                return new Target(strTrim2, token2.centerX(), token2.centerY());
            }
        }
        int iIndexOf = strTrim.indexOf("花朵");
        if (iIndexOf >= 0) {
            String strTrim3 = strTrim.substring(iIndexOf + "花朵".length()).trim();
            if (isDetailPostcardNameText(strTrim3)) {
                return new Target(strTrim3, token2.centerX(), token2.centerY());
            }
        }
        PetalMatcher.Token tokenOrElse = list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda5
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findMapPostcardName$2(token2, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda6
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.isDetailPostcardNameText(((PetalMatcher.Token) obj).text());
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda7
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findMapPostcardName$4(token2, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda8
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findMapPostcardName$5(token2, i2, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda9
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findMapPostcardName$6(token2, i, (PetalMatcher.Token) obj);
            }
        }).min(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda11
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return ((PetalMatcher.Token) obj).top();
            }
        })).orElse(null);
        if (tokenOrElse == null) {
            return null;
        }
        return new Target(tokenOrElse.text().trim(), tokenOrElse.centerX(), tokenOrElse.centerY());
    }

    static /* synthetic */ int lambda$findMapPostcardName$1(int i, int i2, PetalMatcher.Token token) {
        return Math.abs(token.centerX() - (i / 2)) + Math.abs(token.centerY() - (i2 / 2));
    }

    static /* synthetic */ boolean lambda$findMapPostcardName$4(PetalMatcher.Token token, PetalMatcher.Token token2) {
        return token2.top() >= token.bottom();
    }

    static /* synthetic */ boolean lambda$findMapPostcardName$5(PetalMatcher.Token token, int i, PetalMatcher.Token token2) {
        return ((double) (token2.top() - token.bottom())) <= ((double) i) * 0.05d;
    }

    static /* synthetic */ boolean lambda$findMapPostcardName$6(PetalMatcher.Token token, int i, PetalMatcher.Token token2) {
        return ((double) Math.abs(token2.centerX() - token.centerX())) <= ((double) i) * 0.18d;
    }

    static boolean hasConfirmedMapFlowerBubble(List<PetalMatcher.Token> list, final int i, final int i2) {
        if (findSplitMapPostcardName(list, i, i2) != null) {
            return true;
        }
        return list.stream().anyMatch(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda12
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$hasConfirmedMapFlowerBubble$7(i, i2, (PetalMatcher.Token) obj);
            }
        });
    }

    static /* synthetic */ boolean lambda$hasConfirmedMapFlowerBubble$7(int i, int i2, PetalMatcher.Token token) {
        return findMergedMapPostcardName(token, i, i2) != null;
    }

    private static Target findMergedMapPostcardName(PetalMatcher.Token token, int i, int i2) {
        if (!isMapSpeciesCandidate(token, i, i2)) {
            return null;
        }
        String[] strArrSplit = token.text().trim().split("\\s+");
        if (strArrSplit.length < 2) {
            return null;
        }
        for (int i3 = 1; i3 < strArrSplit.length; i3++) {
            String strTrim = String.join(" ", (CharSequence[]) Arrays.copyOfRange(strArrSplit, i3, strArrSplit.length)).trim();
            if (isDetailPostcardNameText(strTrim)) {
                return new Target(strTrim, token.centerX(), token.centerY());
            }
        }
        return null;
    }

    private static Target findSplitMapPostcardName(List<PetalMatcher.Token> list, final int i, final int i2) {
        ArrayList arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            if (isMapSpeciesCandidate(token, i, i2)) {
                for (PetalMatcher.Token token2 : list) {
                    if (token2 != token && isDetailPostcardNameText(token2.text())) {
                        double d = i2;
                        if (token2.top() >= ((double) token.bottom()) - (0.01d * d) && token2.top() - token.bottom() <= d * 0.06d && Math.abs(token2.centerX() - token.centerX()) <= ((double) i) * 0.2d) {
                            arrayList.add(new Target(token2.text().trim(), (token.centerX() + token2.centerX()) / 2, (token.centerY() + token2.centerY()) / 2));
                        }
                    }
                }
            }
        }
        return (Target) arrayList.stream().max(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda1
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.mapLocationTextQuality(((PostcardMatcher.Target) obj).text());
            }
        }).thenComparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda2
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findSplitMapPostcardName$9(i, i2, (PostcardMatcher.Target) obj);
            }
        })).orElse(null);
    }

    static /* synthetic */ int lambda$findSplitMapPostcardName$9(int i, int i2, Target target) {
        return -mapCenterDistance(target, i, i2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static int mapLocationTextQuality(String str) {
        int iCharCount = 0;
        int i = 0;
        int i2 = 0;
        boolean z = false;
        boolean z2 = false;
        boolean z3 = false;
        boolean z4 = false;
        boolean z5 = false;
        boolean z6 = false;
        while (true) {
            if (iCharCount >= str.length()) {
                break;
            }
            int iCodePointAt = str.codePointAt(iCharCount);
            iCharCount += Character.charCount(iCodePointAt);
            if (Character.isLetter(iCodePointAt)) {
                i++;
                Character.UnicodeScript unicodeScriptOf = Character.UnicodeScript.of(iCodePointAt);
                z |= unicodeScriptOf == Character.UnicodeScript.HAN;
                z2 |= unicodeScriptOf == Character.UnicodeScript.HIRAGANA || unicodeScriptOf == Character.UnicodeScript.KATAKANA;
                z6 |= unicodeScriptOf == Character.UnicodeScript.LATIN;
                z3 |= unicodeScriptOf == Character.UnicodeScript.HANGUL;
                z4 |= unicodeScriptOf == Character.UnicodeScript.DEVANAGARI;
                z5 |= (z || z2 || z6 || z3 || z4) ? false : true;
            } else if (!Character.isDigit(iCodePointAt) && !Character.isWhitespace(iCodePointAt)) {
                i2++;
            }
        }
        int iMin = ((i * 4) + Math.min(str.length(), 18)) - (i2 * 4);
        boolean z7 = z || z2;
        if (z7) {
            iMin += 100;
        } else if (z3 || z4 || z5) {
            iMin += 90;
        } else if (z6) {
            iMin += 75;
        }
        if (z6) {
            return (z7 || z3 || z4 || z5) ? iMin - 45 : iMin;
        }
        return iMin;
    }

    private static int mapCenterDistance(Target target, int i, int i2) {
        return Math.abs(target.x() - (i / 2)) + Math.abs(target.y() - (i2 / 2));
    }

    private static boolean isMapSpeciesCandidate(PetalMatcher.Token token, int i, int i2) {
        String strNormalize = normalize(token.text());
        if ((!strNormalize.startsWith("白色") && !strNormalize.startsWith("黃色") && !strNormalize.startsWith("紅色") && !strNormalize.startsWith("藍色") && !strNormalize.startsWith("紫色") && !strNormalize.startsWith("灰色")) || !token.text().matches(".*\\p{IsHan}.*")) {
            return false;
        }
        double d = i;
        if (token.centerX() <= 0.1d * d || token.centerX() >= d * 0.9d) {
            return false;
        }
        double d2 = i2;
        return ((double) token.centerY()) > 0.12d * d2 && ((double) token.centerY()) < d2 * 0.82d;
    }

    static Target findDetailPostcardName(List<PetalMatcher.Token> list, final int i, int i2) {
        float f = i2;
        int iRound = Math.round(0.05f * f);
        int iRound2 = Math.round(f * 0.26f);
        ArrayList<PetalMatcher.Token> arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            if (isDetailPostcardNameCandidate(token, i, iRound, iRound2)) {
                arrayList.add(token);
            }
        }
        for (PetalMatcher.Token token2 : arrayList) {
            for (PetalMatcher.Token token3 : arrayList) {
                if (token3 != token2 && token3.top() >= token2.bottom() && token3.top() - token2.bottom() <= ((double) i2) * 0.04d && Math.abs(token3.centerX() - token2.centerX()) <= ((double) i) * 0.12d) {
                    String str = token2.text() + token3.text();
                    if (isDetailPostcardNameText(str)) {
                        return new Target(str, (Math.min(token2.left(), token3.left()) + Math.max(token2.right(), token3.right())) / 2, (token2.top() + token3.bottom()) / 2);
                    }
                }
            }
        }
        final int i3 = (iRound + iRound2) / 2;
        PetalMatcher.Token token4 = (PetalMatcher.Token) arrayList.stream().min(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda3
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findDetailPostcardName$10(i, i3, (PetalMatcher.Token) obj);
            }
        })).orElse(null);
        if (token4 == null) {
            return null;
        }
        return new Target(token4.text(), token4.centerX(), token4.centerY());
    }

    static /* synthetic */ int lambda$findDetailPostcardName$10(int i, int i2, PetalMatcher.Token token) {
        return Math.abs(token.centerX() - (i / 2)) + Math.abs(token.centerY() - i2);
    }

    static Target findDetailFlowerName(List<PetalMatcher.Token> list, final int i, int i2) {
        int iMin;
        Target targetFindUsePetals = findUsePetals(list);
        float f = i2;
        int iRound = Math.round(0.42f * f);
        if (targetFindUsePetals == null) {
            iMin = Math.round(f * 0.78f);
        } else {
            iMin = Math.min(targetFindUsePetals.y() - Math.round(0.03f * f), Math.round(f * 0.78f));
        }
        if (iMin <= iRound) {
            iMin = Math.round(f * 0.78f);
        }
        ArrayList<PetalMatcher.Token> arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            if (isDetailTitleCandidate(token, i, iRound, iMin)) {
                arrayList.add(token);
            }
        }
        for (PetalMatcher.Token token2 : arrayList) {
            for (PetalMatcher.Token token3 : arrayList) {
                if (token2 != token3 && token3.top() >= token2.bottom() && token3.top() - token2.bottom() <= ((double) i2) * 0.04d && Math.abs(token3.centerX() - token2.centerX()) <= ((double) i) * 0.12d) {
                    String str = token2.text() + token3.text();
                    if (isDetailTitleText(str)) {
                        return new Target(str, (Math.min(token2.left(), token3.left()) + Math.max(token2.right(), token3.right())) / 2, (token2.top() + token3.bottom()) / 2);
                    }
                }
            }
        }
        final int i3 = (iRound + iMin) / 2;
        PetalMatcher.Token token4 = (PetalMatcher.Token) arrayList.stream().min(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda15
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findDetailFlowerName$11(i, i3, (PetalMatcher.Token) obj);
            }
        })).orElse(null);
        if (token4 == null) {
            return null;
        }
        return new Target(token4.text(), token4.centerX(), token4.centerY());
    }

    static /* synthetic */ int lambda$findDetailFlowerName$11(int i, int i2, PetalMatcher.Token token) {
        return Math.abs(token.centerX() - (i / 2)) + Math.abs(token.centerY() - i2);
    }

    static boolean isSortMenuVisible(List<PetalMatcher.Token> list, int i) {
        if (findFavoriteMenuItem(list, i) != null) {
            return hasText(list, "排序") || hasText(list, "友好度") || hasText(list, "飾品");
        }
        return false;
    }

    static Target findSortControl(List<PetalMatcher.Token> list, final int i) {
        return (Target) list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda10
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findSortControl$12((PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda21
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findSortControl$13(i, (PetalMatcher.Token) obj);
            }
        }).min(Comparator.comparingInt(new PetalMatcher$$ExternalSyntheticLambda0())).map(new PostcardMatcher$$ExternalSyntheticLambda24()).orElse(null);
    }

    static /* synthetic */ boolean lambda$findSortControl$12(PetalMatcher.Token token) {
        return normalize(token.text()).startsWith("喜愛") || normalize(token.text()).startsWith("自動") || normalize(token.text()).startsWith("發現日") || normalize(token.text()).startsWith("種類") || normalize(token.text()).startsWith("友好度") || normalize(token.text()).startsWith("飾品");
    }

    static /* synthetic */ boolean lambda$findSortControl$13(int i, PetalMatcher.Token token) {
        return ((double) token.centerY()) < ((double) i) * 0.68d;
    }

    static Target findFavoriteMenuItem(List<PetalMatcher.Token> list, final int i) {
        return (Target) list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda13
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.normalize(((PetalMatcher.Token) obj).text()).startsWith("喜愛");
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda14
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findFavoriteMenuItem$15(i, (PetalMatcher.Token) obj);
            }
        }).max(Comparator.comparingInt(new PetalMatcher$$ExternalSyntheticLambda0())).map(new PostcardMatcher$$ExternalSyntheticLambda24()).orElse(null);
    }

    static /* synthetic */ boolean lambda$findFavoriteMenuItem$15(int i, PetalMatcher.Token token) {
        return ((double) token.centerY()) > ((double) i) * 0.5d;
    }

    static int selectedPikminCount(List<PetalMatcher.Token> list) {
        Iterator<PetalMatcher.Token> it = list.iterator();
        while (it.hasNext()) {
            Matcher matcher = SELECTED_COUNT.matcher(Normalizer.normalize(it.next().text(), Normalizer.Form.NFKC));
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return 0;
    }

    private static boolean hasSelectedPikminCounter(List<PetalMatcher.Token> list) {
        Iterator<PetalMatcher.Token> it = list.iterator();
        while (it.hasNext()) {
            if (SELECTED_COUNT.matcher(Normalizer.normalize(it.next().text(), Normalizer.Form.NFKC)).find()) {
                return true;
            }
        }
        return false;
    }

    static Target findFirstPikmin(List<PetalMatcher.Token> list, int i, int i2) {
        List<Target> listFindPikminCandidates = findPikminCandidates(list, i, i2);
        if (listFindPikminCandidates.isEmpty()) {
            return null;
        }
        return listFindPikminCandidates.get(0);
    }

    static List<Target> findPikminCandidates(List<PetalMatcher.Token> list, final int i, final int i2) {
        return (List) list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda16
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$16(i2, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda17
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$17(i2, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda18
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$18(i, (PetalMatcher.Token) obj);
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda19
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return ((PetalMatcher.Token) obj).text().matches(".*\\p{IsHan}.*");
            }
        }).filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda20
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$20((PetalMatcher.Token) obj);
            }
        }).sorted(Comparator.comparingInt(new ToIntFunction() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda22
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$21((PetalMatcher.Token) obj);
            }
        }).thenComparingInt(new PetalMatcher$$ExternalSyntheticLambda1())).map(new Function() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda23
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return PostcardMatcher.lambda$findPikminCandidates$22(i2, (PetalMatcher.Token) obj);
            }
        }).collect(Collectors.toList());
    }

    static /* synthetic */ boolean lambda$findPikminCandidates$16(int i, PetalMatcher.Token token) {
        return ((double) token.centerY()) > ((double) i) * 0.46d;
    }

    static /* synthetic */ boolean lambda$findPikminCandidates$17(int i, PetalMatcher.Token token) {
        return ((double) token.centerY()) < ((double) i) * 0.88d;
    }

    static /* synthetic */ boolean lambda$findPikminCandidates$18(int i, PetalMatcher.Token token) {
        return ((double) token.centerX()) < ((double) i) * 0.92d;
    }

    static /* synthetic */ boolean lambda$findPikminCandidates$20(PetalMatcher.Token token) {
        return !isPikminUiText(token.text());
    }

    static /* synthetic */ int lambda$findPikminCandidates$21(PetalMatcher.Token token) {
        return token.centerY() / 40;
    }

    static /* synthetic */ Target lambda$findPikminCandidates$22(int i, PetalMatcher.Token token) {
        return new Target(token.text(), token.centerX(), Math.max(0, token.top() - Math.round(i * 0.065f)));
    }

    static List<Target> findTopRowPikminSlots(int i, int i2) {
        return findPikminSelectionSlots(i, i2).subList(0, 5);
    }

    static List<Target> findPikminSelectionSlots(int i, int i2) {
        float[] fArr = {0.13f, 0.32f, 0.51f, 0.7f, 0.89f};
        float[] fArr2 = {0.515f, 0.66f, 0.805f};
        ArrayList arrayList = new ArrayList(12);
        for (int i3 = 0; i3 < 3; i3++) {
            float f = fArr2[i3];
            for (int i4 = 0; i4 < 5; i4++) {
                arrayList.add(new Target("pikmin-slot-" + (arrayList.size() + 1), Math.round(i * fArr[i4]), Math.round(i2 * f)));
                if (arrayList.size() == 12) {
                    return MainActivity$$ExternalSyntheticBackport0.m((Collection) arrayList);
                }
            }
        }
        return MainActivity$$ExternalSyntheticBackport0.m((Collection) arrayList);
    }

    static PetalPot findAvailablePetalPot(List<PetalMatcher.Token> list, String str, int i, int i2, int i3) {
        PetalPotDetector.Match matchFind;
        String strCanonicalName = PostcardPotCatalog.canonicalName(str);
        if (strCanonicalName == null || (matchFind = PetalPotDetector.find(list, strCanonicalName, i, i2, i3, 0.53f, 0.96f, 0.16f, 0.2f, new Function() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda27
            @Override // java.util.function.Function
            public final Object apply(Object obj) {
                return PostcardMatcher.lambda$findAvailablePetalPot$23((String) obj);
            }
        })) == null) {
            return null;
        }
        return new PetalPot(strCanonicalName, matchFind.count(), matchFind.x(), Math.max(0, matchFind.labelTop() - Math.round(i3 * 0.075f)));
    }

    static /* synthetic */ String lambda$findAvailablePetalPot$23(String str) {
        String strCanonicalName = PostcardPotCatalog.canonicalName(str);
        return strCanonicalName == null ? "" : normalize(strCanonicalName);
    }

    static PetalPot findSingleVisiblePetalPot(List<PetalMatcher.Token> list, String str, int i, int i2, int i3) {
        PetalPotDetector.Match matchFindSingleVisible;
        String strCanonicalName = PostcardPotCatalog.canonicalName(str);
        if (strCanonicalName == null || (matchFindSingleVisible = PetalPotDetector.findSingleVisible(list, i, i2, i3, 0.53f, 0.96f, 0.16f, 0.2f, new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda28
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.looksLikeResultLabel((String) obj);
            }
        })) == null) {
            return null;
        }
        return new PetalPot(strCanonicalName, matchFindSingleVisible.count(), matchFindSingleVisible.x(), Math.max(0, matchFindSingleVisible.labelTop() - Math.round(i3 * 0.075f)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean looksLikeResultLabel(String str) {
        return str != null && str.indexOf(10) < 0 && str.indexOf(13) < 0 && str.codePoints().filter(new IntPredicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda29
            @Override // java.util.function.IntPredicate
            public final boolean test(int i) {
                return Character.isLetter(i);
            }
        }).count() >= 2;
    }

    static String petalListSignature(List<PetalMatcher.Token> list, final int i, final int i2) {
        return PetalMatcher.screenSignature((List) list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda25
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.isPetalLabel((PetalMatcher.Token) obj, i, i2);
            }
        }).collect(Collectors.toList()));
    }

    static List<String> visiblePetalPotNames(List<PetalMatcher.Token> list, int i, int i2) {
        String strCanonicalName;
        ArrayList arrayList = new ArrayList();
        for (PetalMatcher.Token token : list) {
            if (isPetalLabel(token, i, i2) && (strCanonicalName = PostcardPotCatalog.canonicalName(token.text())) != null && !arrayList.contains(strCanonicalName)) {
                arrayList.add(strCanonicalName);
            }
        }
        return MainActivity$$ExternalSyntheticBackport0.m((Collection) arrayList);
    }

    private static List<PetalMatcher.Token> matchingTargetLabels(List<PetalMatcher.Token> list, String str, int i, int i2) {
        String strNormalizePetalPotName = normalizePetalPotName(str);
        ArrayList arrayList = new ArrayList();
        if (!strNormalizePetalPotName.isEmpty()) {
            for (PetalMatcher.Token token : list) {
                if (isPetalLabel(token, i, i2) && normalizePetalPotName(token.text()).equals(strNormalizePetalPotName)) {
                    arrayList.add(token);
                }
            }
        }
        return arrayList;
    }

    private static String normalizePetalPotName(String str) {
        String strCanonicalName = PostcardPotCatalog.canonicalName(str);
        return strCanonicalName == null ? "" : normalize(strCanonicalName);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isPetalLabel(PetalMatcher.Token token, int i, int i2) {
        String strNormalize = normalize(token.text());
        if (!token.text().matches(".*\\p{IsHan}.*")) {
            return false;
        }
        double d = i;
        if (token.centerX() <= 0.03d * d || token.centerX() >= d * 0.97d) {
            return false;
        }
        double d2 = i2;
        return (((double) token.centerY()) <= 0.53d * d2 || ((double) token.centerY()) >= d2 * 0.96d || strNormalize.contains("選擇") || strNormalize.equals("下一步") || strNormalize.equals("花瓣")) ? false : true;
    }

    private static boolean isDetailTitleCandidate(PetalMatcher.Token token, int i, int i2, int i3) {
        double d = i;
        return ((double) token.centerX()) > 0.2d * d && ((double) token.centerX()) < d * 0.8d && token.centerY() >= i2 && token.centerY() <= i3 && isDetailTitleText(token.text());
    }

    private static boolean isDetailTitleText(String str) {
        String strNormalize = normalize(str);
        return (!str.matches(".*\\p{IsHan}.*") || str.matches(".*\\d.*") || strNormalize.length() < 3 || strNormalize.length() > 10 || strNormalize.contains("?前地") || strNormalize.contains("?梁\ue663") || strNormalize.contains("?\ue393縑?") || strNormalize.contains("瘜冽?") || strNormalize.contains("?亙?") || strNormalize.contains("銝\uf55c?") || strNormalize.contains("再過") || strNormalize.contains("會變回") || strNormalize.contains("階段") || strNormalize.contains("前往這裡")) ? false : true;
    }

    private static boolean isDetailPostcardNameCandidate(PetalMatcher.Token token, int i, int i2, int i3) {
        double d = i;
        return ((double) token.centerX()) > 0.18d * d && ((double) token.centerX()) < d * 0.82d && token.centerY() >= i2 && token.centerY() <= i3 && isDetailPostcardNameText(token.text());
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isDetailPostcardNameText(String str) {
        String strNormalize = normalize(str);
        return (!str.matches(".*\\p{L}.*") || strNormalize.length() < 2 || strNormalize.length() > 30 || strNormalize.contains("花朵") || strNormalize.contains("花瓣") || strNormalize.contains("明信片") || strNormalize.contains("目前地") || strNormalize.contains("注意") || strNormalize.contains("接受") || strNormalize.contains("下一步") || strNormalize.contains("再過") || strNormalize.contains("會變回") || strNormalize.contains("階段") || strNormalize.contains("前往這裡") || strNormalize.equals("tottori")) ? false : true;
    }

    private static PetalMatcher.Token nearestCountAbove(List<PetalMatcher.Token> list, PetalMatcher.Token token, int i, int i2) {
        PetalMatcher.Token token2 = null;
        double d = Double.MAX_VALUE;
        for (PetalMatcher.Token token3 : list) {
            if (parseCount(token3.text()) != null) {
                int iAbs = Math.abs(token3.centerX() - token.centerX());
                int pVar = token.top() - token3.centerY();
                if (iAbs <= ((double) i) * 0.16d && pVar >= 0 && pVar <= ((double) i2) * 0.2d) {
                    double d2 = (iAbs * iAbs) + (pVar * pVar);
                    if (d2 < d) {
                        token2 = token3;
                        d = d2;
                    }
                }
            }
        }
        return token2;
    }

    private static Integer parseCount(String str) {
        String strTrim = Normalizer.normalize(str, Normalizer.Form.NFKC).replaceAll("\\s*\\+\\s*", "+").trim();
        if (!strTrim.matches("\\+?[0-9OoIl|][0-9OoIl|,.'\\s]{0,7}\\+?")) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(strTrim.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('|', '1').replaceAll("[,.'\\s+]", "")));
        } catch (NumberFormatException unused) {
            return null;
        }
    }

    private static boolean isPikminUiText(String str) {
        String strNormalize = normalize(str);
        return strNormalize.contains("選擇皮克敏") || strNormalize.equals("排序") || strNormalize.equals("自動") || strNormalize.equals("喜愛") || strNormalize.equals("取消") || strNormalize.equals("發現日") || strNormalize.equals("種類") || strNormalize.equals("友好度") || strNormalize.equals("飾品");
    }

    private static boolean hasText(List<PetalMatcher.Token> list, String str) {
        return findText(list, str) != null;
    }

    private static Target findExactText(List<PetalMatcher.Token> list, String str) {
        final String strNormalize = normalize(str);
        return (Target) list.stream().filter(new Predicate() { // from class: com.pikminx.helper.PostcardMatcher$$ExternalSyntheticLambda0
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return PostcardMatcher.normalize(((PetalMatcher.Token) obj).text()).equals(strNormalize);
            }
        }).findFirst().map(new PostcardMatcher$$ExternalSyntheticLambda24()).orElse(null);
    }

    private static Target findText(List<PetalMatcher.Token> list, String str) {
        String strNormalize = normalize(str);
        for (PetalMatcher.Token token : list) {
            if (normalize(token.text()).contains(strNormalize)) {
                return target(token);
            }
        }
        for (PetalMatcher.Token token2 : list) {
            for (PetalMatcher.Token token3 : list) {
                if (token2 != token3 && token3.top() >= token2.top() && Math.abs(token2.centerX() - token3.centerX()) <= 180 && token3.top() - token2.bottom() <= 100 && normalize(token2.text() + token3.text()).contains(strNormalize)) {
                    return new Target(token2.text() + token3.text(), (Math.min(token2.left(), token3.left()) + Math.max(token2.right(), token3.right())) / 2, (token2.top() + token3.bottom()) / 2);
                }
            }
        }
        return null;
    }

    private static List<PetalMatcher.Token> exactTokens(List<PetalMatcher.Token> list, String... strArr) {
        ArrayList arrayList = new ArrayList();
        for (String str : strArr) {
            arrayList.add(normalize(str));
        }
        ArrayList arrayList2 = new ArrayList();
        for (PetalMatcher.Token token : list) {
            if (arrayList.contains(normalize(token.text()))) {
                arrayList2.add(token);
            }
        }
        return arrayList2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static Target target(PetalMatcher.Token token) {
        return new Target(token.text(), token.centerX(), token.centerY());
    }

    static String normalize(String str) {
        if (str == null) {
            return "";
        }
        return Normalizer.normalize(str, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    static String normalizeLocationName(String str) {
        return normalize(str).replaceAll("[<>‹›«»←→↑↓↗↘↙↖▶▷▸❯]+", "");
    }

    static boolean usesDifferentWritingSystem(String str, String str2) {
        return writingSystem(str) != writingSystem(str2);
    }

    private static WritingSystem writingSystem(String str) {
        boolean z;
        boolean z2;
        int iCharCount = 0;
        boolean z3 = false;
        if (str != null) {
            int i = 0;
            z = false;
            z2 = false;
            while (iCharCount < str.length()) {
                int iCodePointAt = str.codePointAt(iCharCount);
                iCharCount += Character.charCount(iCodePointAt);
                if (Character.UnicodeScript.of(iCodePointAt) == Character.UnicodeScript.HAN) {
                    z3 = true;
                } else if (Character.UnicodeScript.of(iCodePointAt) == Character.UnicodeScript.HIRAGANA || Character.UnicodeScript.of(iCodePointAt) == Character.UnicodeScript.KATAKANA) {
                    i = 1;
                } else if (Character.UnicodeScript.of(iCodePointAt) == Character.UnicodeScript.LATIN) {
                    z = true;
                } else if (Character.isLetter(iCodePointAt)) {
                    z2 = true;
                }
            }
            iCharCount = i;
        } else {
            z = false;
            z2 = false;
        }
        if (iCharCount != 0) {
            return WritingSystem.KANA;
        }
        if (z3) {
            return WritingSystem.HAN;
        }
        if (z) {
            return WritingSystem.LATIN;
        }
        return z2 ? WritingSystem.OTHER : WritingSystem.NONE;
    }
}
