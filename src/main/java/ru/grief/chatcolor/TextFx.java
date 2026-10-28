package ru.grief.chatcolor;

/** Цвета, градиенты и "шрифты" (преобразование Unicode). */
final class TextFx {

    private TextFx() {}

    static String hex(int rgb) {
        String h = String.format("%06x", rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder("§x");
        for (int i = 0; i < 6; i++) sb.append('§').append(h.charAt(i));
        return sb.toString();
    }

    static int lerp(int[] stops, double t) {
        if (stops.length == 1) return stops[0];
        double pos = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int i = Math.min((int) pos, stops.length - 2);
        double f = pos - i;
        int a = stops[i], b = stops[i + 1];
        int r = (int) Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    static String solid(String text, int rgb, String styles) {
        return hex(rgb) + styles + text;
    }

    static String gradient(String text, int[] stops, String styles) {
        int visible = 0;
        for (int cp : text.codePoints().toArray()) if (!Character.isWhitespace(cp)) visible++;
        if (visible == 0) return styles + text;
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (int cp : text.codePoints().toArray()) {
            if (Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
                continue;
            }
            double t = visible == 1 ? 0 : (double) idx / (visible - 1);
            idx++;
            sb.append(hex(lerp(stops, t))).append(styles).appendCodePoint(cp);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ fonts

    enum Font {
        NORMAL("normal", "Обычный"),
        SMALLCAPS("smallcaps", "Малые капсы"),
        FULLWIDTH("fullwidth", "Полная ширина"),
        CIRCLED("circled", "Кружочки"),
        UPSIDE("upside", "Перевёрнутый"),
        SPACED("spaced", "Разрядка"),
        WAVE("wave", "Волна"),
        UPPER("upper", "ЗАГЛАВНЫЕ");

        final String id;
        final String title;

        Font(String id, String title) {
            this.id = id;
            this.title = title;
        }

        static Font byId(String id) {
            for (Font f : values()) if (f.id.equals(id)) return f;
            return NORMAL;
        }

        String apply(String text) {
            switch (this) {
                case SMALLCAPS: return map(text, c -> {
                    String sc = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
                    if (c >= 'a' && c <= 'z') return sc.charAt(c - 'a');
                    if (c >= 'A' && c <= 'Z') return sc.charAt(c - 'A');
                    return c;
                });
                case FULLWIDTH: return map(text, c ->
                        (c >= '!' && c <= '~') ? (char) (c - 0x21 + 0xFF01) : c);
                case CIRCLED: return map(text, c -> {
                    if (c >= 'A' && c <= 'Z') return (char) (0x24B6 + (c - 'A'));
                    if (c >= 'a' && c <= 'z') return (char) (0x24D0 + (c - 'a'));
                    if (c >= '1' && c <= '9') return (char) (0x2460 + (c - '1'));
                    if (c == '0') return (char) 0x24EA;
                    return c;
                });
                case UPSIDE: {
                    String from = "abcdefghijklmnopqrstuvwxyz";
                    String to = "ɐqɔpǝɟƃɥᴉɾʞlɯuodbɹsʇnʌʍxʎz";
                    String m = map(text.toLowerCase(), c -> {
                        int i = from.indexOf(c);
                        if (i >= 0) return to.charAt(i);
                        if (c == '?') return '¿';
                        if (c == '!') return '¡';
                        if (c == '.') return '˙';
                        return c;
                    });
                    return new StringBuilder(m).reverse().toString();
                }
                case SPACED: {
                    StringBuilder sb = new StringBuilder();
                    int[] cps = text.codePoints().toArray();
                    for (int i = 0; i < cps.length; i++) {
                        sb.appendCodePoint(cps[i]);
                        if (cps[i] != ' ' && i + 1 < cps.length && cps[i + 1] != ' ') sb.append(' ');
                    }
                    return sb.toString();
                }
                case WAVE: {
                    StringBuilder sb = new StringBuilder();
                    boolean up = false;
                    for (int cp : text.codePoints().toArray()) {
                        if (Character.isLetter(cp)) {
                            sb.appendCodePoint(up ? Character.toUpperCase(cp) : Character.toLowerCase(cp));
                            up = !up;
                        } else {
                            sb.appendCodePoint(cp);
                        }
                    }
                    return sb.toString();
                }
                case UPPER: return text.toUpperCase();
                default: return text;
            }
        }

        private static String map(String text, java.util.function.IntUnaryOperator op) {
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) sb.append((char) op.applyAsInt(c));
            return sb.toString();
        }
    }
}
