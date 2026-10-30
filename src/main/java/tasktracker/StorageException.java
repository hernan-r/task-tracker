package tasktracker;

/**
 * Error al leer o escribir el archivo de tareas.
 */
public class StorageException extends Exception {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
