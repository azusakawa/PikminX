package com.pikminx.helper;

import android.graphics.Bitmap;
import android.os.Build;

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
    interface FrameCallback {
        void onSuccess(OcrScan.Frame frame);
        void onFailure(Exception error);
    }

    private enum Script {
        LATIN,
        CHINESE,
        DEVANAGARI,
        JAPANESE,
        KOREAN
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

    /** 依流程 profile 統一裁切、縮放、辨識及還原來源座標。 */
    void scan(
            Bitmap bitmap,
            OcrScan.Profile profile,
            CaptureGeometry captureGeometry,
            Executor executor,
            FrameCallback callback) {
        if (captureGeometry == null) {
            callback.onFailure(new IllegalArgumentException("Capture geometry is required"));
            return;
        }
        OcrScan.Transform transform = OcrScan.Transform.create(
                profile, bitmap.getWidth(), bitmap.getHeight(), captureGeometry);
        Bitmap analysis;
        try {
            analysis = analysisBitmap(bitmap, transform);
        } catch (RuntimeException error) {
            if (BuildConfig.GEOMETRY_VALIDATION) {
                recordGeometryFailure(profile, transform, captureGeometry, error);
            }
            callback.onFailure(error);
            return;
        }
        boolean ownsAnalysis = analysis != bitmap;
        InputImage image;
        try {
            image = InputImage.fromBitmap(analysis, 0);
        } catch (RuntimeException error) {
            if (ownsAnalysis) {
                analysis.recycle();
            }
            if (BuildConfig.GEOMETRY_VALIDATION) {
                recordGeometryFailure(profile, transform, captureGeometry, error);
            }
            callback.onFailure(error);
            return;
        }
        List<ScoredToken> recognized = Collections.synchronizedList(new ArrayList<>());
        RecognizerEntry chineseRecognizer = recognizers.stream()
                .filter(entry -> entry.script() == Script.CHINESE)
                .findFirst()
                .orElseThrow();
        List<RecognizerEntry> selectedRecognizers = profile.scriptMode()
                == OcrScan.ScriptMode.CHINESE
                ? List.of(chineseRecognizer)
                : recognizers;
        AtomicInteger pending = new AtomicInteger(selectedRecognizers.size());
        AtomicReference<Exception> firstFailure = new AtomicReference<>();
        long startedAt = android.os.SystemClock.elapsedRealtime();
        for (RecognizerEntry entry : selectedRecognizers) {
            entry.recognizer().process(image)
                    .addOnSuccessListener(executor, text -> recognized.addAll(tokens(text, entry.script())))
                    .addOnFailureListener(executor, error -> firstFailure.compareAndSet(null, error))
                    .addOnCompleteListener(executor, task -> {
                        if (pending.decrementAndGet() != 0) {
                            return;
                        }
                        List<PetalMatcher.Token> merged = transform.toSourceTokens(merge(recognized));
                        Exception failure = firstFailure.get();
                        if (merged.isEmpty() && failure != null) {
                            try {
                                if (BuildConfig.GEOMETRY_VALIDATION) {
                                    recordGeometryFailure(
                                            profile, transform, captureGeometry, failure);
                                }
                                callback.onFailure(failure);
                            } finally {
                                if (ownsAnalysis) {
                                    analysis.recycle();
                                }
                            }
                            return;
                        }
                        OcrScan.Frame frame = new OcrScan.Frame(
                                profile,
                                transform,
                                merged,
                                android.os.SystemClock.elapsedRealtime() - startedAt,
                                analysis::getPixel,
                                captureGeometry);
                        try {
                            if (BuildConfig.GEOMETRY_VALIDATION) {
                                recordGeometrySuccess(frame);
                            }
                            callback.onSuccess(frame);
                        } finally {
                            if (ownsAnalysis) {
                                analysis.recycle();
                            }
                        }
                    });
        }
    }

    private void recordGeometrySuccess(OcrScan.Frame frame) {
        if (!BuildConfig.GEOMETRY_VALIDATION) {
            return;
        }
        try {
            GeometryValidation.log(GeometryValidation.success(Build.VERSION.SDK_INT, frame));
        } catch (RuntimeException ignored) {
            // Developer diagnostics must never alter OCR behavior.
        }
    }

    private void recordGeometryFailure(
            OcrScan.Profile profile,
            OcrScan.Transform transform,
            CaptureGeometry captureGeometry,
            Exception error) {
        if (!BuildConfig.GEOMETRY_VALIDATION) {
            return;
        }
        try {
            GeometryValidation.log(GeometryValidation.failure(
                    Build.VERSION.SDK_INT, profile, transform, captureGeometry, error));
        } catch (RuntimeException ignored) {
            // Developer diagnostics must never alter OCR behavior.
        }
    }

    private Bitmap analysisBitmap(Bitmap source, OcrScan.Transform transform) {
        if (transform.usesSourceBitmap()) {
            return source;
        }
        Bitmap crop = null;
        try {
            crop = Bitmap.createBitmap(
                    source,
                    transform.cropLeft(),
                    transform.cropTop(),
                    transform.cropWidth(),
                    transform.cropHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(
                    crop, transform.analysisWidth(), transform.analysisHeight(), true);
            if (scaled != crop) {
                crop.recycle();
            }
            return scaled;
        } catch (RuntimeException error) {
            if (crop != null && crop != source && !crop.isRecycled()) {
                crop.recycle();
            }
            throw error;
        }
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
