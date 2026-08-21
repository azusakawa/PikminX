package com.pikminx.helper;

import com.android.tools.r8.RecordTag;
import java.util.ArrayDeque;
import java.util.function.IntBinaryOperator;

/* JADX INFO: loaded from: classes.dex */
final class ReturnRewardDetector {

    static final class Target extends RecordTag {
        private final float confidence;
        private final int height;
        private final int width;
        private final int x;
        private final int y;

        private /* synthetic */ boolean $record$equals(Object obj) {
            if (!(obj instanceof Target)) {
                return false;
            }
            Target target = (Target) obj;
            return this.x == target.x && this.y == target.y && this.width == target.width && this.height == target.height && this.confidence == target.confidence;
        }

        private /* synthetic */ Object[] $record$getFieldsAsObjects() {
            return new Object[]{Integer.valueOf(this.x), Integer.valueOf(this.y), Integer.valueOf(this.width), Integer.valueOf(this.height), Float.valueOf(this.confidence)};
        }

        Target(int x, int y, int width, int height, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        public float confidence() {
            return this.confidence;
        }

        public final boolean equals(Object obj) {
            return $record$equals(obj);
        }

        public final int hashCode() {
            return MainActivity$$ExternalSyntheticBackport0.m(this.x, this.y, this.width, this.height, this.confidence);
        }

        public int height() {
            return this.height;
        }

        public final String toString() {
            return MainActivity$$ExternalSyntheticBackport0.m($record$getFieldsAsObjects(), Target.class, "x;y;width;height;confidence");
        }

        public int width() {
            return this.width;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        boolean samePosition(Target target, int i, int i2) {
            return target != null && ((float) Math.abs(this.x - target.x)) <= ((float) i) * 0.05f && ((float) Math.abs(this.y - target.y)) <= ((float) i2) * 0.04f;
        }
    }

    private ReturnRewardDetector() {
    }

    /* JADX WARN: Code duplicated, block: B:66:0x023d  */
    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r13v15 */
    /* JADX WARN: Type inference failed for: r13v16 */
    /* JADX WARN: Type inference failed for: r13v18 */
    /* JADX WARN: Type inference failed for: r13v19 */
    /* JADX WARN: Type inference failed for: r13v21 */
    /* JADX WARN: Type inference failed for: r13v24 */
    static Target find(int i, int i2, IntBinaryOperator intBinaryOperator) {
        int i3;
        ArrayDeque arrayDeque;
        Target target;
        int i4;
        int i5;
        float f;
        int i6;
        boolean[] zArr;
        boolean[] zArr2;
        int i7;
        boolean z;
        ?? r13;
        boolean[] zArr3;
        boolean[] zArr4;
        int i8 = i;
        int i9 = i2;
        if (i8 <= 0 || i9 <= 0) {
            return null;
        }
        float f2 = i8;
        int iMax = Math.max(2, Math.round(f2 / 216.0f));
        int iRound = Math.round(0.18f * f2);
        int iRound2 = Math.round(0.82f * f2);
        float f3 = i9;
        int iRound3 = Math.round(0.4f * f3);
        int iRound4 = Math.round(0.7f * f3);
        boolean z2 = true;
        int iMax2 = Math.max(1, (((iRound2 - iRound) + iMax) - 1) / iMax);
        int iMax3 = Math.max(1, (((iRound4 - iRound3) + iMax) - 1) / iMax);
        int i10 = iMax2 * iMax3;
        boolean[] zArr5 = new boolean[i10];
        boolean[] zArr6 = new boolean[i10];
        int i11 = 0;
        while (i11 < iMax3) {
            boolean z3 = z2;
            int iMin = Math.min(i9 - 1, iRound3 + (i11 * iMax));
            int i12 = 0;
            while (i12 < iMax2) {
                int i13 = iRound;
                zArr5[(i11 * iMax2) + i12] = isRewardPixel(intBinaryOperator.applyAsInt(Math.min(i8 - 1, i13 + (i12 * iMax)), iMin));
                i12++;
                f2 = f2;
                iRound = i13;
            }
            i11++;
            z2 = z3;
        }
        float f4 = f2;
        int i14 = iRound;
        boolean z4 = z2;
        ArrayDeque arrayDeque2 = new ArrayDeque();
        Target target2 = null;
        int i15 = 0;
        while (i15 < i10) {
            if (!zArr5[i15] || zArr6[i15]) {
                i3 = i9;
                arrayDeque = arrayDeque2;
                target = target2;
                i4 = i15;
                i5 = i10;
                f = f3;
                i6 = iRound3;
                zArr = zArr5;
                zArr2 = zArr6;
                i7 = i8;
                z = z4 ? 1 : 0;
            } else {
                zArr6[i15] = z4;
                arrayDeque2.add(Integer.valueOf(i15));
                arrayDeque = arrayDeque2;
                target = target2;
                i4 = i15;
                i5 = i10;
                f = f3;
                i6 = iRound3;
                int i16 = iMax2;
                long j = 0;
                long j2 = 0;
                int i17 = 0;
                int i18 = 0;
                int i19 = 0;
                boolean[] zArr7 = zArr5;
                int iMin2 = iMax3;
                while (!arrayDeque.isEmpty()) {
                    int iIntValue = ((Integer) arrayDeque.removeFirst()).intValue();
                    int i20 = iIntValue % iMax2;
                    int i21 = iIntValue / iMax2;
                    i18++;
                    int iMin3 = Math.min(i16, i20);
                    int iMax4 = Math.max(i19, i20);
                    iMin2 = Math.min(iMin2, i21);
                    int iMax5 = Math.max(i17, i21);
                    j += (long) i20;
                    j2 += (long) i21;
                    ?? r14 = z4;
                    int i22 = -1;
                    while (i22 <= r14) {
                        int i23 = -1;
                        while (i23 <= r13) {
                            if (i23 == 0 && i22 == 0) {
                                zArr3 = zArr7;
                                zArr4 = zArr6;
                            } else {
                                zArr3 = zArr7;
                                zArr4 = zArr6;
                                enqueue(i20 + i23, i21 + i22, iMax2, iMax3, zArr3, zArr4, arrayDeque);
                            }
                            i23++;
                            zArr6 = zArr4;
                            r13 = 1;
                            zArr7 = zArr3;
                        }
                        r13 = r14;
                        i22++;
                        r14 = 1;
                        zArr7 = zArr7;
                    }
                    z4 = r14 == true ? 1 : 0;
                    i19 = iMax4;
                    i16 = iMin3;
                    i17 = iMax5;
                }
                zArr = zArr7;
                zArr2 = zArr6;
                z = true;
                z = true;
                z = true;
                z = true;
                int i24 = (i19 - i16) + 1;
                int i25 = i24 * iMax;
                int i26 = (i17 - iMin2) + 1;
                int i27 = i26 * iMax;
                float f5 = i18;
                float fMax = f5 / Math.max(1, i24 * i26);
                float fMax2 = ((i18 * iMax) * iMax) / Math.max(1, i * i2);
                float f6 = iMax;
                int iRound5 = i14 + Math.round((j / f5) * f6);
                float f7 = i27;
                int iMin4 = Math.min(i6 + Math.round((j2 / f5) * f6), i6 + (iMin2 * iMax) + Math.round(0.45f * f7));
                float f8 = i25;
                if (f8 < 0.08f * f4 || f8 > f4 * 0.56f || f7 < 0.045f * f || f7 > 0.26f * f || ((f8 > 0.38f * f4 && f7 < 0.14f * f) || fMax < 0.12f || fMax2 < 0.003f)) {
                    i7 = i;
                    i3 = i2;
                } else {
                    float f9 = iRound5;
                    if (f9 < 0.27f * f4 || f9 > 0.73f * f4) {
                        i7 = i;
                        i3 = i2;
                    } else {
                        float f10 = iMin4;
                        if (f10 < 0.43f * f || f10 > 0.67f * f) {
                            i7 = i;
                            i3 = i2;
                        } else {
                            i7 = i;
                            i3 = i2;
                            if (!looksLikeGift(i7, i3, iRound5, iMin4, intBinaryOperator)) {
                                Target target3 = new Target(iRound5, iMin4, i25, i27, ((fMax2 * 8.0f) + (fMax * 0.35f)) - (((Math.abs(f9 - (0.5f * f4)) / f4) + (Math.abs(f10 - (0.56f * f)) / f)) * 0.2f));
                                if (target == null || target3.confidence() > target.confidence()) {
                                    target2 = target3;
                                }
                            }
                        }
                    }
                }
                i15 = i4 + 1;
                z4 = z;
                i8 = i7;
                i9 = i3;
                zArr5 = zArr;
                zArr6 = zArr2;
                arrayDeque2 = arrayDeque;
                i10 = i5;
                f3 = f;
                iRound3 = i6;
            }
            target2 = target;
            i15 = i4 + 1;
            z4 = z;
            i8 = i7;
            i9 = i3;
            zArr5 = zArr;
            zArr6 = zArr2;
            arrayDeque2 = arrayDeque;
            i10 = i5;
            f3 = f;
            iRound3 = i6;
        }
        return target2;
    }

    private static void enqueue(int i, int i2, int i3, int i4, boolean[] zArr, boolean[] zArr2, ArrayDeque<Integer> arrayDeque) {
        if (i < 0 || i >= i3 || i2 < 0 || i2 >= i4) {
            return;
        }
        int i5 = (i2 * i3) + i;
        if (!zArr[i5] || zArr2[i5]) {
            return;
        }
        zArr2[i5] = true;
        arrayDeque.add(Integer.valueOf(i5));
    }

    private static boolean isRewardPixel(int i) {
        int i2;
        int i3 = (i >>> 16) & 255;
        int i4 = (i >>> 8) & 255;
        int i5 = i & 255;
        boolean z = i4 >= 62 && i4 >= i3 + 16 && i4 >= i5 + 10;
        boolean z2 = i4 >= 90 && (i2 = i3 * 100) >= i4 * 55 && i2 <= i4 * 92 && i5 * 100 <= i4 * 58;
        int iMax = Math.max(i3, Math.max(i4, i5));
        int iMin = Math.min(i3, Math.min(i4, i5));
        return z2 || (!z && iMax >= 48 && (iMax - iMin >= 18 || iMin >= 105));
    }

    private static boolean looksLikeGift(int i, int i2, int i3, int i4, IntBinaryOperator intBinaryOperator) {
        float[] fArr = {-0.075f, -0.05f, -0.025f, 0.0f, 0.025f, 0.05f, 0.075f};
        for (int i5 = 0; i5 < 7; i5++) {
            int iRound = i4 + Math.round(i2 * fArr[i5]);
            float fRatio = ratio(i, i2, i3, iRound, -0.075f, -0.1f, 0.075f, -0.02f, intBinaryOperator, true);
            float fRatio2 = ratio(i, i2, i3, iRound, -0.085f, -0.1f, 0.085f, 0.1f, intBinaryOperator, true);
            float fRatio3 = ratio(i, i2, i3, iRound, -0.08f, -0.01f, 0.08f, 0.085f, intBinaryOperator, false);
            if (fRatio >= 0.11f && fRatio <= 0.42f && fRatio2 >= 0.085f && fRatio2 <= 0.27f && fRatio3 >= 0.22f) {
                return true;
            }
        }
        return false;
    }

    /* JADX WARN: Code duplicated, block: B:23:0x009a  */
    private static float ratio(int i, int i2, int i3, int i4, float f, float f2, float f3, float f4, IntBinaryOperator intBinaryOperator, boolean z) {
        int iMax = Math.max(1, i / 540);
        float f5 = i3;
        float f6 = i;
        int i5 = i - 1;
        int i6 = 0;
        int iClamp = clamp(Math.round((f * f6) + f5), 0, i5);
        int iClamp2 = clamp(Math.round(f5 + (f3 * f6)), 0, i5);
        float f7 = i4;
        int i7 = i2 - 1;
        int iClamp3 = clamp(Math.round(f7 + (f6 * f4)), 0, i7);
        int i8 = 0;
        for (int iClamp4 = clamp(Math.round((f2 * f6) + f7), 0, i7); iClamp4 <= iClamp3; iClamp4 += iMax) {
            for (int i9 = iClamp; i9 <= iClamp2; i9 += iMax) {
                int iApplyAsInt = intBinaryOperator.applyAsInt(i9, iClamp4);
                int i10 = (iApplyAsInt >>> 16) & 255;
                int i11 = (iApplyAsInt >>> 8) & 255;
                int i12 = iApplyAsInt & 255;
                if (z) {
                    if (i10 >= 145 && i10 - Math.max(i11, i12) >= 30) {
                        float f8 = i10;
                        if (f8 >= i11 * 1.18f && f8 >= i12 * 1.12f) {
                            i8++;
                        }
                    }
                } else if (Math.min(i10, Math.min(i11, i12)) >= 120 && Math.max(i10, Math.max(i11, i12)) <= 246 && Math.max(i10, Math.max(i11, i12)) - Math.min(i10, Math.min(i11, i12)) <= 45) {
                    i8++;
                }
                i6++;
            }
        }
        if (i6 == 0) {
            return 0.0f;
        }
        return i8 / i6;
    }

    private static int clamp(int i, int i2, int i3) {
        return Math.max(i2, Math.min(i3, i));
    }
}
