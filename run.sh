#!/usr/bin/env sh
set -e

# 1. 接收两个参数：输入 C 文件 和 最终可执行输出名
INPUT="$1"     # 例如 test.c
OUTPUT="$2"    # 例如 test.s 最后会变成可执行 test

# 2. 中间汇编文件
TEMP_ASM="temp.s"

# 3. 直接用 java -cp 调用 Main，生成纯文本汇编
java -cp "$(dirname "$0")/build/libs/compiler-1.0-SNAPSHOT.jar" \
    edu.kit.kastel.vads.compiler.Main \
    "$INPUT" "$TEMP_ASM"

# 4. 用 gcc 把汇编文件组装并链接成二进制
gcc -o "$OUTPUT" "$TEMP_ASM"

# 5. 删除临时汇编
rm "$TEMP_ASM"
