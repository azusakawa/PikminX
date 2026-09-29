package com.pikminx.helper;

import java.util.List;

/** Host-only algorithm regression. Not device or gameplay acceptance. */
public final class ExpeditionRecognitionRegression {
    public static void main(String[] args) {
        int[] pixels = new int[64 * 64], inverse = new int[pixels.length];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int i = y * 64 + x;
            pixels[i] = 0xff000000 | (30 + x * 2) << 16 | (40 + y * 2) << 8 | (100 + (x + y) % 31);
            inverse[i] = 0xff000000 | (~pixels[i] & 0xffffff);
        }
        var bounds = new ExpeditionVision.Bounds(0, 0, 64, 64);
        var bank = ExpeditionRecognition.rank("FRUIT", List.of(
                new ExpeditionRecognition.Template("inverse", 64, 64, inverse),
                new ExpeditionRecognition.Template("exact", 64, 64, pixels)), bounds, (x, y) -> pixels[y * 64 + x]);
        check(bank.bestTemplate().equals("exact") && bank.secondTemplate().equals("inverse"), "top two");
        check(bank.bestScore() > .999 && bank.margin() > 0, "aligned exact score and margin");
        var flat = ExpeditionRecognition.rank("FRUIT", List.of(
                new ExpeditionRecognition.Template("exact", 64, 64, pixels)), bounds, (x, y) -> 0xffaa1122);
        check(Double.isNaN(flat.bestScore()), "flat colored patch is not visual identity evidence");
        var empty = ExpeditionRecognition.rank("SEEDLING", List.of(), bounds, (x, y) -> pixels[y * 64 + x]);
        check(Double.isNaN(empty.bestScore()) && Double.isNaN(empty.margin()), "empty bank");
        check(ExpeditionRecognition.visual(bank, empty) == ExpeditionRecognition.Classification.UNKNOWN,
                "perfect score never authorizes uncalibrated visual dispatch");
        check(ExpeditionRecognition.visual(bank, bank) == ExpeditionRecognition.Classification.AMBIGUOUS,
                "cross-bank tie");
        var cache = new ExpeditionRecognition.ScoreCache();
        var cachedScores = new ExpeditionRecognition.Scores[]{bank, empty};
        cache.put(bounds, pixels, cachedScores);
        check(cache.find(bounds, pixels) != null, "exact fresh ROI can reuse pure scores");
        int saved = pixels[0]; pixels[0] ^= 1;
        check(cache.find(bounds, pixels) == null, "one changed pixel invalidates scores");
        pixels[0] = saved;
        check(cache.find(new ExpeditionVision.Bounds(1, 0, 65, 64), pixels) == null, "changed bounds invalidates scores");
        var returned = cache.find(bounds, pixels); returned[0] = empty;
        check(cache.find(bounds, pixels)[0] == bank, "caller cannot mutate cached result array");
        cache.clear();
        check(cache.find(bounds, pixels) == null, "run/viewport reset invalidates observation cache");
        var votes = new ExpeditionRecognition.Consensus();
        var fruit = ExpeditionRecognition.Classification.FRUIT;
        var seedling = ExpeditionRecognition.Classification.SEEDLING;
        var unknown = ExpeditionRecognition.Classification.UNKNOWN;
        check(votes.observe(1, 1, "card", fruit) == unknown, "first capture");
        check(votes.observe(1, 1, "card", fruit) == unknown, "same capture OCR replay");
        check(votes.observe(1, 2, "card", fruit) == fruit, "fresh consensus");
        check(votes.observe(1, 3, "card", seedling) == ExpeditionRecognition.Classification.AMBIGUOUS,
                "conflict");
        check(votes.observe(2, 4, "card", fruit) == unknown, "run invalidation");
        check(votes.observe(2, 3, "card", fruit) == unknown, "out of order");
        check(votes.observe(2, 5, "another", fruit) == unknown, "candidate identity");
        check(votes.observe(2, 6, "", unknown) == unknown, "miss invalidation");
        check(votes.observe(2, 7, "another", fruit) == unknown, "miss breaks consensus");
        votes.reset();
        check(votes.observe(2, 8, "another", fruit) == unknown, "stop invalidation");
        votes.reset();
        for (int i = 1; i <= 5; i++) {
            check(votes.observe(3, i, "changing-rank-" + i, seedling) == unknown, "identity must not mix votes");
            check(votes.observations() == i, "identity changes do not reset burst budget");
        }
        votes.observe(3, 5, "changing-rank-5", seedling);
        check(votes.observations() == 5, "replayed capture does not spend another observation");
        votes.observe(4, 6, "new-run", fruit);
        check(votes.observations() == 1, "new run resets observation budget");
        System.out.println("PASS: aligned score/ranking/margin, uncalibrated safety, fresh-frame OCR consensus");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
