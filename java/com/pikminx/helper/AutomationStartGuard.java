package com.pikminx.helper;

/** Generation guard for delayed automation start callbacks. */
final class AutomationStartGuard {
    private AutomationStartGuard() {}

    static boolean isCurrent(long scheduledGeneration, long currentGeneration) {
        return scheduledGeneration == currentGeneration;
    }
}
