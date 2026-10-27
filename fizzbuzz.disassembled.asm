# Reconstructed by Disassembler.java
# Text base: 0x00400000
# Data base: 0x10010000
# Byte order: BIG_ENDIAN

.data
    .asciiz "Fizz"
    .asciiz "Buzz"

.text
.globl main
main:
    addiu $t1, $zero, 1
    addiu $t2, $zero, 101
L4:
    jal L7
    jal L8
    addi $t1, $t1, 1
    bne $t1, $t2, L4
    jal L9
L7:
    addi $sp, $sp, -8
    sw $ra, 0($sp)
    addiu $at, $zero, 3
    div $t1, $at
    mfhi $t3
    addiu $at, $zero, 5
    div $t1, $at
    mfhi $t4
    bne $t3, $zero, L2
    jal L3
L2:
    bne $t4, $zero, L1
    jal L5
    j L6
L1:
    beq $t3, $zero, L6
    jal L10
L6:
    lw $ra, 0($sp)
    addi $sp, $sp, 8
    jr $ra
L3:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 0
    syscall
    jr $ra
L5:
    addiu $v0, $zero, 4
    lui $a0, 4097
    ori $a0, $a0, 5
    syscall
    jr $ra
L10:
    addiu $v0, $zero, 1
    addu $a0, $t1, $zero
    syscall
    jr $ra
L8:
    addiu $v0, $zero, 11
    addiu $a0, $zero, 10
    syscall
    jr $ra
L9:
    addiu $v0, $zero, 10
    syscall
