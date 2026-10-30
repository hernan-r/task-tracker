#!/usr/bin/env bash
# Pruebas de integración del CLI. Usa un archivo temporal, no toca tus tareas reales.
set -uo pipefail
cd "$(dirname "$0")"

./build.sh >/dev/null

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
export TASK_TRACKER_FILE="$tmp_dir/tasks.tsv"

failures=0
LAST_OUT=""

pass() {
  echo "ok   - $1"
}

fail() {
  echo "FAIL - $1"
  failures=$((failures + 1))
}

run() {
  java -jar task-tracker.jar "$@"
}

# expect_exit <código esperado> <descripción> <comando...>
expect_exit() {
  local expected="$1"
  local description="$2"
  shift 2

  local out code
  out="$("$@" 2>&1)"
  code=$?
  LAST_OUT="$out"

  if [[ "$code" -eq "$expected" ]]; then
    pass "$description (exit $code)"
  else
    fail "$description (esperaba exit $expected, obtuve $code)"
    printf '%s\n' "$out" | sed 's/^/       /'
  fi
}

# assert_contains <descripción> <texto buscado>
assert_contains() {
  if [[ "$LAST_OUT" == *"$2"* ]]; then
    pass "$1"
  else
    fail "$1 (no se encontró '$2')"
    printf '%s\n' "$LAST_OUT" | sed 's/^/       /'
  fi
}

# assert_not_contains <descripción> <texto que no debe aparecer>
assert_not_contains() {
  if [[ "$LAST_OUT" != *"$2"* ]]; then
    pass "$1"
  else
    fail "$1 (no debía aparecer '$2')"
    printf '%s\n' "$LAST_OUT" | sed 's/^/       /'
  fi
}

echo "== Uso y comandos =="
expect_exit 0 "sin argumentos muestra el uso" run
assert_contains "el uso incluye 'Uso:'" "Uso:"
expect_exit 0 "help muestra el uso" run help
expect_exit 0 "--help muestra el uso" run --help
expect_exit 0 "--version muestra la versión" run --version
assert_contains "la versión es 1.0.0" "1.0.0"
expect_exit 1 "comando desconocido falla" run frobnicate
assert_contains "comando desconocido explica el error" "comando desconocido"

expect_exit 0 "TASK_TRACKER_COLOR=always activa ANSI" \
  env TASK_TRACKER_COLOR=always java -jar task-tracker.jar list
if [[ "$LAST_OUT" == *$'\033['* ]]; then
  pass "la salida forzada contiene secuencias ANSI"
else
  fail "la salida forzada no contiene secuencias ANSI"
fi
expect_exit 0 "NO_COLOR tiene prioridad sobre el forzado" \
  env NO_COLOR=1 TASK_TRACKER_COLOR=always java -jar task-tracker.jar list
if [[ "$LAST_OUT" == *$'\033['* ]]; then
  fail "NO_COLOR no desactivó los colores"
else
  pass "NO_COLOR desactiva los colores"
fi

echo
echo "== add y list =="
expect_exit 1 "add sin descripción falla" run add
assert_contains "add sin descripción lo indica" "falta la descripción"
expect_exit 1 "add con solo espacios falla" run add "   "
expect_exit 0 "add crea la primera tarea" run add Comprar leche
assert_contains "add informa el ID" "Tarea 1 añadida"
expect_exit 0 "add une varias palabras" run add Escribir el informe mensual
expect_exit 0 "add crea la tercera tarea" run add Pagar la luz
expect_exit 0 "add conserva barras invertidas" run add 'Ruta C:\temp\nueva'
assert_contains "add muestra la descripción tal cual" 'Ruta C:\temp\nueva'
expect_exit 0 "list relee caracteres especiales" run list
assert_contains "list conserva las barras invertidas" 'Ruta C:\temp\nueva'
if grep -Fq 'Ruta C:\\temp\\nueva' "$TASK_TRACKER_FILE"; then
  pass "la descripción se escapa en el archivo"
else
  fail "la descripción no se escapó correctamente"
fi
expect_exit 0 "delete limpia la tarea especial" run delete 4
expect_exit 0 "list funciona" run list
assert_contains "list muestra la tarea 1" "Comprar leche"
assert_contains "list muestra la tarea 2" "Escribir el informe mensual"
assert_contains "list muestra tareas pendientes" "[ ]"
assert_contains "list muestra el progreso" "0/3 (0%)"
assert_contains "list muestra las pendientes" "3 pendientes"
if [[ "$LAST_OUT" == *$'\033['* ]]; then
  fail "la salida canalizada no debe contener ANSI"
else
  pass "la salida canalizada no contiene ANSI"
fi
expect_exit 1 "list rechaza filtros desconocidos" run list extra
assert_contains "el filtro desconocido se explica" "filtro no válido"
expect_exit 1 "done con ID no numérico falla" run done abc
assert_contains "ID no numérico se explica" "ID inválido"
expect_exit 1 "done con ID cero falla" run done 0
expect_exit 1 "delete con ID negativo falla" run delete -1
expect_exit 1 "done con ID inexistente falla" run done 99
assert_contains "ID inexistente se explica" "no existe ninguna tarea con ID 99"
expect_exit 1 "done sin IDs falla" run done
assert_contains "done sin IDs lo indica" "faltan los IDs"

echo
echo "== done, delete y persistencia =="
expect_exit 0 "done marca la tarea 1" run done 1
assert_contains "done informa la tarea" "Tarea 1 completada"
expect_exit 0 "done en tarea ya completada es idempotente" run done 1
assert_contains "done informa que ya estaba" "ya estaba completada"
expect_exit 0 "list se relee desde el archivo" run list
assert_contains "list muestra la tarea completada" "[x]"
assert_contains "list muestra el progreso" "1/3 (33%)"
assert_contains "list muestra dos pendientes" "2 pendientes"

expect_exit 0 "list --pending filtra pendientes" run list --pending
assert_contains "el filtro pendientes incluye la tarea 2" "Escribir el informe mensual"
assert_not_contains "el filtro pendientes oculta la completada" "Comprar leche"
assert_contains "el filtro tiene su propio título" "TAREAS PENDIENTES"

expect_exit 0 "list --done filtra completadas" run list --done
assert_contains "el filtro completadas incluye la tarea 1" "Comprar leche"
assert_not_contains "el filtro completadas oculta las pendientes" "Escribir el informe mensual"
assert_contains "el filtro completadas tiene su título" "TAREAS COMPLETADAS"

expect_exit 0 "list pending también funciona" run list pending
assert_contains "el alias sin guiones filtra" "TAREAS PENDIENTES"
expect_exit 0 "list --all muestra todo" run list --all
assert_contains "el filtro all incluye completadas" "Comprar leche"
assert_contains "el filtro all incluye pendientes" "Pagar la luz"

expect_exit 0 "done acepta varios IDs" run done 2 3
expect_exit 1 "done valida todos los IDs antes de escribir" run done 1 99
expect_exit 0 "list mantiene los cambios" run list
assert_contains "las tres tareas están completadas" "3/3 (100%)"
expect_exit 0 "list --pending sin pendientes" run list --pending
assert_contains "list informa que no hay pendientes" "No hay tareas pendientes"
assert_contains "el progreso global sigue visible" "3/3 (100%)"
expect_exit 0 "delete elimina la tarea 2" run delete 2
assert_contains "delete informa la tarea eliminada" "Tarea 2 eliminada"
expect_exit 0 "list ya no muestra la tarea eliminada" run list
assert_not_contains "la tarea eliminada desaparece" "Escribir el informe mensual"
expect_exit 1 "delete de una tarea ya eliminada falla" run delete 2
expect_exit 0 "delete varias tareas" run delete 1 3
expect_exit 0 "list detecta que no hay tareas" run list
assert_contains "list informa lista vacía" "No hay tareas"

if [[ ! -f "$TASK_TRACKER_FILE" ]]; then
  fail "el archivo de tareas no se creó"
else
  pass "el archivo de tareas existe"
fi

echo
echo "== Archivo corrupto =="
printf '# task-tracker v1\n1\t0\t\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "list detecta una descripción vacía" run list
assert_contains "el error indica archivo corrupto" "corrupto"
assert_contains "el error indica la línea" "línea 2"
expect_exit 2 "add no sobrescribe un archivo corrupto" run add Otra tarea
assert_contains "add también informa la corrupción" "corrupto"
if grep -q '^1	0	$' "$TASK_TRACKER_FILE"; then
  pass "el archivo corrupto queda intacto"
else
  fail "el archivo corrupto se modificó"
fi

printf '1\t0\tSin cabecera\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "detecta cabecera faltante" run list
assert_contains "el error menciona la cabecera" "cabecera"

printf '# task-tracker v1\n1\t2\tEstado raro\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "detecta estado inválido" run list

printf '# task-tracker v1\n1\t0\tA\n1\t0\tB\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "detecta IDs duplicados" run list

printf '# task-tracker v1\n99999999999999999999\t0\tX\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "detecta ID fuera de rango" run list
assert_contains "el error menciona el rango" "fuera de rango"

printf '# task-tracker v1\n1\t0\t\xff\n' > "$TASK_TRACKER_FILE"
expect_exit 2 "detecta UTF-8 inválido" run list
assert_contains "el error menciona UTF-8" "UTF-8"

printf '\xef\xbb\xbf# task-tracker v1\n1\t0\tCon BOM\n' > "$TASK_TRACKER_FILE"
expect_exit 0 "tolera un BOM UTF-8" run list
assert_contains "el BOM no oculta la tarea" "Con BOM"
expect_exit 0 "add reescribe el archivo" run add Otra
if [[ "$(head -c 3 "$TASK_TRACKER_FILE" | od -An -tx1 | tr -d ' \n')" == "efbbbf" ]]; then
  fail "el archivo guardado conserva el BOM"
else
  pass "el archivo guardado no tiene BOM"
fi

echo
echo "== Descripciones con caracteres de control =="
: > "$TASK_TRACKER_FILE"
expect_exit 0 "add guarda una descripción multilínea" run add $'Linea uno\nLinea dos'
expect_exit 0 "add guarda un tabulador" run add $'Con\ttabulador'
expect_exit 0 "list funciona con caracteres de control" run list
assert_contains "list escapa el salto de línea" 'Linea uno\nLinea dos'
assert_contains "list escapa el tabulador" 'Con\ttabulador'
if [[ "$(grep -c '^[0-9]' "$TASK_TRACKER_FILE")" -eq 2 ]]; then
  pass "cada tarea ocupa una sola línea del TSV"
else
  fail "el TSV no tiene una tarea por línea"
  sed -n l "$TASK_TRACKER_FILE"
fi
if [[ "$LAST_OUT" == *$'\t'* ]]; then
  fail "la salida de list contiene tabuladores literales"
else
  pass "la salida de list no contiene tabuladores literales"
fi

printf '# task-tracker v1\n1\t0\tPeligro \x1b[31mrojo\x1b[0m\n' > "$TASK_TRACKER_FILE"
expect_exit 0 "list sanea secuencias ANSI" run list
assert_contains "la secuencia ANSI se muestra escapada" 'Peligro \u001b[31mrojo\u001b[0m'
if [[ "$LAST_OUT" == *$'\033'* ]]; then
  fail "list emitió el ESC que venía en el archivo"
else
  pass "list no emite controles del archivo"
fi

echo
if [[ "$failures" -eq 0 ]]; then
  echo "Todo OK"
else
  echo "$failures prueba(s) fallaron"
  exit 1
fi
