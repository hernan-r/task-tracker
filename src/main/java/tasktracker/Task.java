package tasktracker;

/**
 * Una tarea del tracker.
 *
 * @param id          identificador positivo, único y estable
 * @param description texto libre de la tarea
 * @param done        {@code true} si la tarea está completada
 */
public record Task(int id, String description, boolean done) {
}
