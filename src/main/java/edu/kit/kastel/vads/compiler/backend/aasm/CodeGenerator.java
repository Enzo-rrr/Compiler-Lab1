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
        Set<Node> visited = new HashSet<>();
        // 从 endBlock 开始遍历以确保所有节点都被访问
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
            case AddNode add -> binary(builder, registers, add, "addl");
            case SubNode sub -> binary(builder, registers, sub, "subl");
            case MulNode mul -> handleMultiplication(builder, registers, mul);
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
                builder.append("    movl $").append(c.value()).append(", ").append(reg).append("\n");
            }
            case Phi phi -> {
                // Simplified Phi handling to reduce redundant instructions
                Register out = registers.get(phi);
                if (phi.predecessors().size() > 0) {
                    Node firstPred = phi.predecessors().get(0);
                    Register firstReg = registers.get(firstPred);
                    // Only generate move if registers are different and both are valid
                    if (firstReg != null && out != null && !out.equals(firstReg)) {
                        builder.append("    movl ").append(firstReg).append(", ").append(out).append("\n");
                    }
                    // For additional predecessors, we would need more sophisticated phi resolution
                    // For now, keep it simple to avoid the redundant cmovne instructions
                }
            }
            case ProjNode proj -> {
                Node in = proj.predecessor(ProjNode.IN);
                Register inReg = registers.get(in);
                Register outReg = registers.get(proj);
                builder.append("    movl ").append(inReg).append(", ").append(outReg).append("\n");
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
            String opcode
    ) {
        Register dest = registers.get(node);
        Register lhs = registers.get(predecessorSkipProj(node, BinaryOperationNode.LEFT));
        Register rhs = registers.get(predecessorSkipProj(node, BinaryOperationNode.RIGHT));

        // 处理目标寄存器与源寄存器冲突的情况
        if (dest.equals(rhs)) {
            // 如果目标寄存器与右操作数相同，使用临时寄存器
            builder.append("    movl ").append(rhs).append(", %eax\n");
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" %eax, ").append(dest).append("\n");
        } else if (dest.equals(lhs)) {
            // 如果目标寄存器与左操作数相同，直接操作
            builder.append("    ").append(opcode).append(" ").append(rhs).append(", ").append(dest).append("\n");
        } else {
            // 如果目标寄存器与两个操作数都不同，先移动左操作数
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" ").append(rhs).append(", ").append(dest).append("\n");
        }
    }

    /**
     * Handle multiplication with special case optimization for powers of 2.
     * When multiplying by powers of 2, use shift instructions for better semantics.
     */
    private static void handleMultiplication(
            StringBuilder builder,
            Map<Node, Register> registers,
            MulNode mul
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
        binary(builder, registers, mul, "imull");
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