package ru.grief.chatcolor;

/** Выбор игрока. paint: "c:<id>" (цвет), "g:<id>" (градиент) или null. */
final class PlayerStyle {
    volatile String paint;
    volatile TextFx.Font font = TextFx.Font.NORMAL;
    volatile boolean bold, italic, underline, strike;

    boolean isDefault() {
        return paint == null && font == TextFx.Font.NORMAL && !bold && !italic && !underline && !strike;
    }

    void reset() {
        paint = null;
        font = TextFx.Font.NORMAL;
        bold = italic = underline = strike = false;
    }

    /** 0 жирный, 1 курсив, 2 подчёркнутый, 3 зачёркнутый */
    boolean get(int i) {
        return switch (i) {
            case 0 -> bold;
            case 1 -> italic;
            case 2 -> underline;
            default -> strike;
        };
    }

    void toggle(int i) {
        switch (i) {
            case 0 -> bold = !bold;
            case 1 -> italic = !italic;
            case 2 -> underline = !underline;
            default -> strike = !strike;
        }
    }
}
