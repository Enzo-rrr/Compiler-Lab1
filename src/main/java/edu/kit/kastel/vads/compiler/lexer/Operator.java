//********************************************** */
package edu.kit.kastel.vads.compiler.lexer;

import edu.kit.kastel.vads.compiler.Span;

public record Operator(OperatorType type, Span span) implements Token {

    @Override
    public boolean isOperator(OperatorType operatorType) {
        return type() == operatorType;
    }

    @Override
    public String asString() {
        return type().toString();
    }

    public enum OperatorType {
        // Assignment operators
        ASSIGN("="),
        ASSIGN_PLUS("+="),
        ASSIGN_MINUS("-="),
        ASSIGN_MUL("*="),
        ASSIGN_DIV("/="),
        ASSIGN_MOD("%="),
        ASSIGN_AND("&="),
        ASSIGN_XOR("^="),
        ASSIGN_OR("|="),
        ASSIGN_SHIFT_LEFT("<<="),
        ASSIGN_SHIFT_RIGHT(">>="),

        // Arithmetic operators
        PLUS("+"),
        MINUS("-"),
        MUL("*"),
        DIV("/"),
        MOD("%"),

        // Bitwise operators
        BITWISE_NOT("~"),
        BITWISE_AND("&"),
        BITWISE_XOR("^"),
        BITWISE_OR("|"),
        SHIFT_LEFT("<<"),
        SHIFT_RIGHT(">>"),

        // Logical operators
        LOGICAL_NOT("!"),
        LOGICAL_AND("&&"),
        LOGICAL_OR("||"),

        // Comparison operators
        LESS("<"),
        LESS_EQUAL("<="),
        GREATER(">"),
        GREATER_EQUAL(">="),
        EQUAL("=="),
        NOT_EQUAL("!="),

        // Ternary operator
        TERNARY_QUESTION("?"),
        TERNARY_COLON(":");

        private final String value;

        OperatorType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
