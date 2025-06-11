package edu.kit.kastel.vads.compiler.ir.node;

public final class GtNode extends BinaryOperationNode { // >
    public GtNode(Block block, Node left, Node right) {
        super(block, left, right);
    }
    @Override
    protected String info() {
        return ">";
    }
}
