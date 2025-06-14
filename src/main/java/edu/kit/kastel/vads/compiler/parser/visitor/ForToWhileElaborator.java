package edu.kit.kastel.vads.compiler.parser.visitor;

import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.parser.ast.*;
import java.util.List;

/**
 * 把所有 ForTree 展开成：
 *   Seq(
 *     initializer,
 *     While(cond,
 *       Seq(body, step)
 *     )
 *   )
 * 其它节点原样返回。
 */
public class ForToWhileElaborator implements Visitor<Void, StatementTree> {
    private final RecursivePostorderVisitor<Void, StatementTree> recur;

    public ForToWhileElaborator() {
        this.recur = new RecursivePostorderVisitor<>(this);
    }

    public StatementTree transform(ProgramTree prog) {
        return prog.accept(recur, null);
    }

    //for展开
    @Override
    public StatementTree visit(ForTree f, Void ctx) {
        StatementTree init = f.initializer() != null
            ? (StatementTree) f.initializer().accept(recur, null)
            : new BlockTree(List.of(), f.span());
        ExpressionTree cond = f.condition();
        StatementTree step = f.step() != null
            ? (StatementTree) f.step().accept(recur, null)
            : new BlockTree(List.of(), f.span());
        StatementTree body = (StatementTree) f.body().accept(recur, null);

        Span bsSpan = body.span().merge(step.span());
        BlockTree bodyThenStep = new BlockTree(List.of(body, step), bsSpan);

        Span wSpan = cond.span().merge(bodyThenStep.span());
        StatementTree whileLoop = new WhileTree(cond, bodyThenStep, wSpan);

        Span seqSpan = init.span().merge(whileLoop.span());
        return new BlockTree(List.of(init, whileLoop), seqSpan);
    }

    @Override public StatementTree visit(AssignmentTree t, Void ctx) { return t; }
    @Override public StatementTree visit(DeclarationTree t, Void ctx) { return t; }
    @Override public StatementTree visit(IfTree t, Void ctx) { return t; }
    @Override public StatementTree visit(WhileTree t, Void ctx) { return t; }
    @Override public StatementTree visit(BreakTree t, Void ctx) { return t; }
    @Override public StatementTree visit(ContinueTree t, Void ctx) { return t; }
    @Override public StatementTree visit(ReturnTree t, Void ctx) { return t; }
    @Override public StatementTree visit(BlockTree t, Void ctx) { return t; }
    @Override public StatementTree visit(ProgramTree t, Void ctx) { return t; }

    @Override public StatementTree visit(BinaryOperationTree t, Void ctx) { return t; }
    @Override public StatementTree visit(NegateTree t, Void ctx) { return t; }
    @Override public StatementTree visit(IdentExpressionTree t, Void ctx) { return t; }
    @Override public StatementTree visit(LiteralTree t, Void ctx) { return t; }
    @Override public StatementTree visit(LValueIdentTree t, Void ctx) { return t; }
    @Override public StatementTree visit(NameTree t, Void ctx) { return t; }
    @Override public StatementTree visit(FunctionTree t, Void ctx) { return t; }
    @Override public StatementTree visit(TypeTree t, Void ctx) { return t; }
    @Override public StatementTree visit(TernaryTree t, Void ctx) { return t; }
}
