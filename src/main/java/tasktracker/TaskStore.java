package tasktracker;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistencia de tareas en un archivo de texto (UTF-8, separado por tabuladores).
 *
 * <p>Formato:
 * <pre>
 * # task-tracker v1
 * &lt;id&gt;\t&lt;0|1&gt;\t&lt;descripción escapada&gt;
 * </pre>
 *
 * <p>La descripción se escapa para que pueda contener tabuladores, saltos de línea
 * y barras invertidas sin romper el formato.
 */
public final class TaskStore {

    private static final String HEADER = "# task-tracker v1";

    private final Path file;
    private final List<Task> tasks = new ArrayList<>();
    private boolean loaded;

    public TaskStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public Path file() {
        return file;
    }

    /**
     * Carga las tareas desde disco. Debe llamarse antes de cualquier operación.
     *
     * @throws StorageException si el archivo no se puede leer
     * @throws CorruptFileException si el contenido no cumple el formato
     */
    public void load() throws StorageException {
        tasks.clear();
        loaded = true;

        if (!Files.exists(file)) {
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new CorruptFileException("el archivo no es UTF-8 válido");
        } catch (IOException e) {
            throw new StorageException("no se pudo leer el archivo: " + e.getMessage(), e);
        }

        if (lines.isEmpty()) {
            return;
        }

        // Algunos editores guardan UTF-8 con BOM; no forma parte del formato.
        String firstLine = lines.get(0);
        if (firstLine.startsWith("\uFEFF")) {
            firstLine = firstLine.substring(1);
        }
        if (!HEADER.equals(firstLine)) {
            throw new CorruptFileException(1, "falta la cabecera '" + HEADER + "'");
        }

        Set<Integer> seenIds = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNumber = i + 1;

            // Se toleran líneas vacías (por ejemplo, un salto de línea final).
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\t", -1);
            if (parts.length != 3) {
                throw new CorruptFileException(lineNumber,
                        "se esperaban 3 campos separados por tabuladores y hay " + parts.length);
            }

            if (!parts[0].matches("[+-]?[0-9]+")) {
                throw new CorruptFileException(lineNumber, "ID no numérico: '" + parts[0] + "'");
            }
            int id;
            try {
                id = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                throw new CorruptFileException(lineNumber, "ID fuera de rango: '" + parts[0] + "'");
            }
            if (id <= 0) {
                throw new CorruptFileException(lineNumber, "el ID debe ser un entero positivo: " + id);
            }
            if (!seenIds.add(id)) {
                throw new CorruptFileException(lineNumber, "ID duplicado: " + id);
            }

            String doneFlag = parts[1];
            if (!doneFlag.equals("0") && !doneFlag.equals("1")) {
                throw new CorruptFileException(lineNumber,
                        "el estado debe ser '0' o '1' y es '" + doneFlag + "'");
            }

            String description;
            try {
                description = unescape(parts[2]);
            } catch (IllegalArgumentException e) {
                throw new CorruptFileException(lineNumber, "descripción con escape inválido: " + e.getMessage());
            }
            if (description.isBlank()) {
                throw new CorruptFileException(lineNumber, "la descripción está vacía");
            }

            tasks.add(new Task(id, description, doneFlag.equals("1")));
        }
    }

    /**
     * @return copia inmutable de las tareas cargadas, en orden de creación
     */
    public List<Task> tasks() {
        requireLoaded();
        return List.copyOf(tasks);
    }

    public Optional<Task> find(int id) {
        requireLoaded();
        return tasks.stream().filter(task -> task.id() == id).findFirst();
    }

    /**
     * Crea una tarea con el siguiente ID libre. No escribe en disco hasta llamar a {@link #save()}.
     */
    public Task add(String description) {
        requireLoaded();
        int nextId = tasks.stream().mapToInt(Task::id).max().orElse(0) + 1;
        Task task = new Task(nextId, description, false);
        tasks.add(task);
        return task;
    }

    /**
     * Cambia el estado de una tarea existente. No escribe en disco hasta llamar a {@link #save()}.
     */
    public void setDone(int id, boolean done) {
        requireLoaded();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task.id() == id) {
                tasks.set(i, new Task(task.id(), task.description(), done));
                return;
            }
        }
    }

    /**
     * Elimina una tarea existente. No escribe en disco hasta llamar a {@link #save()}.
     *
     * @return {@code true} si se eliminó algo
     */
    public boolean remove(int id) {
        requireLoaded();
        return tasks.removeIf(task -> task.id() == id);
    }

    /**
     * Escribe el estado actual en disco de forma atómica (archivo temporal + rename).
     */
    public void save() throws StorageException {
        requireLoaded();
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (Task task : tasks) {
            content.append(task.id())
                    .append('\t')
                    .append(task.done() ? '1' : '0')
                    .append('\t')
                    .append(escape(task.description()))
                    .append('\n');
        }

        Path parent = file.getParent();
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // El error original es más relevante.
            }
            throw new StorageException("no se pudo escribir el archivo: " + e.getMessage(), e);
        }
    }

    private void requireLoaded() {
        if (!loaded) {
            throw new IllegalStateException("load() debe llamarse antes de usar TaskStore");
        }
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                result.append(c);
                continue;
            }
            i++;
            if (i >= value.length()) {
                throw new IllegalArgumentException("secuencia de escape incompleta");
            }
            switch (value.charAt(i)) {
                case '\\' -> result.append('\\');
                case 't' -> result.append('\t');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                default -> throw new IllegalArgumentException("escape desconocido '\\" + value.charAt(i) + "'");
            }
        }
        return result.toString();
    }
}
