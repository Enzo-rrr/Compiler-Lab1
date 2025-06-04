package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

public class IntegerLiteralRangeAnalysis implements NoOpVisitor<Namespace<Void>> {

    @Override
    public Unit visit(LiteralTree literalTree, Namespace<Void> data) {
        if (literalTree.value().matches("-?[0-9]+")) {
            long value = Long.parseLong(literalTree.value());
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new SemanticException("Integer literal out of range: " + literalTree.value());
            }
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ContinueTree continueTree, Namespace<Void> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BreakTree breakTree, Namespace<Void> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ForTree forTree, Namespace<Void> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(WhileTree whileTree, Namespace<Void> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(IfTree ifTree, Namespace<Void> data) {
        return Unit.INSTANCE;
    }
}
