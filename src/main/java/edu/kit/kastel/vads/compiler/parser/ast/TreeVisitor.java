//********************************************** */
package edu.kit.kastel.vads.compiler.parser.ast;

public interface TreeVisitor<R> {
    R visitProgram(ProgramTree tree);
    R visitFunction(FunctionTree tree);
    R visitBlock(BlockTree tree);
    R visitDeclaration(DeclarationTree tree);
    R visitAssignment(AssignmentTree tree);
    R visitReturn(ReturnTree tree);
    R visitBinaryOperation(BinaryOperationTree tree);
    R visitNegate(NegateTree tree);
    R visitIdentExpression(IdentExpressionTree tree);
    R visitLiteral(LiteralTree tree);
    R visitIf(IfTree tree);
    R visitWhile(WhileTree tree);
    R visitFor(ForTree tree);
    R visitBreak(BreakTree tree);
    R visitContinue(ContinueTree tree);
    R visitTernary(TernaryTree tree);
} 