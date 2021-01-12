grammar LitmusC;

import LinuxLexer, LitmusAssertions;

@header{
import com.dat3m.dartagnan.expression.op.*;
import com.dat3m.dartagnan.program.utils.EType;
}

main
    :    LitmusLanguage ~(LBrace)* variableDeclaratorList program variableList? assertionFilter? assertionList? comment? EOF
    ;

variableDeclaratorList
    :   LBrace (globalDeclarator Semi comment?)* RBrace (Semi)?
    ;

globalDeclarator
    :   typeSpecifier? varName (Equals initConstantValue)?                                                              # globalDeclaratorLocation
    |   typeSpecifier? t = threadId Colon n = varName (Equals initConstantValue)?                                       # globalDeclaratorRegister
    |   typeSpecifier? varName (Equals Ast? (Amp? varName | LPar Amp? varName RPar))?                                   # globalDeclaratorLocationLocation
    |   typeSpecifier? t = threadId Colon n = varName (Equals Ast? (Amp? varName | LPar Amp? varName RPar))?            # globalDeclaratorRegisterLocation
    |   typeSpecifier? varName LBracket DigitSequence? RBracket (Equals initArray)?                                     # globalDeclaratorArray
    ;

program
    :   thread+
    ;

thread
    :   threadId LPar threadArguments? RPar LBrace expression* RBrace
    ;

threadArguments
    :   pointerTypeSpecifier varName (Comma pointerTypeSpecifier varName)*
    ;

expression
    :   nre Semi
    |   ifExpression
    |   whileExpression
    ;

whileExpression
    :   While LPar re RPar expression
    |   While LPar re RPar LBrace expression* RBrace
    ;

ifExpression
    :   If LPar re RPar expression elseExpression?
    |   If LPar re RPar LBrace expression* RBrace elseExpression?
    ;

elseExpression
    :   Else expression
    |   Else LBrace expression* RBrace
    ;

re locals [IOpBin op, String mo]
    :   ( AtomicAddReturn        LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.MB;}
        | AtomicAddReturnRelaxed LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELAXED;}
        | AtomicAddReturnAcquire LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.ACQUIRE;}
        | AtomicAddReturnRelease LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELEASE;}
        | AtomicSubReturn        LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;}
        | AtomicSubReturnRelaxed LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELAXED;}
        | AtomicSubReturnAcquire LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.ACQUIRE;}
        | AtomicSubReturnRelease LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELEASE;}
        | AtomicIncReturn        LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.MB;}
        | AtomicIncReturnRelaxed LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELAXED;}
        | AtomicIncReturnAcquire LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.ACQUIRE;}
        | AtomicIncReturnRelease LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELEASE;}
        | AtomicDecReturn        LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;}
        | AtomicDecReturnRelaxed LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELAXED;}
        | AtomicDecReturnAcquire LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.ACQUIRE;}
        | AtomicDecReturnRelease LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELEASE;})                     # reAtomicOpReturn

    |   ( AtomicFetchAdd        LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.MB;}
        | AtomicFetchAddRelaxed LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELAXED;}
        | AtomicFetchAddAcquire LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.ACQUIRE;}
        | AtomicFetchAddRelease LPar value = re Comma address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELEASE;}
        | AtomicFetchSub        LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;}
        | AtomicFetchSubRelaxed LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELAXED;}
        | AtomicFetchSubAcquire LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.ACQUIRE;}
        | AtomicFetchSubRelease LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELEASE;}
        | AtomicFetchInc        LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.MB;}
        | AtomicFetchIncRelaxed LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELAXED;}
        | AtomicFetchIncAcquire LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.ACQUIRE;}
        | AtomicFetchIncRelease LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.RELEASE;}
        | AtomicFetchDec        LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;}
        | AtomicFetchDecRelaxed LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELAXED;}
        | AtomicFetchDecAcquire LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.ACQUIRE;}
        | AtomicFetchDecRelease LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.RELEASE;})                      # reAtomicFetchOp

    |   ( AtomicXchg        LPar address = re Comma value = re RPar {$mo = EType.MB;}
        | AtomicXchgRelaxed LPar address = re Comma value = re RPar {$mo = EType.RELAXED;}
        | AtomicXchgAcquire LPar address = re Comma value = re RPar {$mo = EType.ACQUIRE;}
        | AtomicXchgRelease LPar address = re Comma value = re RPar {$mo = EType.RELEASE;})                             # reXchg

    |   ( Xchg        LPar address = re Comma value = re RPar {$mo = EType.MB;}
        | XchgRelaxed LPar address = re Comma value = re RPar {$mo = EType.RELAXED;}
        | XchgAcquire LPar address = re Comma value = re RPar {$mo = EType.ACQUIRE;}
        | XchgRelease LPar address = re Comma value = re RPar {$mo = EType.RELEASE;})                                   # reXchg

    |   ( AtomicCmpXchg        LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.MB;}
        | AtomicCmpXchgRelaxed LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.RELAXED;}
        | AtomicCmpXchgAcquire LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.ACQUIRE;}
        | AtomicCmpXchgRelease LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.RELEASE;})           # reCmpXchg

    |   ( CmpXchg        LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.MB;}
        | CmpXchgRelaxed LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.RELAXED;}
        | CmpXchgAcquire LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.ACQUIRE;}
        | CmpXchgRelease LPar address = re Comma cmp = re Comma value = re RPar {$mo = EType.RELEASE;})                 # reCmpXchg

    |   ( AtomicSubAndTest LPar value = re Comma address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;}
        | AtomicIncAndTest LPar address = re RPar {$op = IOpBin.PLUS; $mo = EType.MB;}
        | AtomicDecAndTest LPar address = re RPar {$op = IOpBin.MINUS; $mo = EType.MB;})                                # reAtomicOpAndTest

    |   AtomicAddUnless LPar address = re Comma value = re Comma cmp = re RPar                                          # reAtomicAddUnless

    |   ( AtomicReadAcquire LPar address = re RPar {$mo = EType.ACQUIRE;}
        | AtomicRead        LPar address = re RPar {$mo = EType.RELAXED;}
        | RcuDereference    LPar Ast? address = re RPar {$mo = EType.RELAXED;}
        | SmpLoadAcquire    LPar address = re RPar {$mo = EType.ACQUIRE;})                                              # reLoad

    |   ReadOnce LPar Ast address = re RPar {$mo = EType.ONCE;}                                                         # reReadOnce
    |   Ast address = re                                                                                                # reReadNa

//    |   SpinTrylock LPar address = re RPar                                                                            # reSpinTryLock
//    |   SpiIsLocked LPar address = re RPar                                                                            # reSpinIsLocked

    |   boolConst                                                                                                       # reBoolConst
    |   Excl re                                                                                                         # reOpBoolNot
    |   re opBool re                                                                                                    # reOpBool
    |   re opCompare re                                                                                                 # reOpCompare
    |   re opArith re                                                                                                   # reOpArith

    |   LPar re RPar                                                                                                    # reParenthesis
    |   cast re                                                                                                         # reCast
    |   varName                                                                                                         # reVarName
    |   constant                                                                                                        # reConst
    ;

nre locals [IOpBin op, String mo, String name]
    :   ( AtomicAdd LPar value = re Comma address = re RPar {$op = IOpBin.PLUS;}
        | AtomicSub LPar value = re Comma address = re RPar {$op = IOpBin.MINUS;}
        | AtomicInc LPar address = re RPar {$op = IOpBin.PLUS;}
        | AtomicDec LPar address = re RPar {$op = IOpBin.MINUS;})                                                       # nreAtomicOp

    |   ( AtomicSet         LPar address = re Comma value = re RPar {$mo = EType.RELAXED;}
        | AtomicSetRelease  LPar address = re Comma value = re RPar {$mo = EType.RELEASE;}
        | SmpStoreRelease   LPar address = re Comma value = re RPar {$mo = EType.RELEASE;}
        | SmpStoreMb        LPar address = re Comma value = re RPar {$mo = EType.MB;}
        | RcuAssignPointer  LPar Ast? address = re Comma value = re RPar {$mo = EType.RELEASE;})                        # nreStore

    |   WriteOnce LPar Ast address = re Comma value = re RPar {$mo = EType.ONCE;}                                       # nreWriteOnce

    |   Ast? varName Equals re                                                                                          # nreAssignment
    |   typeSpecifier varName (Equals re)?                                                                              # nreRegDeclaration

//    |   SpinLock LPar address = re RPar                                                                               # nreSpinLock
//    |   SpinUnlock LPar address = re RPar                                                                             # nreSpinUnlock
//    |   SpinUnlockWait LPar address = re RPar                                                                         # nreSpinUnlockWait

    |   ( FenceSmpMb LPar RPar {$name = "Mb";}
        | FenceSmpWMb LPar RPar {$name = "Wmb";}
        | FenceSmpRMb LPar RPar {$name = "Rmb";}
        | FenceSmpMbBeforeAtomic LPar RPar {$name = "Before-atomic";}
        | FenceSmpMbAfterAtomic LPar RPar {$name = "After-atomic";}
        | FenceSmpMbAfterSpinLock LPar RPar {$name = "After-spinlock";}
        | RcuReadLock LPar RPar {$name = EType.RCU_LOCK;}
        | RcuReadUnlock LPar RPar {$name = EType.RCU_UNLOCK;}
        | (RcuSync | RcuSyncExpedited) LPar RPar {$name = EType.RCU_SYNC;})                                             # nreFence

    ;

variableList
    :   Locations LBracket (threadVariable | varName) (Semi (threadVariable | varName))* Semi? RBracket
    ;

boolConst returns [Boolean value]
    :   True    {$value = true;}
    |   False   {$value = false;}
    ;

opBool returns [BOpBin op]
    :   AmpAmp  {$op = BOpBin.AND;}
    |   BarBar  {$op = BOpBin.OR;}
    ;

opCompare returns [COpBin op]
    :   EqualsEquals    {$op = COpBin.EQ;}
    |   NotEquals       {$op = COpBin.NEQ;}
    |   LessEquals      {$op = COpBin.LTE;}
    |   GreaterEquals   {$op = COpBin.GTE;}
    |   Less            {$op = COpBin.LT;}
    |   Greater         {$op = COpBin.GT;}
    ;

opArith returns [IOpBin op]
    :   Plus    {$op = IOpBin.PLUS;}
    |   Minus   {$op = IOpBin.MINUS;}
    |   Amp     {$op = IOpBin.AND;}
    |   Bar     {$op = IOpBin.OR;}
    |   Circ    {$op = IOpBin.XOR;}
    ;

threadVariable returns [int tid, String name]
    :   t = threadId Colon n = varName  {$tid = $t.id; $name = $n.text;}
    ;

initConstantValue
    :   AtomicInit LPar constant RPar
    |   constant
    ;

initArray
    :   LBrace arrayElement (Comma arrayElement)* RBrace
    ;

arrayElement
    :   constant
    |   Ast? (Amp? varName | LPar Amp? varName RPar)
    ;

cast
    :   LPar typeSpecifier Ast* RPar
    ;

pointerTypeSpecifier
    :   (Volatile)? basicTypeSpecifier Ast
    |   (Volatile)? atomicTypeSpecifier Ast
    ;

typeSpecifier
    :   (Volatile)? basicTypeSpecifier Ast*
    |   (Volatile)? atomicTypeSpecifier Ast*
    ;

basicTypeSpecifier
    :   Int
    |   IntPtr
    |   Char
    ;

atomicTypeSpecifier
    :   AtomicT
    |   SpinlockT
    ;

varName
    :   Underscore* Identifier (Underscore (Identifier | DigitSequence)*)*
    ;

// Allowed outside of thread body (otherwise might conflict with pointer cast)
comment
    :   LPar Ast .*? Ast RPar
    ;

Locations
    :   'locations'
    ;

While
    :   'while'
    ;

If
    :   'if'
    ;

Else
    :   'else'
    ;

True
    :   'true'
    ;

False
    :   'false'
    ;

Volatile
    :   'volatile'
    ;

Int
    :   'int'
    ;

IntPtr
    :   'intptr_t'
    ;

Char
    :   'char'
    ;

AtomicT
    :   'atomic_t'
    ;

SpinlockT
    :   'spinlock_t'
    ;

AmpAmp
    :   '&&'
    ;

BarBar
    :   '||'
    ;

LitmusLanguage
    :   'C'
    ;

AssertionNot
    :   Tilde
    |   'not'
    ;

BlockComment
    :   '/*' .*? '*/'
        -> channel(HIDDEN)
    ;

LineComment
    :   '//' .*? Newline
        -> channel(HIDDEN)
    ;