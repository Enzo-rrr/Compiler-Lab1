package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.ProgramTree;
import edu.kit.kastel.vads.compiler.parser.visitor.Unit;

public class SemanticAnalysis {

    public void analyze(ProgramTree program) {
        program.accept(new VariableStatusAnalysis(), new Namespace<VariableStatusAnalysis.VariableStatus>());
        program.accept(new IntegerLiteralRangeAnalysis(), new Namespace<Void>());
        program.accept(new TypeAnalysis(), new Namespace<TypeAnalysis.Type>());
        program.accept(new ReturnAnalysis(), new Namespace<Boolean>());
    }
}
