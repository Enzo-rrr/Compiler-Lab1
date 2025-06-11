#!/usr/bin/env sh
set -e

# Get the input and output files from arguments
INPUT_FILE="$1"
OUTPUT_FILE="${@: -1}"  # Last argument is the output file
TEMP_ASM="temp.s"

# Get the compiler binary path
BIN_DIR="$(dirname "$0")/build/install/compiler/bin"
COMPILER="$BIN_DIR/compiler"

# Run the compiler to generate assembly
"$COMPILER" "$INPUT_FILE" "$TEMP_ASM"

# Use gcc to compile the assembly into an executable
gcc -o "$OUTPUT_FILE" "$TEMP_ASM"

# Clean up temporary assembly file
rm "$TEMP_ASM"