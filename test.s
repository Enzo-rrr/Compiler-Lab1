.global main
.global _main
.text

main:
    call _main
    movq %rax, %rdi
    movq $0x3C, %rax
    syscall

_main:
.L721748895:
    jmp .L1031980531
.L1031980531:
    jmp .L463345942
.L463345942:
    jmp .L1490180672
.L1490180672:
    jmp .L463345942
