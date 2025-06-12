package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

/// Checks that variables are
/// - declared before assignment
/// - not declared twice
/// - not initialized twice
/// - assigned before referenced
public class VariableStatusAnalysis implements NoOpVisitor<Namespace<VariableStatusAnalysis.VariableStatus>> {

    public enum VariableStatus {
        UNINITIALIZED,
        DEFINED,
        DEFINED_AND_USED
    }

    @Override
    public Unit visit(AssignmentTree assignmentTree, Namespace<VariableStatus> data) {
        if (assignmentTree.lValue() instanceof LValueIdentTree lValue) {
            data.put(lValue.name(), VariableStatus.DEFINED, (existing, replacement) -> replacement);
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(DeclarationTree declarationTree, Namespace<VariableStatus> data) {
        if (declarationTree.initializer() != null) {
            data.put(declarationTree.name(), VariableStatus.DEFINED, (existing, replacement) -> replacement);
        } else {
            data.put(declarationTree.name(), VariableStatus.UNINITIALIZED, (existing, replacement) -> replacement);
        }
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(IdentExpressionTree identExpressionTree, Namespace<VariableStatus> data) {
        VariableStatus status = data.get(identExpressionTree.name());
        if (status == null) {
            throw new SemanticException("Undefined variable: " + identExpressionTree.name().name());
        }
        if (status == VariableStatus.UNINITIALIZED) {
            throw new SemanticException("Uninitialized variable: " + identExpressionTree.name().name());
        }
        data.put(identExpressionTree.name(), VariableStatus.DEFINED_AND_USED, (existing, replacement) -> replacement);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ContinueTree continueTree, Namespace<VariableStatus> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BreakTree breakTree, Namespace<VariableStatus> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ForTree forTree, Namespace<VariableStatus> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(WhileTree whileTree, Namespace<VariableStatus> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(IfTree ifTree, Namespace<VariableStatus> data) {
        return Unit.INSTANCE;
    }
}
