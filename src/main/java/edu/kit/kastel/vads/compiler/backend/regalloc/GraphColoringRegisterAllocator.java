package edu.kit.kastel.vads.compiler.backend.regalloc;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;

import java.util.*;


public class GraphColoringRegisterAllocator implements RegisterAllocator {
    // x86-64 style
    private static final List<PhysicalRegister> DEFAULT_PHYSICAL_REGISTERS = Arrays.asList(
        new PhysicalRegister("ebx", 0),  
        new PhysicalRegister("ecx", 1),  
        new PhysicalRegister("edx", 2),  
        new PhysicalRegister("esi", 3),  
        new PhysicalRegister("edi", 4),  
        new PhysicalRegister("r8d", 5),  
        new PhysicalRegister("r9d", 6),  
        new PhysicalRegister("r10d", 7), 
        new PhysicalRegister("r11d", 8),
        new PhysicalRegister("r12d", 9),
        new PhysicalRegister("r13d", 10), 
        new PhysicalRegister("r14d", 11), 
        new PhysicalRegister("r15d", 12)  
    );
    
    private final List<PhysicalRegister> physicalRegisters;
    private IrGraph graph;
    private LivenessAnalysis liveness;
    private int stackOffset = 0; 
    
    /**
      @param physicalRegisters The list of available physical registers
     */
    public GraphColoringRegisterAllocator(List<PhysicalRegister> physicalRegisters) {
        this.physicalRegisters = physicalRegisters != null ? physicalRegisters : DEFAULT_PHYSICAL_REGISTERS;
    }
    
    @Override
    public Map<Node, Register> allocateRegisters(IrGraph graph) {
        this.graph = graph;
        this.liveness = new LivenessAnalysis();
        this.liveness.analyze(graph);
        this.stackOffset = 0;  
        
        Map<Node, Set<Node>> interferenceGraph = new HashMap<>();
        Set<Node> allNodes = new HashSet<>();
        collectNodes(graph.endBlock(), allNodes);
        
        for (Node node : allNodes) {
            interferenceGraph.put(node, new HashSet<>());
        }
        
        for (Node node1 : allNodes) {
            for (Node node2 : allNodes) {
                if (node1 != node2 && mayInterfere(node1, node2)) {
                    interferenceGraph.get(node1).add(node2);
                    interferenceGraph.get(node2).add(node1);
                }
            }
        }
        
        List<Node> orderedNodes = maximumCardinalitySearch(interferenceGraph, allNodes);
       
        Map<Node, Register> allocation = greedyColoring(interferenceGraph, orderedNodes);
        
        return allocation;
    }
    
    private List<Node> maximumCardinalitySearch(Map<Node, Set<Node>> interferenceGraph, Set<Node> allNodes) {
        Map<Node, Integer> weights = new HashMap<>();
        for (Node node : allNodes) {
            weights.put(node, 0);
        }
        
        Set<Node> unprocessed = new HashSet<>(allNodes);
        List<Node> ordering = new ArrayList<>();
        
        while (!unprocessed.isEmpty()) {
            Node maxWeightNode = null;
            int maxWeight = -1;
            for (Node node : unprocessed) {
                int weight = weights.get(node);
                if (weight > maxWeight) {
                    maxWeight = weight;
                    maxWeightNode = node;
                }
            }
            
            ordering.add(maxWeightNode);
            
            for (Node neighbor : interferenceGraph.get(maxWeightNode)) {
                if (unprocessed.contains(neighbor)) {
                    weights.put(neighbor, weights.get(neighbor) + 1);
                }
            }
            
            unprocessed.remove(maxWeightNode);
        }
        
        return ordering;
    }
    
    private Map<Node, Register> greedyColoring(Map<Node, Set<Node>> interferenceGraph, List<Node> orderedNodes) {
        Map<Node, Register> allocation = new HashMap<>();
        
        for (Node node : orderedNodes) {
            Set<Register> usedColors = new HashSet<>();
            for (Node neighbor : interferenceGraph.get(node)) {
                Register color = allocation.get(neighbor);
                if (color != null) {
                    usedColors.add(color);
                }
            }
            
            Register color = null;
            for (PhysicalRegister reg : physicalRegisters) {
                if (!usedColors.contains(reg)) {
                    color = reg;
                    break;
                }
            }
            
            if (color == null) {
                color = new SpillRegister(stackOffset);
                stackOffset += 8;  // 每个变量8bytes
            }
            
            allocation.put(node, color);
        }
        
        return allocation;
    }
    
    private boolean mayInterfere(Node node1, Node node2) {
        if (node1.equals(node2)) {
            return false;
        }
        
        Set<Node> allNodes = new HashSet<>();
        collectNodes(graph.endBlock(), allNodes);
        
        for (Node node : allNodes) {
            Set<Node> liveAfter = liveness.getLiveAfter(node);
            if (liveAfter.contains(node1) && liveAfter.contains(node2)) {
                return true;
            }
            
            if (node instanceof BinaryOperationNode) {
                Node dest = node;
                if (dest.equals(node1) && liveAfter.contains(node2)) {
                    return true;
                }
                if (dest.equals(node2) && liveAfter.contains(node1)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void collectNodes(Node node, Set<Node> nodes) {
        if (nodes.add(node)) {
            for (Node predecessor : node.predecessors()) {
                collectNodes(predecessor, nodes);
            }
        }
    }

    private static class SpillRegister implements Register {
        private final int offset;  // 相对于 %rbp 的偏移量
        
        public SpillRegister(int offset) {
            this.offset = offset;
        }
        
        @Override
        public String toString() {
            return String.format("%d(%%rbp)", offset);  // 返回栈偏移量格式
        }
        
        public int getOffset() {
            return offset;
        }
    }
} 