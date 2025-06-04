package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BinaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.NegateTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.TernaryTree;
import edu.kit.kastel.vads.compiler.parser.ast.TypeTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

public class TypeAnalysis implements NoOpVisitor<Namespace<TypeAnalysis.Type>> {

    public enum Type {
        INT,
        BOOL,
        VOID;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    @Override
    public Unit visit(AssignmentTree assignmentTree, Namespace<Type> data) {
        if (assignmentTree.lValue() instanceof LValueIdentTree lValue) {
            Type lValueType = data.get(lValue.name());
            Type exprType = getExpressionType(assignmentTree.expression(), data);
            
            if (lValueType != exprType) {
                throw new SemanticException("Type mismatch in assignment: expected " + lValueType + ", got " + exprType);
            }
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BinaryOperationTree binaryOperationTree, Namespace<Type> data) {
        Type lhsType = getExpressionType(binaryOperationTree.lhs(), data);
        Type rhsType = getExpressionType(binaryOperationTree.rhs(), data);
        
        // Check operator type compatibility
        switch (binaryOperationTree.operatorType()) {
            case PLUS, MINUS, MUL, DIV, MOD -> {
                if (lhsType != Type.INT || rhsType != Type.INT) {
                    throw new SemanticException("Arithmetic operators require integer operands");
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (lhsType != Type.INT || rhsType != Type.INT) {
                    throw new SemanticException("Bitwise shift operators require integer operands");
                }
            }
            case BITWISE_AND, BITWISE_OR, BITWISE_XOR -> {
                if (lhsType != Type.INT || rhsType != Type.INT) {
                    throw new SemanticException("Bitwise operators require integer operands");
                }
            }
            case EQUAL, NOT_EQUAL -> {
                if (lhsType != rhsType) {
                    throw new SemanticException("Comparison operators require operands of the same type");
                }
            }
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                if (lhsType != Type.INT || rhsType != Type.INT) {
                    throw new SemanticException("Comparison operators require integer operands");
                }
            }
            case LOGICAL_AND, LOGICAL_OR -> {
                if (lhsType != Type.BOOL || rhsType != Type.BOOL) {
                    throw new SemanticException("Logical operators require boolean operands");
                }
            }
        }
        
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(DeclarationTree declarationTree, Namespace<Type> data) {
        Type declaredType = getTypeFromTypeTree(declarationTree.type());
        if (declarationTree.initializer() != null) {
            Type initType = getExpressionType(declarationTree.initializer(), data);
            if (declaredType != initType) {
                throw new SemanticException("Type mismatch in declaration: expected " + declaredType + ", got " + initType);
            }
        }
        data.put(declarationTree.name(), declaredType, (existing, replacement) -> replacement);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(FunctionTree functionTree, Namespace<Type> data) {
        Type returnType = getTypeFromTypeTree(functionTree.returnType());
        data.put(functionTree.name(), returnType, (existing, replacement) -> replacement);
        
        // Recursively visit the function body
        functionTree.body().accept(this, data);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(IfTree ifTree, Namespace<Type> data) {
        Type conditionType = getExpressionType(ifTree.condition(), data);
        if (conditionType != Type.BOOL) {
            throw new SemanticException("If condition must be boolean");
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(WhileTree whileTree, Namespace<Type> data) {
        Type conditionType = getExpressionType(whileTree.condition(), data);
        if (conditionType != Type.BOOL) {
            throw new SemanticException("While condition must be boolean");
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ForTree forTree, Namespace<Type> data) {
        Type conditionType = getExpressionType(forTree.condition(), data);
        if (conditionType != Type.BOOL) {
            throw new SemanticException("For condition must be boolean");
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(TernaryTree ternaryTree, Namespace<Type> data) {
        Type conditionType = getExpressionType(ternaryTree.condition(), data);
        if (conditionType != Type.BOOL) {
            throw new SemanticException("Ternary condition must be boolean");
        }
        
        Type thenType = getExpressionType(ternaryTree.thenExpr(), data);
        Type elseType = getExpressionType(ternaryTree.elseExpr(), data);
        
        if (thenType != elseType) {
            throw new SemanticException("Ternary branches must have the same type");
        }
        
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ReturnTree returnTree, Namespace<Type> data) {
        Type returnType = getExpressionType(returnTree.expression(), data);
        // For now, assume main function should return int
        // TODO: This should be improved to check against the actual function's declared return type
        if (returnType != Type.INT) {
            throw new SemanticException("Function must return int, but got " + returnType);
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BreakTree breakTree, Namespace<Type> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ContinueTree continueTree, Namespace<Type> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ProgramTree programTree, Namespace<Type> data) {
        // Recursively visit all functions in the program
        for (var function : programTree.topLevelTrees()) {
            function.accept(this, data);
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BlockTree blockTree, Namespace<Type> data) {
        // Recursively visit all statements in the block
        for (var statement : blockTree.statements()) {
            statement.accept(this, data);
        }
        return Unit.INSTANCE;
    }

    private Type getExpressionType(ExpressionTree expression, Namespace<Type> data) {
        if (expression instanceof LiteralTree literal) {
            return literal.value().equals("true") || literal.value().equals("false") ? Type.BOOL : Type.INT;
        } else if (expression instanceof IdentExpressionTree ident) {
            Type type = data.get(ident.name());
            if (type == null) {
                throw new SemanticException("Undefined variable: " + ident.name());
            }
            return type;
        } else if (expression instanceof BinaryOperationTree binary) {
            // First check operand types
            Type lhsType = getExpressionType(binary.lhs(), data);
            Type rhsType = getExpressionType(binary.rhs(), data);
            
            // Type is determined by the operator, but we must check operand compatibility first
            switch (binary.operatorType()) {
                case PLUS, MINUS, MUL, DIV, MOD -> {
                    if (lhsType != Type.INT || rhsType != Type.INT) {
                        throw new SemanticException("Arithmetic operators require integer operands, got " + lhsType + " and " + rhsType);
                    }
                    return Type.INT;
                }
                case EQUAL, NOT_EQUAL -> {
                    if (lhsType != rhsType) {
                        throw new SemanticException("Comparison operators require operands of the same type, got " + lhsType + " and " + rhsType);
                    }
                    return Type.BOOL;
                }
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                    if (lhsType != Type.INT || rhsType != Type.INT) {
                        throw new SemanticException("Comparison operators require integer operands, got " + lhsType + " and " + rhsType);
                    }
                    return Type.BOOL;
                }
                case LOGICAL_AND, LOGICAL_OR -> {
                    if (lhsType != Type.BOOL || rhsType != Type.BOOL) {
                        throw new SemanticException("Logical operators require boolean operands, got " + lhsType + " and " + rhsType);
                    }
                    return Type.BOOL;
                }
                case SHIFT_LEFT, SHIFT_RIGHT -> {
                    if (lhsType != Type.INT || rhsType != Type.INT) {
                        throw new SemanticException("Bitwise shift operators require integer operands, got " + lhsType + " and " + rhsType);
                    }
                    return Type.INT;
                }
                case BITWISE_AND, BITWISE_OR, BITWISE_XOR -> {
                    if (lhsType != Type.INT || rhsType != Type.INT) {
                        throw new SemanticException("Bitwise operators require integer operands, got " + lhsType + " and " + rhsType);
                    }
                    return Type.INT;
                }
                default -> throw new SemanticException("Unsupported operator: " + binary.operatorType());
            }
        } else if (expression instanceof NegateTree negate) {
            Type type = getExpressionType(negate.expression(), data);
            if (type != Type.INT) {
                throw new SemanticException("Arithmetic negation operator requires integer operand, got " + type);
            }
            return Type.INT;
        } else if (expression instanceof TernaryTree ternary) {
            Type thenType = getExpressionType(ternary.thenExpr(), data);
            Type elseType = getExpressionType(ternary.elseExpr(), data);
            if (thenType != elseType) {
                throw new SemanticException("Ternary branches must have the same type");
            }
            return thenType;
        }
        throw new SemanticException("Unsupported expression type");
    }

    private Type getTypeFromTypeTree(TypeTree typeTree) {
        if (typeTree.type() instanceof BasicType basicType) {
            return switch (basicType) {
                case INT -> Type.INT;
                case BOOL -> Type.BOOL;
                case VOID -> Type.VOID;
            };
        }
        throw new SemanticException("Unsupported type: " + typeTree.type());
    }
} 