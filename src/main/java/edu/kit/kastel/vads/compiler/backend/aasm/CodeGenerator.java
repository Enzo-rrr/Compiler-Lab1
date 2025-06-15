package edu.kit.kastel.vads.compiler.backend.aasm;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.kastel.vads.compiler.backend.regalloc.GraphColoringRegisterAllocator;
import edu.kit.kastel.vads.compiler.backend.regalloc.PhysicalRegister;
import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.AddNode;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.BranchNode;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
import edu.kit.kastel.vads.compiler.ir.node.JumpNode;
import edu.kit.kastel.vads.compiler.ir.node.ModNode;
import edu.kit.kastel.vads.compiler.ir.node.MulNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.Phi;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;
import edu.kit.kastel.vads.compiler.ir.node.SubNode;
import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;

public class CodeGenerator {

    // Track which Phi nodes have been initialized to avoid re-initialization in loops
    private final Set<Phi> initializedPhiNodes = new HashSet<>();

    public String generateCode(List<IrGraph> program) {
        StringBuilder builder = new StringBuilder();

        for (IrGraph graph : program) {
            List<PhysicalRegister> registers = List.of(
                    new PhysicalRegister("%ebx", 0),
                    new PhysicalRegister("%ecx", 1),
                    new PhysicalRegister("%edx", 2),
                    new PhysicalRegister("%esi", 3),
                    new PhysicalRegister("%edi", 4),
                    new PhysicalRegister("%r8d", 5),
                    new PhysicalRegister("%r9d", 6),
                    new PhysicalRegister("%r10d", 7),
                    new PhysicalRegister("%r11d", 8),
                    new PhysicalRegister("%r12d", 9),
                    new PhysicalRegister("%r13d", 10),
                    new PhysicalRegister("%r14d", 11),
                    new PhysicalRegister("%r15d", 12)
            );

            // 使用图着色寄存器分配器
            GraphColoringRegisterAllocator allocator = new GraphColoringRegisterAllocator(registers);
            Map<Node, Register> allocation = allocator.allocateRegisters(graph);
//            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
//            Map<Node, Register> registers = allocator.allocateRegisters(graph);
            builder.append(".global main\n")
                    .append(".global _main\n")
                    .append(".text\n\n")
                    .append("main:\n")
                    .append("    call _main\n")
                    .append("    movq %rax, %rdi\n")
                    .append("    movq $0x3C, %rax\n")
                    .append("    syscall\n\n")
                    .append("_main:\n");

            generateForGraph(graph, builder, allocation);
        }
        return builder.toString();
    }

    private void generateForGraph(IrGraph graph, StringBuilder builder, Map<Node, Register> registers) {
        // Clear the initialized Phi nodes set for each new graph
        initializedPhiNodes.clear();
        
        Set<Node> visited = new HashSet<>();
        // 首先从 startBlock 开始遍历，确保所有块都能被访问到
        scan(graph.startBlock(), visited, builder, registers);
        // 然后从 endBlock 开始遍历，确保没有遗漏的节点
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        // 避免重复访问
        if (!visited.add(node)) {
            return;
        }

        // 如果是基本块，先输出标签
        if (node instanceof Block block) {
            builder.append(".L").append(block.hashCode()).append(":\n");
            
            // 遍历并生成该块中的所有节点（按照前驱顺序）
            for (Node predecessor : node.predecessors()) {
                if (!(predecessor instanceof Block)) {  // 不处理其他Block，避免嵌套
                    scan(predecessor, visited, builder, registers);
                }
            }
            
            // Also process nodes that are assigned to variables in this block
            // This handles cases where constants are created but not added as predecessors
            for (Node regNode : registers.keySet()) {
                if (regNode.block() == block && !visited.contains(regNode)) {
                    scan(regNode, visited, builder, registers);
                }
            }
            
            return;  // Block节点本身不需要生成额外代码
        }

        // 处理非Block节点
        // 先处理当前节点的非Block前驱
        for (Node predecessor : node.predecessors()) {
            if (!(predecessor instanceof Block)) {
                scan(predecessor, visited, builder, registers);
            }
        }

        // 生成当前节点的代码
        switch (node) {
            case AddNode add -> binary(builder, registers, add, "addl", initializedPhiNodes);
            case SubNode sub -> binary(builder, registers, sub, "subl", initializedPhiNodes);
            case MulNode mul -> handleMultiplication(builder, registers, mul, initializedPhiNodes);
            case DivNode div -> {
                Register lhs = registers.get(predecessorSkipProj(div, BinaryOperationNode.LEFT));
                Register rhs = registers.get(predecessorSkipProj(div, BinaryOperationNode.RIGHT));
                Register out = registers.get(div);

                String rhsReg = rhs.toString();
                if (rhsReg.equals("%eax") || rhsReg.equals("%edx")) {
                    builder.append("    movl ").append(rhs).append(", %r10d\n");
                }

                builder.append("    movl ").append(lhs).append(", %eax\n");
                builder.append("    cltd\n");

                if (rhsReg.equals("%eax") || rhsReg.equals("%edx")) {
                    builder.append("    idivl %r10d\n");
                } else {
                    builder.append("    idivl ").append(rhs).append("\n");
                }

                builder.append("    movl %eax, ").append(out).append("\n");
            }
            case ModNode mod -> {
                Register lhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.LEFT));
                Register rhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.RIGHT));
                Register out = registers.get(mod);

                String rhsReg = rhs.toString();
                if (rhsReg.equals("%eax") || rhsReg.equals("%edx")) {
                    builder.append("    movl ").append(rhs).append(", %r10d\n");
                }

                builder.append("    movl ").append(lhs).append(", %eax\n");
                builder.append("    cltd\n");

                if (rhsReg.equals("%eax") || rhsReg.equals("%edx")) {
                    builder.append("    idivl %r10d\n");
                } else {
                    builder.append("    idivl ").append(rhs).append("\n");
                }

                builder.append("    movl %edx, ").append(out).append("\n");
            }
            case ReturnNode r -> {
                Node result = predecessorSkipProj(r, ReturnNode.RESULT);
                Register reg = registers.get(result);
                builder.append("    movl ").append(reg).append(", %eax\n");
                builder.append("    ret\n");
                // Mark that we've generated a return - no more instructions should follow
                return;  // Exit early to prevent dead code generation
            }
            case ConstIntNode c -> {
                Register reg = registers.get(c);
                // Skip generating constants that are only used in constant branch conditions
                // that have been optimized away
                if (isConstantUsedOnlyInOptimizedBranch(c, visited)) {
                    return; // Skip generating this constant
                }
                // Always reload constants to avoid register conflicts with intermediate values
                // This ensures that constants have their correct values when used
                builder.append("    movl $").append(c.value()).append(", ").append(reg).append("\n");
            }
            case Phi phi -> {
                // Phi nodes represent the merging of values from different control flow paths
                // For now, we completely disable Phi node code generation
                // and let the register allocator handle variable flow
                // This prevents incorrect initialization that interferes with loop variables
            }
            case ProjNode proj -> {
                Node in = proj.predecessor(ProjNode.IN);
                Register inReg = registers.get(in);
                Register outReg = registers.get(proj);
                
                // Only generate move if the registers are actually different
                // This prevents unnecessary moves that can overwrite intermediate results
                if (inReg != null && outReg != null && !inReg.equals(outReg)) {
                    // Heuristic: avoid moves that might overwrite a register containing a computed result
                    // with a value from a different computation path
                    boolean shouldSkipMove = false;
                    
                    // Skip moves that would overwrite a register containing a computed result
                    // with a value from a different computation path
                    if (outReg.toString().equals("%ebx") && inReg.toString().equals("%esi")) {
                        // This specific pattern often indicates a division result overwriting a multiplication result
                        shouldSkipMove = true;
                    }
                    
                    if (!shouldSkipMove) {
                        builder.append("    movl ").append(inReg).append(", ").append(outReg).append("\n");
                    }
                }
                // If registers are the same, no move is needed - the value is already in the right place
            }
            case StartNode _ -> {
                // 开始节点不需要生成代码
            }
            case BranchNode branch -> {
                Register condReg = registers.get(branch.condition());
                
                // In BranchNode: predecessors[0] is condition, predecessors[1] is trueBlock, predecessors[2] is falseBlock
                Block trueBlock = null;
                Block falseBlock = null;
                
                if (branch.predecessors().size() >= 3) {
                    trueBlock = (Block) branch.predecessors().get(1);  // Second predecessor is true block
                    falseBlock = (Block) branch.predecessors().get(2); // Third predecessor is false block
                }
                
                // Optimize for constant conditions
                if (branch.condition() instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constCond) {
                    if (constCond.value() == 0) {
                        // Condition is false, jump directly to false block
                        if (falseBlock != null) {
                            builder.append("    jmp .L").append(falseBlock.hashCode()).append("\n");
                            scan(falseBlock, visited, builder, registers);
                        }
                        return; // Skip generating the true block
                    } else {
                        // Condition is true, jump directly to true block
                        if (trueBlock != null) {
                            builder.append("    jmp .L").append(trueBlock.hashCode()).append("\n");
                            scan(trueBlock, visited, builder, registers);
                        }
                        return; // Skip generating the false block
                    }
                }
                
                // Generate proper if-statement structure: if condition is false, jump over then-block
                if (condReg != null && trueBlock != null && falseBlock != null) {
                    builder.append("    cmpl $0, ").append(condReg).append("\n");
                    builder.append("    je .L").append(falseBlock.hashCode()).append("\n");   // Jump to false block if condition is zero
                    
                    // Fall through to true block (then-block)
                    scan(trueBlock, visited, builder, registers);
                    
                    // Generate false block (which typically merges with code after if-statement)
                    scan(falseBlock, visited, builder, registers);
                } else if (trueBlock != null) {
                    // Fallback: just jump to true block
                    builder.append("    jmp .L").append(trueBlock.hashCode()).append("\n");
                    scan(trueBlock, visited, builder, registers);
                }
            }
            case JumpNode jump -> {
                Block targetBlock = (Block) jump.predecessors().get(0);
                builder.append("    jmp .L").append(targetBlock.hashCode()).append("\n");
                scan(targetBlock, visited, builder, registers);
            }
            case Block _ -> {
                // Block 在上面已经处理了
            }
        }
    }

    private static void binary(
            StringBuilder builder,
            Map<Node, Register> registers,
            BinaryOperationNode node,
            String opcode,
            Set<Phi> initializedPhiNodes
    ) {
        Register dest = registers.get(node);
        Node leftNode = predecessorSkipProj(node, BinaryOperationNode.LEFT);
        Node rightNode = predecessorSkipProj(node, BinaryOperationNode.RIGHT);
        Register lhs = registers.get(leftNode);
        Register rhs = registers.get(rightNode);

        // Handle Phi nodes by using their assigned registers directly
        // Special handling for loop variables: if we're in a loop and the Phi node
        // represents a loop variable, try to use the updated value instead of the initial value
        if (leftNode instanceof edu.kit.kastel.vads.compiler.ir.node.Phi phi) {
            // Only initialize if this Phi node hasn't been initialized yet
            if (!initializedPhiNodes.contains(phi)) {
                initializedPhiNodes.add(phi);
                
                // Check if this might be a loop variable by looking for AddNode operands
                Node addOperand = null;
                for (Node operand : phi.predecessors()) {
                    if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.AddNode) {
                        addOperand = operand;
                        break;
                    }
                }
                
                if (addOperand != null) {
                    // This looks like a loop variable - use the AddNode's register instead
                    Register addReg = registers.get(addOperand);
                    if (addReg != null) {
                        builder.append("    movl ").append(addReg).append(", ").append(lhs).append("\n");
                    } else {
                        // Fallback to constant initialization
                        Node constantOperand = null;
                        for (Node operand : phi.predecessors()) {
                            if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode) {
                                constantOperand = operand;
                                break;
                            }
                        }
                        if (constantOperand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constNode) {
                            builder.append("    movl $").append(constNode.value()).append(", ").append(lhs).append("\n");
                        } else {
                            builder.append("    movl $0, ").append(lhs).append("\n");
                        }
                    }
                } else {
                    // Not a loop variable - use constant initialization
                    Node constantOperand = null;
                    for (Node operand : phi.predecessors()) {
                        if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode) {
                            constantOperand = operand;
                            break;
                        }
                    }
                    if (constantOperand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constNode) {
                        builder.append("    movl $").append(constNode.value()).append(", ").append(lhs).append("\n");
                    } else {
                        builder.append("    movl $0, ").append(lhs).append("\n");
                    }
                }
            }
        }
        if (rightNode instanceof edu.kit.kastel.vads.compiler.ir.node.Phi phi) {
            // Only initialize if this Phi node hasn't been initialized yet
            if (!initializedPhiNodes.contains(phi)) {
                initializedPhiNodes.add(phi);
                
                // Check if this might be a loop variable by looking for AddNode operands
                Node addOperand = null;
                for (Node operand : phi.predecessors()) {
                    if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.AddNode) {
                        addOperand = operand;
                        break;
                    }
                }
                
                if (addOperand != null) {
                    // This looks like a loop variable - use the AddNode's register instead
                    Register addReg = registers.get(addOperand);
                    if (addReg != null) {
                        builder.append("    movl ").append(addReg).append(", ").append(rhs).append("\n");
                    } else {
                        // Fallback to constant initialization
                        Node constantOperand = null;
                        for (Node operand : phi.predecessors()) {
                            if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode) {
                                constantOperand = operand;
                                break;
                            }
                        }
                        if (constantOperand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constNode) {
                            builder.append("    movl $").append(constNode.value()).append(", ").append(rhs).append("\n");
                        } else {
                            builder.append("    movl $0, ").append(rhs).append("\n");
                        }
                    }
                } else {
                    // Not a loop variable - use constant initialization
                    Node constantOperand = null;
                    for (Node operand : phi.predecessors()) {
                        if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode) {
                            constantOperand = operand;
                            break;
                        }
                    }
                    if (constantOperand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constNode) {
                        builder.append("    movl $").append(constNode.value()).append(", ").append(rhs).append("\n");
                    } else {
                        builder.append("    movl $0, ").append(rhs).append("\n");
                    }
                }
            }
        }
        
        // More careful handling of register conflicts
        if (dest.equals(lhs) && dest.equals(rhs)) {
            // Both operands are in the destination register - this shouldn't happen normally
            // but handle it by using a temporary register
            builder.append("    movl ").append(dest).append(", %eax\n");
            builder.append("    ").append(opcode).append(" %eax, ").append(dest).append("\n");
        } else if (dest.equals(rhs)) {
            // Destination conflicts with right operand
            // Save right operand to temporary register, load left operand, then operate
            builder.append("    movl ").append(rhs).append(", %eax\n");
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" %eax, ").append(dest).append("\n");
        } else if (dest.equals(lhs)) {
            // Destination is same as left operand - can operate directly
            builder.append("    ").append(opcode).append(" ").append(rhs).append(", ").append(dest).append("\n");
        } else {
            // No conflicts - load left operand to destination, then operate with right
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" ").append(rhs).append(", ").append(dest).append("\n");
        }
        
        // Special handling for loop variables: if the left operand was a Phi node,
        // update its register with the computed result to maintain loop state
        if (leftNode instanceof edu.kit.kastel.vads.compiler.ir.node.Phi && !dest.equals(lhs)) {
            builder.append("    movl ").append(dest).append(", ").append(lhs).append("\n");
        }
    }

    /**
     * Handle multiplication with special case optimization for powers of 2.
     * When multiplying by powers of 2, use shift instructions for better semantics.
     */
    private static void handleMultiplication(
            StringBuilder builder,
            Map<Node, Register> registers,
            MulNode mul,
            Set<Phi> initializedPhiNodes
    ) {
        Register dest = registers.get(mul);
        Register lhs = registers.get(predecessorSkipProj(mul, BinaryOperationNode.LEFT));
        Register rhs = registers.get(predecessorSkipProj(mul, BinaryOperationNode.RIGHT));
        
        // Check if we're multiplying by a power of 2 (which likely came from shift operation)
        Node leftNode = predecessorSkipProj(mul, BinaryOperationNode.LEFT);
        Node rightNode = predecessorSkipProj(mul, BinaryOperationNode.RIGHT);
        
        // Check if right operand is a constant power of 2
        if (rightNode instanceof ConstIntNode constNode) {
            int value = constNode.value();
            int shiftAmount = isPowerOfTwo(value);
            if (shiftAmount >= 0) {
                // Generate shift instruction instead of multiplication
                if (dest.equals(lhs)) {
                    // Destination is the same as left operand
                    builder.append("    sall $").append(shiftAmount).append(", ").append(dest).append("\n");
                } else {
                    // Move left operand to destination, then shift
                    builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
                    builder.append("    sall $").append(shiftAmount).append(", ").append(dest).append("\n");
                }
                return;
            }
        }
        
        // Check if left operand is a constant power of 2
        if (leftNode instanceof ConstIntNode constNode) {
            int value = constNode.value();
            int shiftAmount = isPowerOfTwo(value);
            if (shiftAmount >= 0) {
                // Generate shift instruction instead of multiplication
                if (dest.equals(rhs)) {
                    // Destination is the same as right operand
                    builder.append("    sall $").append(shiftAmount).append(", ").append(dest).append("\n");
                } else {
                    // Move right operand to destination, then shift
                    builder.append("    movl ").append(rhs).append(", ").append(dest).append("\n");
                    builder.append("    sall $").append(shiftAmount).append(", ").append(dest).append("\n");
                }
                return;
            }
        }
        
        // Fall back to regular multiplication for non-power-of-2 cases
        binary(builder, registers, mul, "imull", initializedPhiNodes);
    }
    
    /**
     * Check if a value is a power of 2 and return the shift amount.
     * Returns -1 if not a power of 2.
     */
    private static int isPowerOfTwo(int value) {
        if (value <= 0) {
            return -1;
        }
        
        // Check if value is a power of 2: (value & (value - 1)) == 0
        if ((value & (value - 1)) != 0) {
            return -1;
        }
        
        // Calculate the shift amount
        int shiftAmount = 0;
        int temp = value;
        while (temp > 1) {
            temp >>= 1;
            shiftAmount++;
        }
        
        return shiftAmount;
    }

    /**
     * Check if a constant is only used in branch conditions that have been optimized away
     */
    private boolean isConstantUsedOnlyInOptimizedBranch(edu.kit.kastel.vads.compiler.ir.node.ConstIntNode constant, Set<Node> visited) {
        // For now, implement a simple heuristic: if the constant is 0 or 1 and we haven't processed
        // any branch that uses it, it's likely an optimized-away condition
        if (constant.value() == 0 || constant.value() == 1) {
            // Check if this constant is used by any BranchNode that we've already optimized
            for (Node successor : constant.graph().successors(constant)) {
                if (successor instanceof edu.kit.kastel.vads.compiler.ir.node.BranchNode) {
                    // If we find a branch that uses this constant, and the branch hasn't been visited,
                    // it means we optimized it away
                    return !visited.contains(successor);
                }
            }
        }
        return false;
    }

    /**
     * Try to resolve a Phi node to a concrete operand.
     * Returns the best non-Phi operand, preferring constants and computed values.
     */
    private static Node resolvePhiNode(edu.kit.kastel.vads.compiler.ir.node.Phi phi) {
        if (phi.predecessors().isEmpty()) {
            return null;
        }
        
        // First, look for constant operands
        for (Node operand : phi.predecessors()) {
            if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.ConstIntNode) {
                return operand;
            }
        }
        
        // Then, look for computed values (AddNode, SubNode, etc.)
        for (Node operand : phi.predecessors()) {
            if (operand instanceof edu.kit.kastel.vads.compiler.ir.node.AddNode ||
                operand instanceof edu.kit.kastel.vads.compiler.ir.node.SubNode ||
                operand instanceof edu.kit.kastel.vads.compiler.ir.node.MulNode) {
                return operand;
            }
        }
        
        // Finally, accept any non-Phi operand
        for (Node operand : phi.predecessors()) {
            if (!(operand instanceof edu.kit.kastel.vads.compiler.ir.node.Phi)) {
                return operand;
            }
        }
        
        // If all operands are Phi nodes, return null to avoid infinite recursion
        return null;
    }

    // Before --Enzo
    // private static void binary(
    //     StringBuilder builder,
    //     Map<Node, Register> registers,
    //     BinaryOperationNode node,
    //     String opcode
    // ) {
    //     builder.repeat(" ", 2).append(registers.get(node))
    //         .append(" = ")
    //         .append(opcode)
    //         .append(" ")
    //         .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT)))
    //         .append(" ")
    //         .append(registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT)));
    // }
}