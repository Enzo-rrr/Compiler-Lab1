//**********************************************8 */
package edu.kit.kastel.vads.compiler.ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.BinaryOperator;

import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.optimize.Optimizer;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfo;
import edu.kit.kastel.vads.compiler.ir.util.DebugInfoHelper;
import edu.kit.kastel.vads.compiler.parser.ast.AssignmentTree;
import edu.kit.kastel.vads.compiler.parser.ast.BinaryOperationTree;
import edu.kit.kastel.vads.compiler.parser.ast.BlockTree;
import edu.kit.kastel.vads.compiler.parser.ast.BreakTree;
import edu.kit.kastel.vads.compiler.parser.ast.ContinueTree;
import edu.kit.kastel.vads.compiler.parser.ast.DeclarationTree;
import edu.kit.kastel.vads.compiler.parser.ast.ForTree;
import edu.kit.kastel.vads.compiler.parser.ast.FunctionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IdentExpressionTree;
import edu.kit.kastel.vads.compiler.parser.ast.IfTree;
import edu.kit.kastel.vads.compiler.parser.ast.LValueIdentTree;
import edu.kit.kastel.vads.compiler.parser.ast.LiteralTree;
import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.ast.NegateTree;
import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.ast.ReturnTree;
import edu.kit.kastel.vads.compiler.parser.ast.StatementTree;
import edu.kit.kastel.vads.compiler.parser.ast.TernaryTree;
import edu.kit.kastel.vads.compiler.parser.ast.Tree;
import edu.kit.kastel.vads.compiler.parser.ast.TypeTree;
import edu.kit.kastel.vads.compiler.parser.ast.WhileTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import edu.kit.kastel.vads.compiler.parser.visitor.Visitor;
import edu.kit.kastel.vads.compiler.semantic.SemanticException;

/// SSA translation as described in
/// [`Simple and Efficient Construction of Static Single Assignment Form`](https://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf).
///
/// This implementation also tracks side effect edges that can be used to avoid reordering of operations that cannot be
/// reordered.
///
/// We recommend to read the paper to better understand the mechanics implemented here.
public class SsaTranslation {
    private final FunctionTree function;
    private final GraphConstructor constructor;

    public SsaTranslation(FunctionTree function, Optimizer optimizer) {
        this.function = function;
        this.constructor = new GraphConstructor(optimizer, function.name().name().asString());
    }

    public IrGraph translate() {
        var visitor = new SsaTranslationVisitor();
        this.function.accept(visitor, this);
        return this.constructor.graph();
    }

    private void writeVariable(Name variable, Block block, Node value) {
        this.constructor.writeVariable(variable, block, value);
    }

    private Node readVariable(Name variable, Block block) {
        return this.constructor.readVariable(variable, block);
    }

    private Block currentBlock() {
        return this.constructor.currentBlock();
    }

    private static class SsaTranslationVisitor implements Visitor<SsaTranslation, Optional<Node>> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static final Optional<Node> NOT_AN_EXPRESSION = Optional.empty();

        private final Deque<DebugInfo> debugStack = new ArrayDeque<>();
        
        // Loop context stack for tracking nested loops
        private final Deque<LoopContext> loopStack = new ArrayDeque<>();
        
        // Simple class to hold loop context information
        private static class LoopContext {
            final Block headerBlock;
            final Block exitBlock;
            
            LoopContext(Block headerBlock, Block exitBlock) {
                this.headerBlock = headerBlock;
                this.exitBlock = exitBlock;
            }
        }

        private void pushSpan(Tree tree) {
            this.debugStack.push(DebugInfoHelper.getDebugInfo());
            DebugInfoHelper.setDebugInfo(new DebugInfo.SourceInfo(tree.span()));
        }

        private void popSpan() {
            DebugInfoHelper.setDebugInfo(this.debugStack.pop());
        }

        @Override
        public Optional<Node> visit(AssignmentTree assignmentTree, SsaTranslation data) {
            pushSpan(assignmentTree);
            BinaryOperator<Node> desugar = switch (assignmentTree.operator().type()) {
                case ASSIGN_MINUS -> data.constructor::newSub;
                case ASSIGN_PLUS -> data.constructor::newAdd;
                case ASSIGN_MUL -> data.constructor::newMul;
                case ASSIGN_DIV -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case ASSIGN_MOD -> (lhs, rhs) -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case ASSIGN_AND -> data.constructor::newBitAnd;
                case ASSIGN_XOR -> data.constructor::newBitXor;
                case ASSIGN_OR -> data.constructor::newBitOr;
                case ASSIGN_SHIFT_LEFT -> data.constructor::newShiftLeft;
                case ASSIGN_SHIFT_RIGHT ->data.constructor::newShiftRight;
                case ASSIGN -> null;
                default ->
                    throw new IllegalArgumentException("not an assignment operator " + assignmentTree.operator());
            };

            switch (assignmentTree.lValue()) {
                case LValueIdentTree(var name) -> {
                    Node rhs = assignmentTree.expression().accept(this, data).orElseThrow();
                    if (desugar != null) {
                        rhs = desugar.apply(data.readVariable(name.name(), data.currentBlock()), rhs);
                    }
                    data.writeVariable(name.name(), data.currentBlock(), rhs);
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BinaryOperationTree binaryOperationTree, SsaTranslation data) {
            pushSpan(binaryOperationTree);
            Node lhs = binaryOperationTree.lhs().accept(this, data).orElseThrow();
            Node rhs = binaryOperationTree.rhs().accept(this, data).orElseThrow();
            Node res = switch (binaryOperationTree.operatorType()) {
                case MINUS -> data.constructor.newSub(lhs, rhs);
                case PLUS -> data.constructor.newAdd(lhs, rhs);
                case MUL -> data.constructor.newMul(lhs, rhs);
                case DIV -> projResultDivMod(data, data.constructor.newDiv(lhs, rhs));
                case MOD -> projResultDivMod(data, data.constructor.newMod(lhs, rhs));
                case LESS -> data.constructor.newLt(lhs, rhs);
                case LESS_EQUAL -> data.constructor.newLe(lhs, rhs);
                case GREATER -> data.constructor.newGt(lhs, rhs);
                case GREATER_EQUAL -> data.constructor.newGe(lhs, rhs);
                case EQUAL -> data.constructor.newEq(lhs, rhs);
                case NOT_EQUAL -> data.constructor.newNe(lhs, rhs);

                case BITWISE_AND -> data.constructor.newBitAnd(lhs, rhs);
                case BITWISE_OR  -> data.constructor.newBitOr(lhs, rhs);
                case BITWISE_XOR -> data.constructor.newBitXor(lhs, rhs);

                case SHIFT_LEFT -> data.constructor.newShiftLeft(lhs, rhs);
                case SHIFT_RIGHT -> data.constructor.newShiftRight(lhs, rhs);

                default -> throw new IllegalArgumentException("not a binary expression operator " + binaryOperationTree.operatorType());
            };
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(BlockTree blockTree, SsaTranslation data) {
            pushSpan(blockTree);
            for (StatementTree statement : blockTree.statements()) {
                statement.accept(this, data);
                // skip everything after a return in a block
                if (statement instanceof ReturnTree) {
                    break;
                }
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(DeclarationTree declarationTree, SsaTranslation data) {
            pushSpan(declarationTree);
            if (declarationTree.initializer() != null) {
                Node rhs = declarationTree.initializer().accept(this, data).orElseThrow();
                data.writeVariable(declarationTree.name().name(), data.currentBlock(), rhs);
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(FunctionTree functionTree, SsaTranslation data) {
            pushSpan(functionTree);
            Node start = data.constructor.newStart();
            data.constructor.writeCurrentSideEffect(data.constructor.newSideEffectProj(start));
            functionTree.body().accept(this, data);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(IdentExpressionTree identExpressionTree, SsaTranslation data) {
            pushSpan(identExpressionTree);
            Node value = data.readVariable(identExpressionTree.name().name(), data.currentBlock());
            popSpan();
            return Optional.of(value);
        }

        @Override
        public Optional<Node> visit(LiteralTree lit, SsaTranslation data) {
            pushSpan(lit);

            Object value = lit.parseValue().orElseThrow();
            Node node;
            if (value instanceof Boolean boolVal) {
                node = data.constructor.newConstBool(boolVal);
            } else {
                node = data.constructor.newConstInt(((Number) value).intValue());
            }

            popSpan();
            return Optional.of(node);
        }



        @Override
        public Optional<Node> visit(LValueIdentTree lValueIdentTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NameTree nameTree, SsaTranslation data) {
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(NegateTree negateTree, SsaTranslation data) {
            pushSpan(negateTree);
            Node node = negateTree.expression().accept(this, data).orElseThrow();
            Node res = data.constructor.newSub(data.constructor.newConstInt(0), node);
            popSpan();
            return Optional.of(res);
        }

        @Override
        public Optional<Node> visit(ProgramTree programTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(ReturnTree returnTree, SsaTranslation data) {
            pushSpan(returnTree);
            // 计算返回值
            Node value = returnTree.expression()
                                    .accept(this, data)
                                    .orElseThrow();

            // 创建 ReturnNode挂到 currentBlock
            Node ret = data.constructor.newReturn(value);

            // 连CFG正向边：ret → endBlock
            data.constructor.graph()
                .registerSuccessor(ret, data.constructor.graph().endBlock());

            popSpan();
            return NOT_AN_EXPRESSION;
        }


        @Override
        public Optional<Node> visit(TypeTree typeTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(TernaryTree tern, SsaTranslation d) {
            pushSpan(tern);

            // evaluate condition first
            Node cond = tern.condition().accept(this, d).orElseThrow();
            //Block cur   = d.currentBlock();
            Block thenB = new Block(d.constructor.graph());
            Block elseB = new Block(d.constructor.graph());
            Block merge = new Block(d.constructor.graph());

            // branch based on condition (direction fixed inside newBranch)
            d.constructor.newBranch(cond, thenB, elseB);

            /* then branch */
            d.constructor.setCurrentBlock(thenB);
            Node thenVal = tern.thenExpr().accept(this, d).orElseThrow();
            d.constructor.newJump(merge);

            /* else branch */
            d.constructor.setCurrentBlock(elseB);
            Node elseVal = tern.elseExpr().accept(this, d).orElseThrow();
            d.constructor.newJump(merge);

            /* merge & phi */
            d.constructor.setCurrentBlock(merge);
            Phi phi = d.constructor.newPhi();
            phi.appendOperand(thenVal);
            phi.appendOperand(elseVal);

            popSpan();
            return Optional.of(phi);
        }

        @Override
        public Optional<Node> visit(IfTree t, SsaTranslation d) {
            pushSpan(t);
            Node cond = t.condition().accept(this, d).orElseThrow();

            Block thenB = new Block(d.constructor.graph());
            Block elseB = new Block(d.constructor.graph());
            Block merge = new Block(d.constructor.graph());

            d.constructor.newBranch(cond, thenB, elseB);

            // then branch
            d.constructor.setCurrentBlock(thenB);
            t.thenBranch().accept(this, d);
            if (!(t.thenBranch() instanceof ReturnTree)) {
                d.constructor.newJump(merge);
            }

            // else branch (may be null)
            d.constructor.setCurrentBlock(elseB);
            if (t.elseBranch() != null) {
                t.elseBranch().accept(this, d);
                if (!(t.elseBranch() instanceof ReturnTree)) {
                    d.constructor.newJump(merge);
                }
            } else {
                d.constructor.newJump(merge);
            }

            d.constructor.setCurrentBlock(merge);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(WhileTree w, SsaTranslation d) {
            pushSpan(w);
            Block header = new Block(d.constructor.graph());
            Block body   = new Block(d.constructor.graph());
            Block exit   = new Block(d.constructor.graph());

            loopStack.push(new LoopContext(header, exit));

            d.constructor.newJump(header);               // from current → header
            d.constructor.setCurrentBlock(header);

            Node cond = w.condition().accept(this, d).orElseThrow();
            d.constructor.newBranch(cond, body, exit);

            d.constructor.setCurrentBlock(body);
            w.body().accept(this, d);
            d.constructor.newJump(header);

            loopStack.pop();
            d.constructor.setCurrentBlock(exit);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ForTree f, SsaTranslation d) {
            pushSpan(f);
            Block init  = new Block(d.constructor.graph());
            Block head  = new Block(d.constructor.graph());
            Block body  = new Block(d.constructor.graph());
            Block step  = new Block(d.constructor.graph());
            Block exit  = new Block(d.constructor.graph());

            loopStack.push(new LoopContext(head, exit));

            d.constructor.newJump(init);
            d.constructor.setCurrentBlock(init);
            if (f.initializer() != null) f.initializer().accept(this, d);
            d.constructor.newJump(head);

            d.constructor.setCurrentBlock(head);
            Node cond = f.condition().accept(this, d).orElseThrow();
            d.constructor.newBranch(cond, body, exit);

            d.constructor.setCurrentBlock(body);
            f.body().accept(this, d);
            d.constructor.newJump(step);

            d.constructor.setCurrentBlock(step);
            if (f.step() != null) f.step().accept(this, d);
            d.constructor.newJump(head);

            loopStack.pop();
            d.constructor.setCurrentBlock(exit);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BreakTree b, SsaTranslation d) {
            if (loopStack.isEmpty()) throw new SemanticException("break outside loop");
            LoopContext lc = loopStack.peek();
            d.constructor.newJump(lc.exitBlock);
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ContinueTree c, SsaTranslation d) {
            if (loopStack.isEmpty()) throw new SemanticException("continue outside loop");
            LoopContext lc = loopStack.peek();
            d.constructor.newJump(lc.headerBlock);
            return NOT_AN_EXPRESSION;
        }

        private Node projResultDivMod(SsaTranslation data, Node divMod) {
            // make sure we actually have a div or a mod, as optimizations could
            // have changed it to something else already
            if (!(divMod instanceof DivNode || divMod instanceof ModNode)) {
                return divMod;
            }
            Node projSideEffect = data.constructor.newSideEffectProj(divMod);
            data.constructor.writeCurrentSideEffect(projSideEffect);
            return data.constructor.newResultProj(divMod);
        }

        // private Node calculatePowerOfTwo(SsaTranslation data, Node rhs) {
        //     if (rhs instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constNode) {
        //         // Extract the constant value for power calculation
        //         int constantValue = constNode.value();
        //         return data.constructor.newConstInt(1 << constantValue);
        //     } else {
        //         // For variable shift amounts, this is more complex
        //         // Simplified: treat as x * (2 * rhs) which is not mathematically correct
        //         // but at least closer than our previous implementation
        //         Node two = data.constructor.newConstInt(2);
        //         Node powerApprox = data.constructor.newMul(two, rhs);
        //         return data.constructor.newMul(data.constructor.newConstInt(2), rhs);
        //     }
        // }
    }
}
