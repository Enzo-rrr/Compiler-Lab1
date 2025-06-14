.global main
.global _main
.text

main:
    call _main
    movq %rax, %rdi
    movq $0x3C, %rax
    syscall

_main:
.L476402209:
.L917142466:
    jmp .L1993134103
.L1993134103:
    jmp .L1617791695
.L1617791695:
    movl $0, %ebx
    cmpl $0, %ebx
    je .L537548559
.L864237698:
.L537548559:
    movl $1, %ebx
    movl %ebx, %eax
    ret
    jmp .L237852351
.L237852351:
    jmp .L1617791695
