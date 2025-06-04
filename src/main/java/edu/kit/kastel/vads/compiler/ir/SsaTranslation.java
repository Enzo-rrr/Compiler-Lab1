package edu.kit.kastel.vads.compiler.ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;

import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.BranchNode;
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
                case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL, EQUAL, NOT_EQUAL -> {
                    // For comparison operators, create a subtraction and return it
                    // The actual comparison will be handled by the BranchNode in CodeGenerator
                    Node diff = data.constructor.newSub(lhs, rhs);
                    yield diff;
                }
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
        public Optional<Node> visit(LiteralTree literalTree, SsaTranslation data) {
            pushSpan(literalTree);
            Node node = data.constructor.newConstInt((int) literalTree.parseValue().orElseThrow());
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
            Node node = returnTree.expression().accept(this, data).orElseThrow();
            Node ret = data.constructor.newReturn(node);
            // 将 ReturnNode 连接到 endBlock 用于遍历，但也要确保它在当前块中
            data.constructor.graph().endBlock().addPredecessor(ret);
            // 将 ReturnNode 添加到当前块中，确保块包含它的内容
            data.currentBlock().addPredecessor(ret);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(TypeTree typeTree, SsaTranslation data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Node> visit(TernaryTree ternaryTree, SsaTranslation data) {
            pushSpan(ternaryTree);
            Node condition = ternaryTree.condition().accept(this, data).orElseThrow();
            Node thenExpr = ternaryTree.thenExpr().accept(this, data).orElseThrow();
            Node elseExpr = ternaryTree.elseExpr().accept(this, data).orElseThrow();
            
            // Create a phi node for the result
            Phi phi = data.constructor.newPhi();
            phi.appendOperand(thenExpr);
            phi.appendOperand(elseExpr);
            
            popSpan();
            return Optional.of(phi);
        }

        @Override
        public Optional<Node> visit(IfTree ifTree, SsaTranslation data) {
            pushSpan(ifTree);
            Node condition = ifTree.condition().accept(this, data).orElseThrow();
            
            Block currentBlock = data.currentBlock();
            Block thenBlock = new Block(data.constructor.graph());
            Block elseBlock = new Block(data.constructor.graph());
            Block mergeBlock = new Block(data.constructor.graph());
            
            // Create branch
            Node branch = data.constructor.newBranch(condition, thenBlock, elseBlock);
            
            // Connect the branch to endBlock so it can be reached during traversal
            data.constructor.graph().endBlock().addPredecessor(branch);
            
            // Set up block predecessor relationships
            thenBlock.addPredecessor(currentBlock);
            elseBlock.addPredecessor(currentBlock);
            
            // Visit then branch
            data.constructor.setCurrentBlock(thenBlock);
            ifTree.thenBranch().accept(this, data);
            // Only jump to merge block if we haven't returned
            if (!(ifTree.thenBranch() instanceof ReturnTree)) {
                data.constructor.newJump(mergeBlock);
                mergeBlock.addPredecessor(thenBlock);
            }
            
            // Visit else branch (if it exists)
            data.constructor.setCurrentBlock(elseBlock);
            if (ifTree.elseBranch() != null) {
                ifTree.elseBranch().accept(this, data);
                // Only jump to merge block if we haven't returned
                if (!(ifTree.elseBranch() instanceof ReturnTree)) {
                    data.constructor.newJump(mergeBlock);
                    mergeBlock.addPredecessor(elseBlock);
                }
            } else {
                // No else branch, just jump to merge
                data.constructor.newJump(mergeBlock);
                mergeBlock.addPredecessor(elseBlock);
            }
            
            // Seal blocks now that their predecessors are known
            data.constructor.sealBlock(thenBlock);
            data.constructor.sealBlock(elseBlock);
            data.constructor.sealBlock(mergeBlock);
            
            // Set merge block as current only if we need it
            if (!(ifTree.thenBranch() instanceof ReturnTree) || 
                (ifTree.elseBranch() != null && !(ifTree.elseBranch() instanceof ReturnTree))) {
                data.constructor.setCurrentBlock(mergeBlock);
            }
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(WhileTree whileTree, SsaTranslation data) {
            pushSpan(whileTree);
            Block headerBlock = new Block(data.constructor.graph());
            Block bodyBlock = new Block(data.constructor.graph());
            Block exitBlock = new Block(data.constructor.graph());
            
            // Jump to header
            data.constructor.newJump(headerBlock);
            
            // Visit header
            data.constructor.setCurrentBlock(headerBlock);
            Node condition = whileTree.condition().accept(this, data).orElseThrow();
            data.constructor.newBranch(condition, bodyBlock, exitBlock);
            
            // Visit body
            data.constructor.setCurrentBlock(bodyBlock);
            whileTree.body().accept(this, data);
            data.constructor.newJump(headerBlock);
            
            // Set exit block as current
            data.constructor.setCurrentBlock(exitBlock);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ForTree forTree, SsaTranslation data) {
            pushSpan(forTree);
            
            Block currentBlock = data.currentBlock();
            Block initBlock = new Block(data.constructor.graph());
            Block headerBlock = new Block(data.constructor.graph());
            Block bodyBlock = new Block(data.constructor.graph());
            Block stepBlock = new Block(data.constructor.graph());
            Block exitBlock = new Block(data.constructor.graph());
            
            // Jump to initialization
            data.constructor.newJump(initBlock);
            initBlock.addPredecessor(currentBlock);
            
            // Visit initialization
            data.constructor.setCurrentBlock(initBlock);
            if (forTree.initializer() != null) {
                forTree.initializer().accept(this, data);
            }
            data.constructor.newJump(headerBlock);
            headerBlock.addPredecessor(initBlock);
            // Seal init block since it has only one predecessor
            data.constructor.sealBlock(initBlock);
            
            // Visit header (condition)
            data.constructor.setCurrentBlock(headerBlock);
            Node condition = forTree.condition().accept(this, data).orElseThrow();
            Node branch = data.constructor.newBranch(condition, bodyBlock, exitBlock);
            data.constructor.graph().endBlock().addPredecessor(branch);
            bodyBlock.addPredecessor(headerBlock);
            exitBlock.addPredecessor(headerBlock);
            
            // Visit body
            data.constructor.setCurrentBlock(bodyBlock);
            forTree.body().accept(this, data);
            data.constructor.newJump(stepBlock);
            stepBlock.addPredecessor(bodyBlock);
            // Seal body block since it has only one predecessor (from header)
            data.constructor.sealBlock(bodyBlock);
            
            // Visit step
            data.constructor.setCurrentBlock(stepBlock);
            if (forTree.step() != null) {
                forTree.step().accept(this, data);
            }
            data.constructor.newJump(headerBlock);
            headerBlock.addPredecessor(stepBlock);
            // Seal step block since it has only one predecessor (from body)
            data.constructor.sealBlock(stepBlock);
            
            // Now seal header block since all its predecessors are known (init and step)
            data.constructor.sealBlock(headerBlock);
            
            // Set exit block as current
            data.constructor.setCurrentBlock(exitBlock);
            // Seal exit block since it has only one predecessor (from header)
            data.constructor.sealBlock(exitBlock);
            
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(BreakTree breakTree, SsaTranslation data) {
            pushSpan(breakTree);
            // Find the nearest loop exit block
            Block currentBlock = data.currentBlock();
            Block exitBlock = findLoopExitBlock(currentBlock);
            if (exitBlock == null) {
                throw new SemanticException("Break statement outside of loop");
            }
            data.constructor.newJump(exitBlock);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        @Override
        public Optional<Node> visit(ContinueTree continueTree, SsaTranslation data) {
            pushSpan(continueTree);
            // Find the nearest loop header block
            Block currentBlock = data.currentBlock();
            Block headerBlock = findLoopHeaderBlock(currentBlock);
            if (headerBlock == null) {
                throw new SemanticException("Continue statement outside of loop");
            }
            data.constructor.newJump(headerBlock);
            popSpan();
            return NOT_AN_EXPRESSION;
        }

        private Block findLoopExitBlock(Block block) {
            return findLoopExitBlockRecursive(block, new HashSet<>());
        }

        private Block findLoopExitBlockRecursive(Block block, Set<Block> visited) {
            if (visited.contains(block)) {
                return null;
            }
            visited.add(block);

            // Check if this block has a branch node that leads to a block outside the loop
            for (Node node : block.nodes()) {
                if (node instanceof BranchNode branch) {
                    // Check both branches
                    for (Node pred : branch.predecessors()) {
                        Block predBlock = pred.block();
                        if (!visited.contains(predBlock)) {
                            return predBlock;
                        }
                    }
                }
            }

            // Recursively check predecessor blocks
            for (Node pred : block.predecessors()) {
                Block predBlock = pred.block();
                Block exitBlock = findLoopExitBlockRecursive(predBlock, visited);
                if (exitBlock != null) {
                    return exitBlock;
                }
            }

            return null;
        }

        private Block findLoopHeaderBlock(Block block) {
            return findLoopHeaderBlockRecursive(block, new HashSet<>());
        }

        private Block findLoopHeaderBlockRecursive(Block block, Set<Block> visited) {
            if (visited.contains(block)) {
                return block; // Found a cycle, this is the header
            }
            visited.add(block);

            // Check predecessor blocks
            for (Node pred : block.predecessors()) {
                Block predBlock = pred.block();
                Block headerBlock = findLoopHeaderBlockRecursive(predBlock, visited);
                if (headerBlock != null) {
                    return headerBlock;
                }
            }

            return null;
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
    }
}
