package edu.kit.kastel.vads.compiler.backend.aasm;

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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        
            case AddNode add -> binary(builder, registers, add, "add");
            case SubNode sub -> binary(builder, registers, sub, "sub");
            case MulNode mul -> binary(builder, registers, mul, "imul");  
            case DivNode div -> binary(builder, registers, div, "idiv");
            case ModNode mod -> {
                Register lhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.LEFT)); 
                Register rhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.RIGHT));
                Register out = registers.get(mod);
                // cdq->idiv->mov edx
                builder.append("    mov %eax, ").append(lhs).append("\n");
                builder.append("    cdq\n"); 
                builder.append("    idiv ").append(rhs).append("\n"); 
                builder.append("    mov ").append(out).append(", edx\n"); 
            }

            case ReturnNode r -> {
                Node result = predecessorSkipProj(r, ReturnNode.RESULT);
                Register reg = registers.get(result);
                builder.append("    mov %eax, ").append(reg).append("\n");
                builder.append("    ret\n");
            }

            case ConstIntNode c -> {
                Register reg = registers.get(c);
                builder.append("    mov ").append(reg).append(", ").append(c.value()).append("\n");
            }

            case Phi _ -> throw new UnsupportedOperationException("phi");
            case Block _, ProjNode _, StartNode _ -> {
                // do nothing, skip line break
                return;
            }
        }   

        // Before  --Enzo
        // switch (node) {
        //     case AddNode add -> binary(builder, registers, add, "add");
        //     case SubNode sub -> binary(builder, registers, sub, "sub");
        //     case MulNode mul -> binary(builder, registers, mul, "mul");
        //     case DivNode div -> binary(builder, registers, div, "div");
        //     case ModNode mod -> binary(builder, registers, mod, "mod");
        //     case ReturnNode r -> builder.repeat(" ", 2).append("ret ")
        //         .append(registers.get(predecessorSkipProj(r, ReturnNode.RESULT)));
        //     case ConstIntNode c -> builder.repeat(" ", 2)
        //         .append(registers.get(c))
        //         .append(" = const ")
        //         .append(c.value());
        //     case Phi _ -> throw new UnsupportedOperationException("phi");
        //     case Block _, ProjNode _, StartNode _ -> {
        //         // do nothing, skip line break
        //         return;
        //     }
        // }

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
    
        // mov dest, lhs
        builder.append("    mov ").append(dest).append(", ").append(lhs).append("\n");
        // opcode dest, rhs
        builder.append("    ").append(opcode).append(" ").append(dest).append(", ").append(rhs).append("\n");
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
