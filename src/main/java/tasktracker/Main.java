package tasktracker;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Punto de entrada del Task Tracker CLI.
 *
 * <p>Códigos de salida:
 * <ul>
 *   <li>0: éxito (incluido {@code help} sin argumentos)</li>
 *   <li>1: error de uso (comando desconocido, argumentos faltantes, ID inválido o inexistente)</li>
 *   <li>2: error de almacenamiento (archivo corrupto o problemas de lectura/escritura)</li>
 * </ul>
 */
public final class Main {

    static final String PROGRAM = "task-tracker";
    static final String VERSION = "1.0.0";
    private static final String FILE_ENV = "TASK_TRACKER_FILE";
    private static final int BAR_WIDTH = 10;

    private final TaskStore store;

    public Main(Path file) {
        this.store = new TaskStore(file);
    }

    public static void main(String[] args) {
        int exitCode = new Main(resolveFile()).run(args);
        System.exit(exitCode);
    }

    static Path resolveFile() {
        String override = System.getenv(FILE_ENV);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".task-tracker", "tasks.tsv");
    }

    public int run(String[] args) {
        if (args.length == 0) {
            printUsage(System.out);
            return 0;
        }

        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        try {
            return switch (command) {
                case "add" -> add(rest);
                case "list" -> list(rest);
                case "done" -> done(rest);
                case "delete" -> delete(rest);
                case "version", "--version" -> {
                    System.out.println(PROGRAM + " " + VERSION);
                    yield 0;
                }
                case "help", "-h", "--help" -> {
                    printUsage(System.out);
                    yield 0;
                }
                default -> throw new UsageException("comando desconocido: '" + command + "'");
            };
        } catch (UsageException e) {
            System.err.println(Ansi.red("✗ Error:") + " " + e.getMessage());
            System.err.println(Ansi.dim("  Ejecuta '" + PROGRAM + " help' para ver el uso."));
            return 1;
        } catch (StorageException e) {
            return reportStorageError(e);
        }
    }

    private int add(String[] args) throws UsageException, StorageException {
        if (args.length == 0) {
            throw new UsageException("falta la descripción de la tarea");
        }
        String description = String.join(" ", args).trim();
        if (description.isEmpty()) {
            throw new UsageException("la descripción de la tarea no puede estar vacía");
        }

        store.load();
        Task task = store.add(description);
        store.save();
        System.out.println(Ansi.green("✓") + " Tarea " + Ansi.bold(String.valueOf(task.id()))
                + " añadida: " + display(task.description()));
        return 0;
    }

    private int list(String[] args) throws UsageException, StorageException {
        Filter filter = Filter.parse(args);
        store.load();
        List<Task> all = store.tasks();

        if (all.isEmpty()) {
            System.out.println(Ansi.bold("No hay tareas todavía."));
            System.out.println();
            System.out.println("Añade la primera con:");
            System.out.println("  " + Ansi.cyan(PROGRAM + " add \"Comprar leche\""));
            return 0;
        }

        List<Task> visible = all.stream().filter(filter::matches).toList();

        String title = switch (filter) {
            case ALL -> "TAREAS";
            case PENDING -> "TAREAS PENDIENTES";
            case DONE -> "TAREAS COMPLETADAS";
        };
        System.out.println(Ansi.bold(title) + Ansi.dim(" (" + visible.size() + ")"));
        System.out.println();

        if (visible.isEmpty()) {
            if (filter == Filter.PENDING) {
                System.out.println(Ansi.green("✓") + " No hay tareas pendientes.");
            } else {
                System.out.println("Todavía no hay tareas completadas.");
            }
        } else {
            printTable(visible);
        }

        long completed = all.stream().filter(Task::done).count();
        System.out.println();
        System.out.println(progress(all.size(), completed));
        return 0;
    }

    private static void printTable(List<Task> tasks) {
        int idWidth = Math.max("ID".length(), tasks.stream()
                .mapToInt(task -> String.valueOf(task.id()).length())
                .max()
                .orElse(1));

        System.out.println(Ansi.dim(String.format("%" + idWidth + "s  %-6s  %s",
                "ID", "ESTADO", "DESCRIPCIÓN")));

        for (Task task : tasks) {
            String id = String.format("%" + idWidth + "s", task.id());
            String status = String.format("%-6s", task.done() ? "[x]" : "[ ]");
            String description = display(task.description());

            if (task.done()) {
                System.out.println(Ansi.dim(id) + "  " + Ansi.green(status) + "  "
                        + Ansi.dim(description));
            } else {
                System.out.println(Ansi.cyan(id) + "  " + Ansi.yellow(status) + "  "
                        + description);
            }
        }
    }

    private static String progress(int total, long completed) {
        long pending = total - completed;
        int percent = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
        int filled = total == 0 ? 0 : (int) Math.round(completed * (double) BAR_WIDTH / total);

        String bar = Ansi.green("█".repeat(filled)) + Ansi.dim("░".repeat(BAR_WIDTH - filled));
        String pendingLabel = pending == 1 ? " pendiente" : " pendientes";
        return Ansi.dim("Progreso ") + "[" + bar + "] "
                + completed + "/" + total + " (" + percent + "%)"
                + Ansi.dim(" · " + pending + pendingLabel);
    }

    private int done(String[] args) throws UsageException, StorageException {
        List<Integer> ids = parseIds(args);
        store.load();

        List<Integer> unknown = ids.stream()
                .filter(id -> store.find(id).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw new UsageException(unknownMessage(unknown));
        }

        for (int id : ids) {
            Optional<Task> task = store.find(id);
            if (task.isPresent() && task.get().done()) {
                System.out.println(Ansi.dim("• La tarea " + id + " ya estaba completada."));
            } else {
                store.setDone(id, true);
                System.out.println(Ansi.green("✓") + " Tarea " + Ansi.bold(String.valueOf(id))
                        + " completada: " + display(task.get().description()));
            }
        }
        store.save();
        return 0;
    }

    private int delete(String[] args) throws UsageException, StorageException {
        List<Integer> ids = parseIds(args);
        store.load();

        List<Integer> unknown = ids.stream()
                .filter(id -> store.find(id).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw new UsageException(unknownMessage(unknown));
        }

        for (int id : ids) {
            Task task = store.find(id).orElseThrow();
            store.remove(id);
            System.out.println(Ansi.green("✓") + " Tarea " + Ansi.bold(String.valueOf(task.id()))
                    + " eliminada: " + display(task.description()));
        }
        store.save();
        return 0;
    }

    private static List<Integer> parseIds(String[] args) throws UsageException {
        if (args.length == 0) {
            throw new UsageException("faltan los IDs de las tareas");
        }

        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (String arg : args) {
            int id;
            try {
                id = Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                throw new UsageException("ID inválido: '" + arg + "' (debe ser un número entero positivo)");
            }
            if (id <= 0) {
                throw new UsageException("ID inválido: '" + arg + "' (debe ser un número entero positivo)");
            }
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    private static String unknownMessage(List<Integer> ids) {
        String joined = ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return ids.size() == 1
                ? "no existe ninguna tarea con ID " + joined
                : "no existen tareas con los IDs " + joined;
    }

    /**
     * Evita que tabuladores, saltos de línea o secuencias ANSI rompan la salida por
     * terminal. En el archivo se guardan escapados; aquí se hacen visibles como texto.
     */
    static String display(String description) {
        StringBuilder visible = new StringBuilder(description.length());
        for (int i = 0; i < description.length(); i++) {
            char c = description.charAt(i);
            switch (c) {
                case '\r' -> visible.append("\\r");
                case '\n' -> visible.append("\\n");
                case '\t' -> visible.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        visible.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        visible.append(c);
                    }
                }
            }
        }
        return visible.toString();
    }

    private int reportStorageError(StorageException e) {
        if (e instanceof CorruptFileException corrupt) {
            String location = corrupt.line() > 0 ? " en la línea " + corrupt.line() : "";
            System.err.println(Ansi.red("✗ Error:") + " el archivo de tareas está corrupto"
                    + location + ": " + corrupt.reason());
            System.err.println(Ansi.dim("  Archivo: " + store.file()));
            System.err.println(Ansi.dim("  Sugerencia: corrige el archivo, o haz una copia de seguridad"));
            System.err.println(Ansi.dim("              y elimínalo para empezar de cero."));
        } else {
            System.err.println(Ansi.red("✗ Error:") + " " + e.getMessage());
            System.err.println(Ansi.dim("  Archivo: " + store.file()));
        }
        return 2;
    }

    private void printUsage(PrintStream out) {
        out.println(Ansi.bold(PROGRAM) + " " + Ansi.dim("v" + VERSION));
        out.println();
        out.println(Ansi.bold("Uso:"));
        out.println("  " + PROGRAM + " <comando> [argumentos]");
        out.println();
        out.println(Ansi.bold("Comandos:"));
        usageLine(out, "add <descripción...>", "Añade una tarea nueva");
        usageLine(out, "list [--pending|--done]", "Lista tareas (sin filtro: todas)");
        usageLine(out, "done <id...>", "Marca tareas como completadas");
        usageLine(out, "delete <id...>", "Elimina tareas");
        usageLine(out, "help", "Muestra esta ayuda");
        out.println();
        out.println(Ansi.bold("Opciones:"));
        usageLine(out, "-h, --help", "Muestra esta ayuda");
        usageLine(out, "--version", "Muestra la versión");
        out.println();
        out.println(Ansi.bold("Entorno:"));
        usageLine(out, FILE_ENV, "Ruta del archivo de tareas");
        usageLine(out, "TASK_TRACKER_COLOR", "always | never (por defecto: automático)");
        usageLine(out, "NO_COLOR", "Desactiva los colores");
        out.println();
        out.println(Ansi.bold("Archivo de datos:"));
        out.println("  " + Ansi.dim(store.file().toString()));
        out.println();
        out.println(Ansi.bold("Ejemplos:"));
        out.println("  " + PROGRAM + " add Comprar leche");
        out.println("  " + PROGRAM + " list --pending");
        out.println("  " + PROGRAM + " done 1 3");
        out.println("  " + PROGRAM + " delete 2");
        out.println();
        out.println(Ansi.dim("Códigos de salida: 0 éxito · 1 error de uso · 2 error de archivo"));
    }

    private static void usageLine(PrintStream out, String command, String description) {
        out.println("  " + Ansi.cyan(String.format(Locale.ROOT, "%-24s", command))
                + Ansi.dim(description));
    }

    private enum Filter {
        ALL,
        PENDING,
        DONE;

        boolean matches(Task task) {
            return switch (this) {
                case ALL -> true;
                case PENDING -> !task.done();
                case DONE -> task.done();
            };
        }

        static Filter parse(String[] args) throws UsageException {
            if (args.length == 0) {
                return ALL;
            }
            if (args.length > 1) {
                throw new UsageException("el comando 'list' acepta como máximo un filtro");
            }

            return switch (args[0]) {
                case "--all", "all" -> ALL;
                case "--pending", "pending" -> PENDING;
                case "--done", "done" -> DONE;
                default -> throw new UsageException("filtro no válido para 'list': '" + args[0]
                        + "' (usa --pending, --done o --all)");
            };
        }
    }

    private static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
