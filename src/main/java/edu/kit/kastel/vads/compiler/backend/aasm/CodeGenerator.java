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
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.DivNode;
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
        scan(graph.endBlock(), visited, builder, registers);
    }

    private void scan(Node node, Set<Node> visited, StringBuilder builder, Map<Node, Register> registers) {
        for (Node predecessor : node.predecessors()) {
            if (visited.add(predecessor)) {
                scan(predecessor, visited, builder, registers);
            }
        }
        switch (node) {
            case AddNode add -> binary(builder, registers, add, "addl");
            case SubNode sub -> binary(builder, registers, sub, "subl");
            case MulNode mul -> binary(builder, registers, mul, "imull");
            case DivNode div -> {
                Register lhs = registers.get(predecessorSkipProj(div, BinaryOperationNode.LEFT));   // 被除数
                Register rhs = registers.get(predecessorSkipProj(div, BinaryOperationNode.RIGHT));  // 除数
                Register out = registers.get(div);                                                  // 输出结果
            
                String lhsReg = lhs.toString();
                String rhsReg = rhs.toString();
                String outReg = out.toString();
            
                builder.append("    movl ").append(lhsReg).append(", %eax\n");
            
                builder.append("    cltd\n");
                String safeDivisorReg = "%r10d"; // 假设这个寄存器不分配给其他变量
                if (rhsReg.equals("%eax") || rhsReg.equals("%edx")) {

                    builder.append("    movl ").append(rhsReg).append(", ").append(safeDivisorReg).append("\n");
                    builder.append("    idivl ").append(safeDivisorReg).append("\n");
                } else {
                    builder.append("    movl ").append(rhsReg).append(", %ecx\n");
                    builder.append("    idivl %ecx\n");
                }
                builder.append("    movl %eax, ").append(outReg).append("\n");
            }
            case ModNode mod -> {
                Register lhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.LEFT));
                Register rhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.RIGHT));
                Register out = registers.get(mod);
                builder.append("    movl ").append(lhs).append(", %eax\n");
                builder.append("    cltd\n");
                builder.append("    movl ").append(rhs).append(", %ecx\n");
                builder.append("    idivl %ecx\n");
                builder.append("    movl %edx, ").append(out).append("\n");
            }
            case ReturnNode r -> {
                Node result = predecessorSkipProj(r, ReturnNode.RESULT);
                Register reg = registers.get(result);
                builder.append("    movl ").append(reg).append(", %eax\n");
                builder.append("    ret\n");
            }
            case ConstIntNode c -> {
                Register reg = registers.get(c);
                builder.append("    movl $").append(c.value()).append(", ").append(reg).append("\n");
            }
            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }
        builder.append("\n");
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

        if (dest.equals(rhs)) {
            builder.append("    movl ").append(rhs).append(", %eax\n");
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" %eax, ").append(dest).append("\n");
        } else {
            builder.append("    movl ").append(lhs).append(", ").append(dest).append("\n");
            builder.append("    ").append(opcode).append(" ").append(rhs).append(", ").append(dest).append("\n");
        }
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
