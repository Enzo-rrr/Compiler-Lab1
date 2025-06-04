package edu.kit.kastel.vads.compiler.parser;

import java.util.ArrayList;
import java.util.List;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;
import edu.kit.kastel.vads.compiler.lexer.KeywordType;
import edu.kit.kastel.vads.compiler.lexer.NumberLiteral;
import edu.kit.kastel.vads.compiler.lexer.Operator;
import edu.kit.kastel.vads.compiler.lexer.Operator.OperatorType;
import edu.kit.kastel.vads.compiler.lexer.Separator;
import edu.kit.kastel.vads.compiler.lexer.Separator.SeparatorType;
import edu.kit.kastel.vads.compiler.lexer.Token;
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
import edu.kit.kastel.vads.compiler.parser.ast.LValueTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.NegateTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.ast.TernaryTree;
import edu.kit.kastel.vads.compiler.parser.ast.TypeTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.type.BasicType;

public class Parser {
    private final TokenSource tokenSource;

    public Parser(TokenSource tokenSource) {
        this.tokenSource = tokenSource;
    }

    public ProgramTree parseProgram() {
        ProgramTree programTree = new ProgramTree(List.of(parseFunction()));
        if (this.tokenSource.hasMore()) {
            throw new ParseException("expected end of input but got " + this.tokenSource.peek());
        }
        return programTree;
    }

    private FunctionTree parseFunction() {
        Keyword returnType = this.tokenSource.expectKeyword(KeywordType.INT);
        Identifier identifier = this.tokenSource.expectIdentifier();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        BlockTree body = parseBlock();
        return new FunctionTree(
            new TypeTree(BasicType.INT, returnType.span()),
            name(identifier),
            body
        );
    }

    private BlockTree parseBlock() {
        Separator bodyOpen = this.tokenSource.expectSeparator(SeparatorType.BRACE_OPEN);
        List<StatementTree> statements = new ArrayList<>();
        while (!(this.tokenSource.peek() instanceof Separator sep && sep.type() == SeparatorType.BRACE_CLOSE)) {
            statements.add(parseStatement());
        }
        Separator bodyClose = this.tokenSource.expectSeparator(SeparatorType.BRACE_CLOSE);
        return new BlockTree(statements, bodyOpen.span().merge(bodyClose.span()));
    }

    private StatementTree parseStatement() {
        StatementTree statement;
        if (this.tokenSource.peek().isSeparator(SeparatorType.BRACE_OPEN)) {
            statement = parseBlock();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.INT) || 
            this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            statement = parseDeclaration();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.RETURN)) {
            statement = parseReturn();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.IF)) {
            statement = parseIf();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.WHILE)) {
            statement = parseWhile();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.FOR)) {
            statement = parseFor();
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BREAK)) {
            statement = parseBreak();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else if (this.tokenSource.peek().isKeyword(KeywordType.CONTINUE)) {
            statement = parseContinue();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        } else {
            statement = parseSimple();
            this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        }
        return statement;
    }

    private StatementTree parseDeclaration() {
        Keyword type;
        BasicType basicType;
        if (this.tokenSource.peek().isKeyword(KeywordType.INT)) {
            type = this.tokenSource.expectKeyword(KeywordType.INT);
            basicType = BasicType.INT;
        } else if (this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            type = this.tokenSource.expectKeyword(KeywordType.BOOL);
            basicType = BasicType.BOOL;
        } else {
            throw new ParseException("expected type (int or bool) but got " + this.tokenSource.peek());
        }
        Identifier ident = this.tokenSource.expectIdentifier();
        ExpressionTree expr = null;
        if (this.tokenSource.peek().isOperator(OperatorType.ASSIGN)) {
            this.tokenSource.expectOperator(OperatorType.ASSIGN);
            expr = parseExpression();
        }
        return new DeclarationTree(new TypeTree(basicType, type.span()), name(ident), expr);
    }

    private StatementTree parseSimple() {
        LValueTree lValue = parseLValue();
        Operator assignmentOperator = parseAssignmentOperator();
        ExpressionTree expression = parseExpression();
        return new AssignmentTree(lValue, assignmentOperator, expression);
    }

    private Operator parseAssignmentOperator() {
        if (this.tokenSource.peek() instanceof Operator op) {
            return switch (op.type()) {
                case ASSIGN, ASSIGN_DIV, ASSIGN_MINUS, ASSIGN_MOD, ASSIGN_MUL, ASSIGN_PLUS,
                     ASSIGN_AND, ASSIGN_XOR, ASSIGN_OR, ASSIGN_SHIFT_LEFT, ASSIGN_SHIFT_RIGHT -> {
                    this.tokenSource.consume();
                    yield op;
                }
                default -> throw new ParseException("expected assignment but got " + op.type());
            };
        }
        throw new ParseException("expected assignment but got " + this.tokenSource.peek());
    }

    private LValueTree parseLValue() {
        if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_OPEN)) {
            this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
            LValueTree inner = parseLValue();
            this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
            return inner;
        }
        Identifier identifier = this.tokenSource.expectIdentifier();
        return new LValueIdentTree(name(identifier));
    }

    private StatementTree parseReturn() {
        Keyword ret = this.tokenSource.expectKeyword(KeywordType.RETURN);
        ExpressionTree expression = parseExpression();
        return new ReturnTree(expression, ret.span().start());
    }

    private StatementTree parseIf() {
        Keyword ifKeyword = this.tokenSource.expectKeyword(KeywordType.IF);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        StatementTree thenBranch = parseStatement();
        StatementTree elseBranch = null;
        if (this.tokenSource.peek().isKeyword(KeywordType.ELSE)) {
            this.tokenSource.expectKeyword(KeywordType.ELSE);
            elseBranch = parseStatement();
        }
        return new IfTree(condition, thenBranch, elseBranch, ifKeyword.span());
    }

    private StatementTree parseWhile() {
        Keyword whileKeyword = this.tokenSource.expectKeyword(KeywordType.WHILE);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        StatementTree body = parseStatement();
        return new WhileTree(condition, body, whileKeyword.span());
    }

    private StatementTree parseFor() {
        Keyword forKeyword = this.tokenSource.expectKeyword(KeywordType.FOR);
        this.tokenSource.expectSeparator(SeparatorType.PAREN_OPEN);
        StatementTree initializer = parseForInitializer();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        ExpressionTree condition = parseExpression();
        this.tokenSource.expectSeparator(SeparatorType.SEMICOLON);
        StatementTree step = parseForStep();
        this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
        StatementTree body = parseStatement();
        return new ForTree(initializer, condition, step, body, forKeyword.span());
    }

    private StatementTree parseForInitializer() {
        if (this.tokenSource.peek().isKeyword(KeywordType.INT) || 
            this.tokenSource.peek().isKeyword(KeywordType.BOOL)) {
            return parseDeclaration();
        } else if (this.tokenSource.peek() instanceof Identifier) {
            return parseSimple();
        } else if (this.tokenSource.peek().isSeparator(SeparatorType.SEMICOLON)) {
            return null;
        }
        throw new ParseException("expected declaration, assignment, or semicolon in for initializer but got " + this.tokenSource.peek());
    }

    private StatementTree parseForStep() {
        if (this.tokenSource.peek() instanceof Identifier) {
            return parseSimple();
        } else if (this.tokenSource.peek().isSeparator(SeparatorType.PAREN_CLOSE)) {
            return null;
        }
        throw new ParseException("expected assignment or closing parenthesis in for step but got " + this.tokenSource.peek());
    }

    private StatementTree parseBreak() {
        Keyword breakKeyword = this.tokenSource.expectKeyword(KeywordType.BREAK);
        return new BreakTree(breakKeyword.span());
    }

    private StatementTree parseContinue() {
        Keyword continueKeyword = this.tokenSource.expectKeyword(KeywordType.CONTINUE);
        return new ContinueTree(continueKeyword.span());
    }

    private ExpressionTree parseExpression() {
        ExpressionTree lhs = parseLogicalOr();
        return lhs;
    }

    private ExpressionTree parseLogicalOr() {
        ExpressionTree lhs = parseLogicalAnd();
        while (this.tokenSource.peek().isOperator(OperatorType.LOGICAL_OR)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.LOGICAL_OR);
            ExpressionTree rhs = parseLogicalAnd();
            lhs = new BinaryOperationTree(lhs, rhs, op.type());
        }
        return lhs;
    }

    private ExpressionTree parseLogicalAnd() {
        ExpressionTree lhs = parseBitwiseOr();
        while (this.tokenSource.peek().isOperator(OperatorType.LOGICAL_AND)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.LOGICAL_AND);
            ExpressionTree rhs = parseBitwiseOr();
            lhs = new BinaryOperationTree(lhs, rhs, op.type());
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseOr() {
        ExpressionTree lhs = parseBitwiseXor();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_OR)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.BITWISE_OR);
            lhs = new BinaryOperationTree(lhs, parseBitwiseXor(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseXor() {
        ExpressionTree lhs = parseBitwiseAnd();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_XOR)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.BITWISE_XOR);
            lhs = new BinaryOperationTree(lhs, parseBitwiseAnd(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseBitwiseAnd() {
        ExpressionTree lhs = parseEquality();
        while (this.tokenSource.peek().isOperator(OperatorType.BITWISE_AND)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.BITWISE_AND);
            lhs = new BinaryOperationTree(lhs, parseEquality(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseEquality() {
        ExpressionTree lhs = parseRelational();
        while (this.tokenSource.peek().isOperator(OperatorType.EQUAL) || 
               this.tokenSource.peek().isOperator(OperatorType.NOT_EQUAL)) {
            Operator op = this.tokenSource.expectOperator(
                this.tokenSource.peek().isOperator(OperatorType.EQUAL) ? 
                OperatorType.EQUAL : OperatorType.NOT_EQUAL
            );
            lhs = new BinaryOperationTree(lhs, parseRelational(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseRelational() {
        ExpressionTree lhs = parseShift();
        while (this.tokenSource.peek().isOperator(OperatorType.LESS) || 
               this.tokenSource.peek().isOperator(OperatorType.LESS_EQUAL) ||
               this.tokenSource.peek().isOperator(OperatorType.GREATER) ||
               this.tokenSource.peek().isOperator(OperatorType.GREATER_EQUAL)) {
            Operator op = this.tokenSource.expectOperator(
                switch (this.tokenSource.peek().asString()) {
                    case "<" -> OperatorType.LESS;
                    case "<=" -> OperatorType.LESS_EQUAL;
                    case ">" -> OperatorType.GREATER;
                    case ">=" -> OperatorType.GREATER_EQUAL;
                    default -> throw new ParseException("expected relational operator but got " + this.tokenSource.peek());
                }
            );
            lhs = new BinaryOperationTree(lhs, parseShift(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseShift() {
        ExpressionTree lhs = parseAdditive();
        while (this.tokenSource.peek().isOperator(OperatorType.SHIFT_LEFT) || 
               this.tokenSource.peek().isOperator(OperatorType.SHIFT_RIGHT)) {
            Operator op = this.tokenSource.expectOperator(
                this.tokenSource.peek().isOperator(OperatorType.SHIFT_LEFT) ? 
                OperatorType.SHIFT_LEFT : OperatorType.SHIFT_RIGHT
            );
            lhs = new BinaryOperationTree(lhs, parseAdditive(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseAdditive() {
        ExpressionTree lhs = parseMultiplicative();
        while (this.tokenSource.peek().isOperator(OperatorType.PLUS) || 
               this.tokenSource.peek().isOperator(OperatorType.MINUS)) {
            Operator op = this.tokenSource.expectOperator(
                this.tokenSource.peek().isOperator(OperatorType.PLUS) ? 
                OperatorType.PLUS : OperatorType.MINUS
            );
            lhs = new BinaryOperationTree(lhs, parseMultiplicative(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseMultiplicative() {
        ExpressionTree lhs = parseUnary();
        while (this.tokenSource.peek().isOperator(OperatorType.MUL) || 
               this.tokenSource.peek().isOperator(OperatorType.DIV) ||
               this.tokenSource.peek().isOperator(OperatorType.MOD)) {
            Operator op = this.tokenSource.expectOperator(
                switch (this.tokenSource.peek().asString()) {
                    case "*" -> OperatorType.MUL;
                    case "/" -> OperatorType.DIV;
                    case "%" -> OperatorType.MOD;
                    default -> throw new ParseException("expected multiplicative operator but got " + this.tokenSource.peek());
                }
            );
            lhs = new BinaryOperationTree(lhs, parseUnary(), op.type());
        }
        return lhs;
    }

    private ExpressionTree parseUnary() {
        if (this.tokenSource.peek().isOperator(OperatorType.MINUS)) {
            Span span = this.tokenSource.expectOperator(OperatorType.MINUS).span();
            return new NegateTree(parseUnary(), span);
        } else if (this.tokenSource.peek().isOperator(OperatorType.LOGICAL_NOT)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.LOGICAL_NOT);
            return new BinaryOperationTree(parseUnary(), null, op.type());
        } else if (this.tokenSource.peek().isOperator(OperatorType.BITWISE_NOT)) {
            Operator op = this.tokenSource.expectOperator(OperatorType.BITWISE_NOT);
            return new BinaryOperationTree(parseUnary(), null, op.type());
        }
        return parseTernary();
    }

    private ExpressionTree parseTernary() {
        ExpressionTree condition = parseFactor();
        if (this.tokenSource.peek().isOperator(OperatorType.TERNARY_QUESTION)) {
            this.tokenSource.expectOperator(OperatorType.TERNARY_QUESTION);
            ExpressionTree thenExpr = parseExpression();
            this.tokenSource.expectOperator(OperatorType.TERNARY_COLON);
            ExpressionTree elseExpr = parseExpression();
            return new TernaryTree(condition, thenExpr, elseExpr, condition.span());
        }
        return condition;
    }

    private ExpressionTree parseFactor() {
        return switch (this.tokenSource.peek()) {
            case Separator(var type, _) when type == SeparatorType.PAREN_OPEN -> {
                this.tokenSource.consume();
                ExpressionTree expression = parseExpression();
                this.tokenSource.expectSeparator(SeparatorType.PAREN_CLOSE);
                yield expression;
            }
            case Identifier ident -> {
                this.tokenSource.consume();
                yield new IdentExpressionTree(name(ident));
            }
            case NumberLiteral(String value, int base, Span span) -> {
                this.tokenSource.consume();
                yield new LiteralTree(value, base, span);
            }
            case Keyword(var type, var span) when type == KeywordType.TRUE || type == KeywordType.FALSE -> {
                this.tokenSource.consume();
                yield new LiteralTree(type == KeywordType.TRUE ? "1" : "0", 10, span);
            }
            case Token t -> throw new ParseException("invalid factor " + t);
        };
    }

    private static NameTree name(Identifier ident) {
        return new NameTree(Name.forIdentifier(ident), ident.span());
    }
}
