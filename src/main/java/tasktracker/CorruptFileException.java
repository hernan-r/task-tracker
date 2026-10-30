package tasktracker;

/**
 * El archivo de tareas existe pero su contenido no es válido.
 */
public final class CorruptFileException extends StorageException {

    private static final long serialVersionUID = 1L;

    private final int line;
    private final String reason;

    public CorruptFileException(int line, String reason) {
        super("línea " + line + ": " + reason);
        this.line = line;
        this.reason = reason;
    }

    /**
     * Corrupción que afecta al archivo completo y no a una línea concreta.
     */
    public CorruptFileException(String reason) {
        super(reason);
        this.line = 0;
        this.reason = reason;
    }

    public int line() {
        return line;
    }

    public String reason() {
        return reason;
    }
}
