package tasktracker;

import java.util.Locale;

/**
 * Colores ANSI para la terminal. Se activan solo si la salida es una terminal,
 * salvo que se fuerce con {@code TASK_TRACKER_COLOR=always|never}.
 * {@code NO_COLOR} (cualquier valor) siempre desactiva los colores.
 */
final class Ansi {

    private static final String ESC = "\u001B[";
    private static final boolean ENABLED = detectEnabled();

    private Ansi() {
    }

    static String bold(String text) {
        return style("1", text);
    }

    static String dim(String text) {
        return style("2", text);
    }

    static String red(String text) {
        return style("31", text);
    }

    static String green(String text) {
        return style("32", text);
    }

    static String yellow(String text) {
        return style("33", text);
    }

    static String cyan(String text) {
        return style("36", text);
    }

    private static String style(String code, String text) {
        if (!ENABLED || text.isEmpty()) {
            return text;
        }
        return ESC + code + "m" + text + ESC + "0m";
    }

    private static boolean detectEnabled() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }

        String mode = System.getenv("TASK_TRACKER_COLOR");
        if (mode != null) {
            switch (mode.toLowerCase(Locale.ROOT)) {
                case "always", "1", "true", "yes" -> {
                    return true;
                }
                case "never", "0", "false", "no" -> {
                    return false;
                }
                default -> {
                    // Valor no reconocido: se usa la detección automática.
                }
            }
        }

        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;
        }
        return System.console() != null;
    }
}
