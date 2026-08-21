package com.pikminx.helper;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 同時使用 APK 內建的 ML Kit 文字系統模型，將文字區塊轉成自動化流程可用的 token。
 * 離線模型涵蓋拉丁、中文、天城文、日文與韓文；未涵蓋文字系統不能假裝已讀懂其語意。
 */
final class OcrScanner implements AutoCloseable {
    interface Callback {
        void onSuccess(List<PetalMatcher.Token> tokens);
        void onFailure(Exception error);
    }

    private enum Script {
        LATIN("Latin"),
        CHINESE("Chinese"),
        DEVANAGARI("Devanagari"),
        JAPANESE("Japanese"),
        KOREAN("Korean");

        private final String label;

        Script(String label) {
            this.label = label;
        }
    }

    private record RecognizerEntry(Script script, TextRecognizer recognizer) {}
    private record ScoredToken(PetalMatcher.Token token, int score) {}

    private final List<RecognizerEntry> recognizers = List.of(
            new RecognizerEntry(Script.LATIN, TextRecognition.getClient(
                    new TextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.CHINESE, TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.DEVANAGARI, TextRecognition.getClient(
                    new DevanagariTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.JAPANESE, TextRecognition.getClient(
                    new JapaneseTextRecognizerOptions.Builder().build())),
            new RecognizerEntry(Script.KOREAN, TextRecognition.getClient(
                    new KoreanTextRecognizerOptions.Builder().build())));

    static List<String> supportedScriptNames() {
        return List.of("Latin", "Chinese", "Devanagari", "Japanese", "Korean");
    }

    /** 非同步辨識單張遊戲截圖；所有內建文字系統完成後才回傳合併結果。 */
    void scan(Bitmap bitmap, Executor executor, Callback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        List<ScoredToken> recognized = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pending = new AtomicInteger(recognizers.size());
        AtomicReference<Exception> firstFailure = new AtomicReference<>();
        for (RecognizerEntry entry : recognizers) {
            entry.recognizer().process(image)
                    .addOnSuccessListener(executor, text -> recognized.addAll(tokens(text, entry.script())))
                    .addOnFailureListener(error -> firstFailure.compareAndSet(null, error))
                    .addOnCompleteListener(executor, task -> {
                        if (pending.decrementAndGet() != 0) {
                            return;
                        }
                        List<PetalMatcher.Token> merged = merge(recognized);
                        Exception failure = firstFailure.get();
                        if (merged.isEmpty() && failure != null) {
                            callback.onFailure(failure);
                        } else {
                            callback.onSuccess(merged);
                        }
                    });
        }
    }

    /**
     * 花盆清單只含中文名稱；局部放大後只跑中文模型，避免其他文字系統覆蓋
     * 同一列結果，也能把第二次確認的延遲控制在可接受範圍。
     */
    void scanChinese(Bitmap bitmap, Executor executor, Callback callback) {
        RecognizerEntry chinese = recognizers.stream()
                .filter(entry -> entry.script() == Script.CHINESE)
                .findFirst()
                .orElseThrow();
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        chinese.recognizer().process(image)
                .addOnSuccessListener(executor, text -> {
                    List<PetalMatcher.Token> result = new ArrayList<>();
                    for (ScoredToken value : tokens(text, Script.CHINESE)) {
                        result.add(value.token());
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(executor, callback::onFailure);
    }

    /** 將每個模型的文字與框座標保留為可依文字系統選優的 token。 */
    private List<ScoredToken> tokens(Text text, Script script) {
        List<ScoredToken> result = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                android.graphics.Rect box = line.getBoundingBox();
                if (box != null && !line.getText().isBlank()) {
                    PetalMatcher.Token token = new PetalMatcher.Token(
                            line.getText(), box.left, box.top, box.right, box.bottom);
                    result.add(new ScoredToken(token, score(script, line.getText())));
                }
            }
        }
        return result;
    }

    /** 合併同一行的多模型結果；日文假名、韓文與天城文優先使用對應模型。 */
    private List<PetalMatcher.Token> merge(List<ScoredToken> recognized) {
        List<ScoredToken> ordered = new ArrayList<>(recognized);
        ordered.sort(Comparator
                .comparingInt((ScoredToken value) -> value.token().top())
                .thenComparingInt(value -> value.token().left())
                .thenComparing(Comparator.comparingInt(ScoredToken::score).reversed()));
        List<ScoredToken> merged = new ArrayList<>();
        for (ScoredToken candidate : ordered) {
            int duplicateIndex = duplicateIndex(merged, candidate.token());
            if (duplicateIndex < 0) {
                merged.add(candidate);
            } else if (candidate.score() > merged.get(duplicateIndex).score()) {
                merged.set(duplicateIndex, candidate);
            }
        }
        merged.sort(Comparator
                .comparingInt((ScoredToken value) -> value.token().top())
                .thenComparingInt(value -> value.token().left()));
        List<PetalMatcher.Token> result = new ArrayList<>(merged.size());
        for (ScoredToken value : merged) {
            result.add(value.token());
        }
        return result;
    }

    private int duplicateIndex(List<ScoredToken> values, PetalMatcher.Token candidate) {
        for (int index = 0; index < values.size(); index++) {
            PetalMatcher.Token existing = values.get(index).token();
            int overlapLeft = Math.max(existing.left(), candidate.left());
            int overlapTop = Math.max(existing.top(), candidate.top());
            int overlapRight = Math.min(existing.right(), candidate.right());
            int overlapBottom = Math.min(existing.bottom(), candidate.bottom());
            int overlap = Math.max(0, overlapRight - overlapLeft)
                    * Math.max(0, overlapBottom - overlapTop);
            int existingArea = Math.max(1, existing.right() - existing.left())
                    * Math.max(1, existing.bottom() - existing.top());
            int candidateArea = Math.max(1, candidate.right() - candidate.left())
                    * Math.max(1, candidate.bottom() - candidate.top());
            int union = existingArea + candidateArea - overlap;
            if (overlap * 100 >= union * 72) {
                return index;
            }
        }
        return -1;
    }

    private int score(Script script, String text) {
        int letters = 0;
        boolean han = false;
        boolean kana = false;
        boolean hangul = false;
        boolean devanagari = false;
        boolean latin = false;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                letters++;
            }
            Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(codePoint);
            han |= unicodeScript == Character.UnicodeScript.HAN;
            kana |= unicodeScript == Character.UnicodeScript.HIRAGANA
                    || unicodeScript == Character.UnicodeScript.KATAKANA;
            hangul |= unicodeScript == Character.UnicodeScript.HANGUL;
            devanagari |= unicodeScript == Character.UnicodeScript.DEVANAGARI;
            latin |= unicodeScript == Character.UnicodeScript.LATIN;
        }
        int scriptMatch = switch (script) {
            case LATIN -> latin ? 200 : 0;
            case CHINESE -> han && !kana ? 180 : 0;
            case DEVANAGARI -> devanagari ? 220 : 0;
            case JAPANESE -> kana ? 220 : han ? 140 : 0;
            case KOREAN -> hangul ? 220 : 0;
        };
        return scriptMatch + Math.min(letters, 40);
    }

    /** 釋放 ML Kit recognizer，避免無障礙服務重啟時累積資源。 */
    @Override
    public void close() {
        for (RecognizerEntry entry : recognizers) {
            entry.recognizer().close();
        }
    }
}
