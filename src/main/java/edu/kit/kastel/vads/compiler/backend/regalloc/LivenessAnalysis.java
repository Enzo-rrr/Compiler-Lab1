package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;

import java.util.*;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class LivenessAnalysis {
    private final Map<Node, Set<Node>> liveAfter = new HashMap<>();
    private final Map<Node, Set<Node>> liveBefore = new HashMap<>();
    
    public void analyze(IrGraph graph) {
        // 初始化所有节点的活跃集合initial liveness
        Set<Node> allNodes = new HashSet<>();
        collectNodes(graph.endBlock(), allNodes);
        
        for (Node node : allNodes) {
            liveAfter.put(node, new HashSet<>());
            liveBefore.put(node, new HashSet<>());
        }
        
        // 从末尾向前遍历bottom-up analysis
        Set<Node> visited = new HashSet<>();
        analyzeNode(graph.endBlock(), visited);
    }
    
    private void collectNodes(Node node, Set<Node> nodes) {
        if (nodes.add(node)) {
            for (Node predecessor : node.predecessors()) {
                collectNodes(predecessor, nodes);
            }
        }
    }
    
    private void analyzeNode(Node node, Set<Node> visited) {
        if (!visited.add(node)) {
            return;
        }
        Set<Node> live = new HashSet<>(liveAfter.get(node));
        // 移除定义变量kill
        live.removeAll(defs(node));
        // 添加gen
        live.addAll(uses(node));
        // 更新当前节点的活跃变量successor
        liveBefore.put(node, live);
        // 更新前驱节点的活跃变量predecessor
        for (Node pred : node.predecessors()) {
            liveAfter.put(pred, new HashSet<>(live));
            analyzeNode(pred, visited);
        }
    }
    
    private Set<Node> defs(Node node) {
        Set<Node> defs = new HashSet<>();
        if (node instanceof BinaryOperationNode) {
            defs.add(node);
        }
        return defs;
    }
    
    private Set<Node> uses(Node node) {
        Set<Node> uses = new HashSet<>();
        if (node instanceof BinaryOperationNode) {
            uses.add(predecessorSkipProj(node, BinaryOperationNode.LEFT));
            uses.add(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
        } else if (node instanceof ReturnNode) {
            // return expr, uses value
            uses.add(predecessorSkipProj(node, ReturnNode.RESULT));
        }
        return uses;
    }
    
    public Set<Node> getLiveAfter(Node node) {
        return liveAfter.getOrDefault(node, Collections.emptySet());
    }
    
    public Set<Node> getLiveBefore(Node node) {
        return liveBefore.getOrDefault(node, Collections.emptySet());
    }
} 