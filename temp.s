.global main
.global _main
.text

main:
    call _main
    movq %rax, %rdi
    movq $0x3C, %rax
    syscall

_main:
.L653305407:
    jmp .L1130478920
.L1130478920:
    movl $1, %ebx
    movl %ebx, %eax
    movl %edx, %ebx
    addl %eax, %ebx
    movl $42, %ecx
    subl %ecx, %ebx
    cmpl $0, %ebx
    je .L1252585652
.L1556956098:
.L1252585652:
    jmp .L1982791261
.L1982791261:
    movl %ebx, %eax
    ret
    jmp .L1552787810
.L1552787810:
    jmp .L1552787810
    jmp .L1130478920
