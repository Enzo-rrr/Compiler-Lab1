package edu.kit.kastel.vads.compiler.ir.node;

public final class LtNode extends BinaryOperationNode { // <
    public LtNode(Block block, Node left, Node right) {
        super(block, left, right);
    }
    @Override
    protected String info() {
        return "<";
    }
}