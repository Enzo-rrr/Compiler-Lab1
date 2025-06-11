//********************************************** */
package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
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
import edu.kit.kastel.vads.compiler.Span;
import edu.kit.kastel.vads.compiler.Position.SimplePosition;
import edu.kit.kastel.vads.compiler.Span.SimpleSpan;

/// Checks that functions return.
/// Currently only works for straight-line code.
public class ReturnAnalysis implements NoOpVisitor<Namespace<Boolean>> {

    //E
    //空位置”Span
    private static final Span EMPTY_SPAN = new SimpleSpan(
        new SimplePosition(0, 0),//起始位置
        new SimplePosition(0, 0)//结束位置
    );

    private static final NameTree RETURN_KEY = new NameTree(
            Name.forIdentifier(new Identifier("__return__", EMPTY_SPAN)),
            EMPTY_SPAN
    );


    //E
    @Override
    public Unit visit(ReturnTree returnTree, Namespace<Boolean> ns) {
        ns.put(RETURN_KEY, true, (a, b) -> a || b);//只要有一个 return 就标记为 true
        return Unit.INSTANCE;
    }
    // @Override
    // public Unit visit(ReturnTree returnTree, Namespace<Boolean> data) {
    //     NameTree nameTree = new NameTree(Name.forIdentifier(new Identifier("return", returnTree.span())), returnTree.span());
    //     data.put(nameTree, true, (a, b) -> a || b);
    //     return Unit.INSTANCE;
    // }

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

    // @Override
    // public Unit visit(IfTree ifTree, Namespace<Boolean> data) {
    //     // Visit condition
    //     ifTree.condition().accept(this, data);
        
    //     // Visit then branch
    //     ifTree.thenBranch().accept(this, data);
        
    //     // Visit else branch if it exists
    //     if (ifTree.elseBranch() != null) {
    //         ifTree.elseBranch().accept(this, data);
    //     }
        
    //     return Unit.INSTANCE;
    // }

    //E
    @Override
    public Unit visit(IfTree ifTree, Namespace<Boolean> ns) {
        Namespace<Boolean> thenNS = ns.enter();
        ifTree.thenBranch().accept(this, thenNS);
        boolean thenReturns = thenNS.getOrDefault(RETURN_KEY, false);

        boolean elseReturns = false;
        if (ifTree.elseBranch() != null) {
            Namespace<Boolean> elseNS = ns.enter();
            ifTree.elseBranch().accept(this, elseNS);
            elseReturns = elseNS.getOrDefault(RETURN_KEY, false);
        }

        if (thenReturns && elseReturns) {
            ns.put(RETURN_KEY, true, (a, b) -> a || b);
        }

        return Unit.INSTANCE;
    }

    // @Override
    //好像没要求每个block都要有一个return
    // public Unit visit(BlockTree blockTree, Namespace<Boolean> data) {
    //     boolean hasReturn = false;
    //     for (StatementTree statement : blockTree.statements()) {
    //         statement.accept(this, data);
    //         if (statement instanceof ReturnTree) {
    //             hasReturn = true;
    //             break;
    //         }
    //     }
        
    //     if (!hasReturn) {
    //         throw new SemanticException("Block does not return");
    //     }
        
    //     return Unit.INSTANCE;
    // }

    //E
    @Override
    public Unit visit(BlockTree blockTree, Namespace<Boolean> ns) {
        for (StatementTree stmt : blockTree.statements()) {
            stmt.accept(this, ns);

            if (Boolean.TRUE.equals(ns.get(RETURN_KEY))) {
                break;
            }
        }
        return Unit.INSTANCE;
    }


    //E
    @Override
    public Unit visit(FunctionTree functionTree, Namespace<Boolean> ns) {
        Namespace<Boolean> functionNS = ns.enter();
        functionTree.body().accept(this, functionNS);
        if (!functionNS.getOrDefault(RETURN_KEY, false)) {
            throw new SemanticException(
                "Function '" + functionTree.name() + "' may not return on all control-flow paths");
        }
        return Unit.INSTANCE;
    }
}
