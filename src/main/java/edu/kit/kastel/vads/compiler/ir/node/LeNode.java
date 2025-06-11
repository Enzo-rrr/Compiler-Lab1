package edu.kit.kastel.vads.compiler.ir.node;

public final class LeNode extends BinaryOperationNode { // <=
    public LeNode(Block block, Node left, Node right) {
        super(block, left, right);
    }
    @Override
    protected String info() {
        return "<=";
    }
}
