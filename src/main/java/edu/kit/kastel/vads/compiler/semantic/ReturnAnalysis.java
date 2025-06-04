package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.visitor.NoOpVisitor;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

/// Checks that functions return.
/// Currently only works for straight-line code.
public class ReturnAnalysis implements NoOpVisitor<Namespace<Boolean>> {

    @Override
    public Unit visit(ReturnTree returnTree, Namespace<Boolean> data) {
        NameTree nameTree = new NameTree(Name.forIdentifier(new Identifier("return", returnTree.span())), returnTree.span());
        data.put(nameTree, true, (a, b) -> a || b);
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ContinueTree continueTree, Namespace<Boolean> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BreakTree breakTree, Namespace<Boolean> data) {
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(ForTree forTree, Namespace<Boolean> data) {
        // Visit initialization
        forTree.initializer().accept(this, data);
        
        // Visit condition
        forTree.condition().accept(this, data);
        
        // Visit body
        forTree.body().accept(this, data);
        
        // Visit step
        forTree.step().accept(this, data);
        
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(WhileTree whileTree, Namespace<Boolean> data) {
        // Visit condition
        whileTree.condition().accept(this, data);
        
        // Visit body
        whileTree.body().accept(this, data);
        
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(IfTree ifTree, Namespace<Boolean> data) {
        // Visit condition
        ifTree.condition().accept(this, data);
        
        // Visit then branch
        ifTree.thenBranch().accept(this, data);
        
        // Visit else branch if it exists
        if (ifTree.elseBranch() != null) {
            ifTree.elseBranch().accept(this, data);
        }
        
        return Unit.INSTANCE;
    }

    @Override
    public Unit visit(BlockTree blockTree, Namespace<Boolean> data) {
        boolean hasReturn = false;
        for (StatementTree statement : blockTree.statements()) {
            statement.accept(this, data);
            if (statement instanceof ReturnTree) {
                hasReturn = true;
                break;
            }
        }
        
        if (!hasReturn) {
            throw new SemanticException("Block does not return");
        }
        
        return Unit.INSTANCE;
    }
}
