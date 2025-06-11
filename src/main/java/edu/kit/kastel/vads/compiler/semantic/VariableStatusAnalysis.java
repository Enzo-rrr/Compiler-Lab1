package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/// Checks that variables are
/// - declared before assignment
/// - not declared twice
/// - not initialized twice
/// - assigned before referenced
class VariableStatusAnalysis implements NoOpVisitor<Namespace<VariableStatusAnalysis.VariableStatus>> {

    @Override
    public Unit visit(AssignmentTree assignmentTree, Namespace<VariableStatus> data) {
        switch (assignmentTree.lValue()) {
            case LValueIdentTree(var name) -> {
                VariableStatus status = data.get(name);
                checkDeclared(name, status);
                if (status != VariableStatus.INITIALIZED) {
                    // only update when needed, reassignment is totally fine
                    updateStatus(data, VariableStatus.INITIALIZED, name);
                }
            }
        }
        return NoOpVisitor.super.visit(assignmentTree, data);
    }

    private static void checkDeclared(NameTree name, @Nullable VariableStatus status) {
        if (status == null) {
            throw new SemanticException("Variable " + name + " must be declared before assignment");
        }
    }

    private static void checkInitialized(NameTree name, @Nullable VariableStatus status) {
        if (status == null || status == VariableStatus.DECLARED) {
            throw new SemanticException("Variable " + name + " must be initialized before use");
        }
    }

    private static void checkUndeclared(NameTree name, @Nullable VariableStatus status) {
        if (status != null) {
            throw new SemanticException("Variable " + name + " is already declared");
        }
    }

    @Override
    public Unit visit(DeclarationTree declarationTree, Namespace<VariableStatus> data) {
        checkUndeclared(declarationTree.name(), data.get(declarationTree.name()));
        VariableStatus status = declarationTree.initializer() == null
            ? VariableStatus.DECLARED
            : VariableStatus.INITIALIZED;
        updateStatus(data, status, declarationTree.name());
        return NoOpVisitor.super.visit(declarationTree, data);
    }

    private static void updateStatus(Namespace<VariableStatus> data, VariableStatus status, NameTree name) {
        data.put(name, status, (existing, replacement) -> {
            if (existing.ordinal() >= replacement.ordinal()) {
                throw new SemanticException("variable is already " + existing + ". Cannot be " + replacement + " here.");
            }
            return replacement;
        });
    }

    @Override
    public Unit visit(IdentExpressionTree identExpressionTree, Namespace<VariableStatus> data) {
        VariableStatus status = data.get(identExpressionTree.name());
        checkInitialized(identExpressionTree.name(), status);
        return NoOpVisitor.super.visit(identExpressionTree, data);
    }

    enum VariableStatus {
        DECLARED,
        INITIALIZED;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public Unit visit(WhileTree whileTree, Namespace<VariableStatus> ns) {
        //条件检查
        whileTree.condition().accept(this, ns);

        //进入循环体用子作用域
        Namespace<VariableStatus> bodyNs = ns.enter();
        whileTree.body().accept(this, bodyNs);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ForTree forTree, Namespace<VariableStatus> env) {
        // 为整个 for-loop 开一个新作用域
        Namespace<VariableStatus> forScope = env.enter();

        // ① initializer：可能是 int x = ...
        if (forTree.initializer() != null) {
            forTree.initializer().accept(this, forScope);
        }

        // ② condition：必须在看得见 x 的同一作用域里检查
        if (forTree.condition() != null) {
            forTree.condition().accept(this, forScope);
        }

        // ③ step：L2 规范禁止在这里写声明
        if (forTree.step() instanceof DeclarationTree) {
            throw new SemanticException("Step statement in a for-loop may not be a declaration");
        }
        if (forTree.step() != null) {
            forTree.step().accept(this, forScope);
        }

        // ④ body
        forTree.body().accept(this, forScope);

        return Unit.INSTANCE;   // 不要 env.pop() —— Namespace#enter() 已经给了隔离副本
    }



        @Override
    public Unit visit(BreakTree breakTree, Namespace<VariableStatus> ns) {
        ns.setAll(VariableStatus.INITIALIZED);   // 避免后续路径误报
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ContinueTree continueTree, Namespace<VariableStatus> ns) {
        ns.setAll(VariableStatus.INITIALIZED);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BlockTree block, Namespace<VariableStatus> env){
        Namespace<VariableStatus> inner = env.enter();
        for (var stmt : block.statements()) {
            stmt.accept(this, inner);
        }
        return Unit.INSTANCE;
    }
}
