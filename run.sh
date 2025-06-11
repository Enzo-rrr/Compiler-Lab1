#!/usr/bin/env sh
set -e

# ——————————————————————————
# 1. 参数检查
if [ $# -ne 2 ]; then
  echo "Usage: $0 <input.c> <output_executable>" >&2
  exit 1
fi
INPUT="$1"
OUTPUT="$2"

# ——————————————————————————
# 2. 找到编译器 JAR
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIBDIR="$SCRIPT_DIR/build/libs"
# 匹配第一个 compiler-*.jar
JAR=$(ls "$LIBDIR"/compiler-*.jar 2>/dev/null | head -n1)
if [ ! -f "$JAR" ]; then
  echo "Error: cannot find compiler jar in $LIBDIR" >&2
  exit 1
fi

TEMP_ASM="$(mktemp /tmp/compiler_asm_XXXXXX).s"

# 4. 用你的编译器生成纯文本汇编
echo "Generating assembly with $JAR ..."
java -cp "$JAR" edu.kit.kastel.vads.compiler.Main \
     "$INPUT" "$TEMP_ASM"

# 5. 用 gcc 把汇编编译并链接成可执行
echo "Assembling and linking to produce $OUTPUT ..."
gcc -o "$OUTPUT" "$TEMP_ASM"

# 6. 清理
rm "$TEMP_ASM"

echo "Done: ./$OUTPUT"
