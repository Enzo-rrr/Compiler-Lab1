// ！！！！！！！！！！！
//不要用这个！这是草稿！
//改好的在原来的文件位置
package edu.kit.kastel.vads.compiler.backend.aasm;

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
            AasmRegisterAllocator allocator = new AasmRegisterAllocator();
            Map<Node, Register> registers = allocator.allocateRegisters(graph);
            builder.append("function ")
                .append(graph.name())
                .append(" {\n");
            generateForGraph(graph, builder, registers);
            builder.append("}");
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
                Register lhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.LEFT)); // 左操作数
                Register rhs = registers.get(predecessorSkipProj(mod, BinaryOperationNode.RIGHT)); // 右操作数
                Register out = registers.get(mod); // 当前节点要写入的目标寄存器
                // cdq->idiv->mov edx
                builder.append("    mov eax, ").append(lhs).append("\n");// eax ← 左操作数
                builder.append("    cdq\n"); // 符号扩展，准备除法（edx:eax）
                builder.append("    idiv ").append(rhs).append("\n"); // eax = 商, edx = 余数
                builder.append("    mov ").append(out).append(", edx\n"); // 把余数保存到目标寄存器
            }
            // x86中没有取余指令，通过将被除数放在edx：eax、除数放在指定寄存器，来实现   
            
            case ReturnNode r -> {
                Node result = predecessorSkipProj(r, ReturnNode.RESULT);
                Register reg = registers.get(result);
                builder.append("    mov eax, ").append(reg).append("\n");
                builder.append("    ret\n");
                // x86规定函数的返回值必须通过eax（或rax返回），所以要先把结果move到eax
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
        builder.append("    ").append(opcode).append(" ").append(dest).append(", ").append(rhs);
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
