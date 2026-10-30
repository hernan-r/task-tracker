# Task Tracker CLI

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)
![Dependencias](https://img.shields.io/badge/dependencias-0-brightgreen)
![Interfaz](https://img.shields.io/badge/interfaz-CLI-blue)

Gestor de tareas para terminal escrito en Java, sin dependencias externas. Un único jar,
persistencia en texto plano y una salida cuidada: colores automáticos, tabla alineada,
barra de progreso, filtros y mensajes de error claros.

## Características

- **Sin dependencias**: solo el JDK; se compila con `javac` y se ejecuta con `java -jar`.
- **Comandos esenciales**: `add`, `list`, `done` y `delete`, con soporte para varios IDs.
- **Filtros**: `list --pending`, `list --done` y `list --all`.
- **Progreso visible**: barra y porcentaje globales en cada listado.
- **Colores con criterio**: se activan solo en terminal y respetan `NO_COLOR`.
- **Persistencia segura**: TSV en UTF-8, escritura atómica y compatible con edición manual.
- **Errores explícitos**: IDs inválidos, argumentos faltantes y archivos corruptos, con
  códigos de salida distintos para uso y almacenamiento.
- **Salida robusta**: descripciones con saltos de línea, tabuladores o ANSI se muestran
  escapadas y no rompen la tabla.

## Inicio rápido

Requisitos: JDK 17 o superior y `javac`/`jar` en el `PATH` (probado con JDK 27).

```bash
# Desde la raíz del repositorio
./build.sh

java -jar task-tracker.jar help
```

Primeras tareas:

```bash
java -jar task-tracker.jar add "Comprar leche"
java -jar task-tracker.jar add "Escribir el informe mensual"
java -jar task-tracker.jar list
java -jar task-tracker.jar done 1
java -jar task-tracker.jar list --pending
```

Salida de `list` (en una terminal se muestra con color):

```text
TAREAS (2)

ID  ESTADO  DESCRIPCIÓN
 1  [x]     Comprar leche
 2  [ ]     Escribir el informe mensual

Progreso [█████░░░░░] 1/2 (50%) · 1 pendiente
```

También puedes usar `make build`, `make test` y `make clean`.

## Comandos

| Comando | Descripción |
| --- | --- |
| `add <descripción...>` | Añade una tarea y le asigna el siguiente ID libre. |
| `list [--pending\|--done]` | Lista todas las tareas o solo las pendientes/completadas. |
| `done <id...>` | Marca una o varias tareas como completadas (idempotente). |
| `delete <id...>` | Elimina una o varias tareas. |
| `version`, `--version` | Muestra la versión. |
| `help`, `-h`, `--help` | Muestra el mensaje de uso. Sin argumentos también se muestra el uso. |

`done` y `delete` aceptan varios IDs: `done 1 3 5`. Se valida la lista completa antes
de escribir, así que un ID inválido no deja cambios a medias.

El siguiente ID es el máximo actual + 1. Si borras la tarea con el ID más alto, ese ID
puede reutilizarse.

## Interfaz de terminal

- **Colores automáticos**: solo se activan cuando la salida es una terminal. Si rediriges
  la salida a un archivo o a otro proceso, se emite texto plano sin códigos ANSI.
- **Tabla legible**: IDs en cian, pendientes en amarillo, completadas en verde y
  atenuadas; cabecera atenuada y columnas alineadas aunque los IDs crezcan.
- **Progreso**: barra y porcentaje globales, también visibles al usar un filtro.
- **Estados vacíos útiles**: sin tareas se muestra el comando exacto para crear la
  primera; `list --pending` sin pendientes lo indica explícitamente.
- **Mensajes claros**: `✓` para operaciones correctas, `•` para avisos idempotentes y
  `✗ Error:` con una sugerencia para los fallos.

Variables relacionadas:

```bash
TASK_TRACKER_COLOR=always java -jar task-tracker.jar list  # fuerza color
TASK_TRACKER_COLOR=never  java -jar task-tracker.jar list  # lo desactiva
NO_COLOR=1                java -jar task-tracker.jar list  # desactiva (tiene prioridad)
```

## Persistencia

Por defecto las tareas se guardan en:

```text
~/.task-tracker/tasks.tsv
```

Se puede usar otro archivo con la variable de entorno `TASK_TRACKER_FILE`:

```bash
TASK_TRACKER_FILE=./mis-tareas.tsv java -jar task-tracker.jar list
```

Formato (UTF-8, tabuladores):

```text
# task-tracker v1
1	0	Comprar leche
2	1	Escribir el informe mensual
```

- Campo 1: ID entero positivo y único.
- Campo 2: `0` pendiente, `1` completada.
- Campo 3: descripción. Las barras invertidas, tabuladores y saltos de línea se
  escapan como `\\`, `\t`, `\n` y `\r` para que el formato no se rompa.

Se toleran finales de línea CRLF y un BOM UTF-8 al inicio (habitual si el archivo se
editó con otra herramienta); al guardar se escribe siempre UTF-8 sin BOM. En `list`,
cualquier carácter de control de una descripción (tabuladores, saltos de línea, ESC...)
se muestra escapado, de modo que cada tarea ocupa una fila y el archivo no puede
inyectar secuencias ANSI en la terminal.

Las escrituras son atómicas: se escribe un archivo temporal y se renombra sobre el
original, de modo que un fallo a mitad de escritura no deja el archivo incompleto. No
hay bloqueo entre procesos; evita dos escrituras simultáneas sobre el mismo archivo.

## Manejo de errores

| Situación | Mensaje | Código de salida |
| --- | --- | --- |
| Comando desconocido | `✗ Error: comando desconocido: '...'` | 1 |
| Argumentos faltantes | `✗ Error: falta la descripción...` / `faltan los IDs...` | 1 |
| Filtro de `list` inválido | `✗ Error: filtro no válido para 'list': '...'` | 1 |
| ID no numérico o ≤ 0 | `✗ Error: ID inválido: '...'` | 1 |
| ID inexistente | `✗ Error: no existe ninguna tarea con ID ...` | 1 |
| Archivo corrupto | `✗ Error: el archivo de tareas está corrupto en la línea N: ...` | 2 |
| Archivo no UTF-8 | `✗ Error: el archivo de tareas está corrupto: el archivo no es UTF-8 válido` | 2 |
| Error de lectura/escritura | `✗ Error: no se pudo leer/escribir el archivo...` | 2 |

Si el archivo está corrupto, el CLI **no lo sobrescribe**: muestra la línea y el motivo,
y sugiere corregirlo o hacer una copia antes de empezar de cero. Se detectan cabecera
faltante, campos incompletos, IDs no numéricos/duplicados/no positivos, IDs fuera de
rango, estados distintos de `0`/`1`, descripciones vacías, escapes inválidos y archivos
que no son UTF-8 válido.

## Desarrollo

### Pruebas

```bash
./test.sh
# o: make test
```

Las pruebas de integración compilan el proyecto, usan un archivo temporal (no tocan tus
tareas reales) y cubren el uso y la versión, `add`/`list`/`done`/`delete`, filtros,
persistencia, validación de IDs, descripciones con tabuladores/saltos de línea y
secuencias ANSI, BOM/CRLF, colores (`always`/`NO_COLOR`), progreso y los distintos casos
de archivo corrupto.

### Estructura

```text
src/main/java/tasktracker/
  Main.java                  Comandos, filtros, tabla, progreso, uso y códigos de salida
  Ansi.java                  Colores ANSI con detección de terminal y NO_COLOR
  Task.java                  Modelo de tarea (record)
  TaskStore.java             Carga, validación y guardado atómico del archivo
  StorageException.java      Error de lectura/escritura
  CorruptFileException.java  Archivo con formato inválido (incluye línea y motivo)
build.sh                     Compilación con javac + jar
test.sh                      Pruebas de integración
Makefile                     Atajos build/test/clean/run
```

### Contribuir

Las issues y pull requests son bienvenidas. Antes de enviar cambios, ejecuta `./test.sh`
y mantén el estilo y los mensajes existentes.
