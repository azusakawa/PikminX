package com.pikminx.helper;

/** Raw bank evidence is never a dispatch permission without device calibration. */
final class ExpeditionRecognition {
    static final String CALIBRATION = "XIAOMI_HELDOUT_BANK_OVERRIDES_20260923";
    static final String METRIC = "masked-rgb-ncc-40-bank-align-v4";
    // Independent Xiaomi frames: supported Seedlings and per-template exceptions
    // below; observed ICE cross-matches remain below the .85 Blue score gate.
    // Fruit positives/negatives were rechecked with this metric. Coverage is limited.
    static final double FRUIT_MIN_SCORE = 0.85;
    static final double FRUIT_MIN_MARGIN = 0.04;
    static final double SEEDLING_MIN_SCORE = 0.85;
    static final double SEEDLING_MIN_MARGIN = 0.12;
    static final double SEEDLING_WHITE_MIN_SCORE = 0.80;
    static final double SEEDLING_BLUE_MIN_MARGIN = 0.10;
    static final double SEEDLING_HUGE_MIN_SCORE = 0.81;
    static final double SEEDLING_HUGE_MIN_MARGIN = 0.11;
    static final double SEEDLING_BLACK_MIN_SCORE = 0.75;
    static final double SEEDLING_BLACK_MIN_MARGIN = 0.20;
    private static final int SIDE = 40;

    /** Immutable normalized features, computed once from the original PNG pixels. */
    static final class Template {
        final String name;
        final double aspect;
        final int[] points;
        final double[] centered;
        final double energy;

        Template(String name, int width, int height, int[] pixels) {
            if (width <= 0 || height <= 0 || (long) width * height != pixels.length)
                throw new IllegalArgumentException("Invalid template dimensions");
            this.name = name;
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int c = opaque(pixels[y * width + x]);
                if (minimumChannel(c) < 220) {
                    left = Math.min(left, x); top = Math.min(top, y);
                    right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
            }
            if (right < left) {
                aspect = 1; points = new int[0]; centered = new double[0]; energy = 0; return;
            }
            ExpeditionVision.Bounds bounds = new ExpeditionVision.Bounds(left, top, right + 1, bottom + 1);
            aspect = bounds.width() / (double) bounds.height();
            int[] selected = new int[SIDE * SIDE];
            double[] values = new double[SIDE * SIDE * 3];
            int count = 0, firstColor = 0; boolean spatialVariation = false; double sum = 0;
            java.util.function.IntBinaryOperator pixelAt = (x, y) -> opaque(pixels[y * width + x]);
            for (int y = 0; y < SIDE; y++) for (int x = 0; x < SIDE; x++) {
                int c = sample(bounds, x, y, pixelAt);
                if (minimumChannel(c) >= 245) continue;
                if (count == 0) firstColor = c; else if (c != firstColor) spatialVariation = true;
                selected[count] = y * SIDE + x;
                for (int channel = 0; channel < 3; channel++) {
                    int value = (c >>> (channel * 8)) & 255;
                    values[count * 3 + channel] = value; sum += value;
                }
                count++;
            }
            points = java.util.Arrays.copyOf(selected, count);
            centered = java.util.Arrays.copyOf(values, count * 3);
            double mean = count == 0 ? 0 : sum / (count * 3), variance = 0;
            for (int i = 0; i < centered.length; i++) {
                centered[i] -= mean; variance += centered[i] * centered[i];
            }
            energy = spatialVariation ? variance : 0;
        }
    }

    enum Classification { FRUIT, SEEDLING, UNKNOWN, AMBIGUOUS }
    record Scores(String bank, String bestTemplate, double bestScore,
                  String secondTemplate, double secondScore) {
        double margin() { return bestScore - secondScore; }
    }

    static Classification visual(Scores fruit, Scores seedling) {
        boolean fruitClear = clear(fruit, FRUIT_MIN_SCORE, FRUIT_MIN_MARGIN);
        boolean seedlingClear = clear(seedling, SEEDLING_MIN_SCORE, SEEDLING_MIN_MARGIN);
        if (fruitClear && seedlingClear) {
            double crossMargin = Math.abs(fruit.bestScore() - seedling.bestScore());
            if (crossMargin < Math.max(FRUIT_MIN_MARGIN, SEEDLING_MIN_MARGIN)) {
                return Classification.AMBIGUOUS;
            }
            return fruit.bestScore() > seedling.bestScore()
                    ? Classification.FRUIT : Classification.SEEDLING;
        }
        if (fruitClear) return Classification.FRUIT;
        if (seedlingClear) return Classification.SEEDLING;
        return Classification.UNKNOWN;
    }

    /** Xiaomi evidence: either visual bank can cross-match Green Apple; title OCR resolves it. */
    static boolean requiresTitleOcr(Classification classification, Scores fruit) {
        return fruit != null
                && "large_green_apple.png".equals(fruit.bestTemplate());
    }

    private static boolean clear(Scores scores, double minimum, double margin) {
        if (scores != null && "SEEDLING".equals(scores.bank())) {
            if ("seedling_white.png".equals(scores.bestTemplate())) {
                minimum = SEEDLING_WHITE_MIN_SCORE;
            } else if ("seedling_blue.png".equals(scores.bestTemplate())) {
                margin = SEEDLING_BLUE_MIN_MARGIN;
            } else if ("seedling_huge.png".equals(scores.bestTemplate())) {
                minimum = SEEDLING_HUGE_MIN_SCORE;
                margin = SEEDLING_HUGE_MIN_MARGIN;
            } else if ("seedling_black.png".equals(scores.bestTemplate())) {
                minimum = SEEDLING_BLACK_MIN_SCORE;
                margin = SEEDLING_BLACK_MIN_MARGIN;
            }
        }
        return scores != null
                && Double.isFinite(scores.bestScore())
                && Double.isFinite(scores.margin())
                && scores.bestScore() >= minimum
                && scores.margin() >= margin;
    }

    /** Bounded run-local memoization of pure scores, never of action geometry or admission. */
    static final class ScoreCache {
        private record Entry(ExpeditionVision.Bounds bounds, int hash, int[] pixels, Scores[] scores) {}
        private final java.util.ArrayDeque<Entry> entries = new java.util.ArrayDeque<>();
        private int retainedPixels;
        private long hits, misses;
        Scores[] find(ExpeditionVision.Bounds currentBounds, int[] currentPixels) {
            int hash = java.util.Arrays.hashCode(currentPixels);
            for (Entry entry : entries) {
                if (entry.bounds.equals(currentBounds) && entry.hash == hash
                        && java.util.Arrays.equals(entry.pixels, currentPixels)) {
                    hits++; return entry.scores.clone();
                }
            }
            misses++; return null;
        }
        void put(ExpeditionVision.Bounds bounds, int[] pixels, Scores[] scores) {
            if (pixels.length > 2_000_000) return;
            while (!entries.isEmpty() && (entries.size() >= 32 || retainedPixels + pixels.length > 2_000_000)) {
                retainedPixels -= entries.removeFirst().pixels.length;
            }
            entries.addLast(new Entry(bounds, java.util.Arrays.hashCode(pixels), pixels.clone(), scores.clone()));
            retainedPixels += pixels.length;
        }
        void clear() { entries.clear(); retainedPixels = 0; hits = 0; misses = 0; }
        String summary() { return "hits=" + hits + " misses=" + misses + " entries=" + entries.size(); }
    }

    /** Two consecutive fresh captures, never two OCR passes over one screenshot. */
    static final class Consensus {
        private long generation = -1, frame = -1;
        private String key = "";
        private Classification previous = Classification.UNKNOWN;
        private int agreeing, observations;
        int observations() { return observations; }
        Classification observe(long run, long capture, String identity, Classification ocr) {
            if (run != generation) { reset(); generation = run; }
            if (capture <= 0 || capture <= frame) return Classification.UNKNOWN;
            frame = capture;
            observations++;
            if (ocr == Classification.UNKNOWN || ocr == Classification.AMBIGUOUS) {
                key = ""; agreeing = 0; previous = ocr; return ocr;
            }
            boolean same = key.equals(identity);
            boolean conflict = same && previous != ocr && agreeing > 0;
            agreeing = same && previous == ocr ? agreeing + 1 : 1;
            key = identity; previous = ocr;
            return conflict ? Classification.AMBIGUOUS
                    : agreeing >= 2 ? ocr : Classification.UNKNOWN;
        }
        void reset() { generation = -1; frame = -1; key = "";
            previous = Classification.UNKNOWN; agreeing = 0; observations = 0; }
    }
    static Scores rank(String bank, java.util.List<Template> templates,
            ExpeditionVision.Bounds bounds, java.util.function.IntBinaryOperator pixelAt) {
        java.util.ArrayList<TemplateScore> scores = new java.util.ArrayList<>();
        for (Template template : templates) {
            double score = quickAlignedScore(template, bounds, pixelAt);
            if (Double.isFinite(score)) scores.add(new TemplateScore(template, score));
        }
        if ("SEEDLING".equals(bank)) {
            java.util.ArrayList<TemplateScore> leaders = new java.util.ArrayList<>(scores);
            leaders.sort(java.util.Comparator.comparingDouble(TemplateScore::score).reversed());
            for (int i = 0; i < Math.min(3, leaders.size()); i++) {
                Template leader = leaders.get(i).template();
                double refined = refinedAlignedScore(leader, bounds, pixelAt);
                for (int j = 0; j < scores.size(); j++) {
                    if (scores.get(j).template() == leader) {
                        scores.set(j, new TemplateScore(leader, refined)); break;
                    }
                }
            }
        }
        String best = "", second = "";
        double bestScore = Double.NEGATIVE_INFINITY, secondScore = Double.NEGATIVE_INFINITY;
        for (TemplateScore candidate : scores) {
            Template template = candidate.template();
            double score = candidate.score();
            if (!Double.isFinite(score)) continue;
            if (score > bestScore) {
                second = best; secondScore = bestScore;
                best = template.name; bestScore = score;
            } else if (score > secondScore) {
                second = template.name; secondScore = score;
            }
        }
        return new Scores(bank, best, Double.isFinite(bestScore) ? bestScore : Double.NaN,
                second, Double.isFinite(secondScore) ? secondScore : Double.NaN);
    }

    private record TemplateScore(Template template, double score) {}
    private record Alignment(double score, double scale, double x, double y) {}

    private static double quickAlignedScore(Template template, ExpeditionVision.Bounds bounds,
            java.util.function.IntBinaryOperator pixelAt) {
        if (template.energy <= 0 || template.points.length == 0) return Double.NaN;
        Alignment best = new Alignment(Double.NEGATIVE_INFINITY, 1, 0, 0);
        for (double scale : new double[] {.65, .80, 1.0})
            for (double x : new double[] {0, .5, 1}) for (double y : new double[] {0, .5, 1}) {
                double score = scoreAt(template, bounds, scale, x, y, pixelAt);
                if (score > best.score()) best = new Alignment(score, scale, x, y);
            }
        Alignment coarse = best;
        for (int ds = -2; ds <= 2; ds++) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            double scale = Math.max(.5, Math.min(1, coarse.scale() + ds * .05));
            double x = Math.max(0, Math.min(1, coarse.x() + dx * .25));
            double y = Math.max(0, Math.min(1, coarse.y() + dy * .25));
            double score = scoreAt(template, bounds, scale, x, y, pixelAt);
            if (score > best.score()) best = new Alignment(score, scale, x, y);
        }
        return Double.isFinite(best.score()) ? best.score() : Double.NaN;
    }

    private static double refinedAlignedScore(Template template, ExpeditionVision.Bounds bounds,
            java.util.function.IntBinaryOperator pixelAt) {
        if (template.energy <= 0 || template.points.length == 0) return Double.NaN;
        java.util.ArrayList<Alignment> fits = new java.util.ArrayList<>();
        for (double scale : new double[] {.65, .80, 1.0})
            for (double x : new double[] {0, .5, 1}) for (double y : new double[] {0, .5, 1}) {
                double score = scoreAt(template, bounds, scale, x, y, pixelAt);
                if (Double.isFinite(score)) fits.add(new Alignment(score, scale, x, y));
            }
        for (double step : new double[] {.05, .025, .0125}) {
            var seeds = fits.stream().sorted(java.util.Comparator
                    .comparingDouble(Alignment::score).reversed()).limit(3)
                    .collect(java.util.stream.Collectors.toList());
            int scaleRadius = step == .05 ? 2 : 1;
            for (Alignment seed : seeds)
                for (int ds = -scaleRadius; ds <= scaleRadius; ds++)
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                        double scale = Math.max(.5, Math.min(1, seed.scale + ds * step));
                        double x = Math.max(0, Math.min(1, seed.x + dx * step * 5));
                        double y = Math.max(0, Math.min(1, seed.y + dy * step * 5));
                        double score = scoreAt(template, bounds, scale, x, y, pixelAt);
                        if (Double.isFinite(score)) fits.add(new Alignment(score, scale, x, y));
                    }
        }
        return fits.stream().mapToDouble(Alignment::score).max().orElse(Double.NaN);
    }

    private static double scoreAt(Template template, ExpeditionVision.Bounds bounds,
            double scale, double xFraction, double yFraction,
            java.util.function.IntBinaryOperator pixelAt) {
        int h = Math.max(1, (int) Math.round(bounds.height() * scale));
        int w = Math.max(1, (int) Math.round(h * template.aspect));
        if (w > bounds.width()) { w = bounds.width(); h = Math.max(1, (int) Math.round(w / template.aspect)); }
        h = Math.min(h, bounds.height());
        int x = bounds.left() + (int) Math.round((bounds.width() - w) * xFraction);
        int y = bounds.top() + (int) Math.round((bounds.height() - h) * yFraction);
        ExpeditionVision.Bounds sample = new ExpeditionVision.Bounds(x, y, x + w, y + h);
        double sum = 0, square = 0, dot = 0;
        int firstColor = 0; boolean spatialVariation = false;
        for (int i = 0; i < template.points.length; i++) {
            int point = template.points[i], c = sample(sample, point % SIDE, point / SIDE, pixelAt);
            if (i == 0) firstColor = c; else if (c != firstColor) spatialVariation = true;
            for (int channel = 0; channel < 3; channel++) {
                int value = (c >>> (channel * 8)) & 255;
                sum += value; square += value * value;
                dot += value * template.centered[i * 3 + channel];
            }
        }
        double variance = square - sum * sum / template.centered.length;
        if (!spatialVariation || variance <= 0) return Double.NaN;
        return Math.max(-1, Math.min(1, dot / Math.sqrt(variance * template.energy)));
    }

    /** The same bilinear sampling runs on Android and the host calibration harness. */
    private static int sample(ExpeditionVision.Bounds b, int x, int y,
            java.util.function.IntBinaryOperator pixelAt) {
        double sx = Math.max(b.left(), Math.min(b.right() - 1, b.left() + (x + .5) * b.width() / SIDE - .5));
        double sy = Math.max(b.top(), Math.min(b.bottom() - 1, b.top() + (y + .5) * b.height() / SIDE - .5));
        int x0 = (int) sx, y0 = (int) sy;
        int x1 = Math.min(b.right() - 1, x0 + 1), y1 = Math.min(b.bottom() - 1, y0 + 1);
        int a = pixelAt.applyAsInt(x0, y0), c = pixelAt.applyAsInt(x1, y0);
        int d = pixelAt.applyAsInt(x0, y1), e = pixelAt.applyAsInt(x1, y1);
        double fx = sx - x0, fy = sy - y0;
        int result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            double upper = ((a >>> shift) & 255) * (1 - fx) + ((c >>> shift) & 255) * fx;
            double lower = ((d >>> shift) & 255) * (1 - fx) + ((e >>> shift) & 255) * fx;
            result |= (int) Math.round(upper * (1 - fy) + lower * fy) << shift;
        }
        return result;
    }

    private static int minimumChannel(int color) {
        return Math.min((color >>> 16) & 255, Math.min((color >>> 8) & 255, color & 255));
    }

    private static int opaque(int color) {
        int alpha = color >>> 24;
        if (alpha == 255) return color;
        int result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= ((((color >>> shift) & 255) * alpha + 255 * (255 - alpha)) / 255) << shift;
        return result;
    }
}
