//**********************************************8 */
package edu.kit.kastel.vads.compiler.ir.node;

public final class BranchNode extends Node {
    private final Node condition;

    public BranchNode(Block block, Node condition) {
        super(block, condition);
        // assert condition instanceof ConstBoolNode
        //     || condition.getResultType() == Type.BOOL
        //     : "Branch condition must be of type bool";
        this.condition = condition;
    }

    public Node condition() {
        return condition;
    }

    @Override
    protected String info() {
        return condition.toString();
    }
} 