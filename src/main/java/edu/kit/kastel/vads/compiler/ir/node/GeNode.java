package edu.kit.kastel.vads.compiler.ir.node;

public final class GeNode extends BinaryOperationNode { // >=
    public GeNode(Block block, Node left, Node right) {
        super(block, left, right);
    }
    @Override
    protected String info() {
        return ">=";
    }
}
