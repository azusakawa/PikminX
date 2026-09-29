package com.pikminx.helper;

/** Centralizes the slower OCR cadence used while the game returns from a postcard receipt. */
final class PostcardTiming {
    private static final float RECEIPT_RETURN_FACTOR = 1.5f;

    private PostcardTiming() {}

    static long receiptReturnDelayMillis(long normalDelayMillis) {
        return Math.max(1L, Math.round(normalDelayMillis * RECEIPT_RETURN_FACTOR));
    }
}
