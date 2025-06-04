package edu.kit.kastel.vads.compiler.ir.node;

import java.util.ArrayList;
import java.util.List;

import edu.kit.kastel.vads.compiler.ir.IrGraph;

public final class Block extends Node {
    private final List<Node> nodes = new ArrayList<>();

    public Block(IrGraph graph) {
        super(graph);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }
}
