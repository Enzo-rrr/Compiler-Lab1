package edu.kit.kastel.vads.compiler.backend.regalloc;

/**
 * Represents a physical register in the target machine.
 */
public record PhysicalRegister(String name, int id) implements Register {
    @Override
    public String toString() {
        return name;
    }
} 