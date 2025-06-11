package edu.kit.kastel.vads.compiler.ir.node;

public final class ConstBoolNode extends Node {
    private final boolean value;

    public ConstBoolNode(Block block, boolean value) {
        super(block);
        this.value = value;
    }

    /** Returns the boolean value carried by this node. */
    public boolean value() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ConstBoolNode other) {
            return this.value == other.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(this.value);
    }

    @Override
    protected String info() {
        return "[" + this.value + "]";
    }
}
