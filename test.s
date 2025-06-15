.global main
.global _main
.text

main:
    call _main
    movq %rax, %rdi
    movq $0x3C, %rax
    syscall

_main:
.L110718392:
    movl $0, %ebx
    jmp .L1101288798
.L1101288798:
    jmp .L305623748
.L305623748:
    movl $1, %edx
    movl %edx, %esi
    movl $1, %edx
    movl %edx, %eax
    movl %esi, %edx
    addl %eax, %edx
    movl $42, %ebx
    movl $42, %ebx
    movl %ebx, %eax
    movl %edx, %ebx
    subl %eax, %ebx
    cmpl $0, %ebx
    je .L991505714
.L166239592:
    jmp .L2085857771
.L2085857771:
    jmp .L1101288798
    jmp .L758529971
.L758529971:
    movl %edx, %ebx
    movl %edx, %ebx
    movl %ebx, %eax
    ret
.L991505714:
    jmp .L2085857771
.L1562557367:
