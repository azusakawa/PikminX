package com.pikminx.helper;

import java.util.Objects;

/** 描述懸浮狀態膠囊的語意；Android View 只負責渲染，不自行猜測流程狀態。 */
final class OverlayRunStatus {
    enum Kind {
        IDLE,
        SEARCHING,
        RECOGNIZING,
        SUCCESS,
        ERROR
    }

    private final Kind kind;
    private final String stage;
    private final String message;
    private final String detail;

    OverlayRunStatus(Kind kind, String stage, String message, String detail) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.stage = requireText(stage, "stage");
        this.message = requireText(message, "message");
        this.detail = detail == null ? "" : detail.trim();
    }

    Kind kind() {
        return kind;
    }

    String stage() {
        return stage;
    }

    String message() {
        return message;
    }

    String detail() {
        return detail;
    }

    boolean remainsUntilReplaced() {
        return kind == Kind.ERROR;
    }

    String accessibilityText() {
        return detail.isEmpty()
                ? stage + "，" + message
                : stage + "，" + message + "，" + detail;
    }

    String visibleText() {
        return detail.isEmpty()
                ? stage + "\n" + message
                : stage + "\n" + message + "\n" + detail;
    }

    private static String requireText(String value, String name) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
