grammar LitmusC;
import LinuxLexer, LitmusAssertions;
@header{
import com.dat3m.dartagnan.program.utils.EType;
}
main
:	LitmusLanguage ~(LBrace)* variableDeclaratorList program variableList? assertionFilter? assertionList? comment? EOF
;
variableDeclaratorList
:	LBrace (globalDeclarator Semi comment?)* RBrace (Semi)?
;
globalDeclarator
:	typeSpecifier? varName (Equals initConstantValue)? #globalDeclaratorLocation
|	typeSpecifier? t=threadId Colon n=varName (Equals initConstantValue)? #globalDeclaratorRegister
|	typeSpecifier? varName (Equals Ast? (Amp? varName | LPar Amp? varName RPar))? #globalDeclaratorLocationLocation
|	typeSpecifier? t=threadId Colon n=varName (Equals Ast? (Amp? varName | LPar Amp? varName RPar))? #globalDeclaratorRegisterLocation
|	typeSpecifier? varName LBracket DigitSequence? RBracket (Equals initArray)? #globalDeclaratorArray
;
program
:	thread+
;
thread
:	threadId LPar threadArguments? RPar LBrace expression* RBrace
;
threadArguments
:	pointerTypeSpecifier varName (Comma pointerTypeSpecifier varName)*
;
expression
:	nre Semi
|	ifExpression
|	whileExpression
;
whileExpression
:	While LPar re RPar expression
|	While LPar re RPar LBrace expression* RBrace
;
ifExpression
:	If LPar re RPar expression elseExpression?
|	If LPar re RPar LBrace expression* RBrace elseExpression?
;
elseExpression
:	Else expression
|	Else LBrace expression* RBrace
;
re locals [String mo]
:	(	AtomicAddReturnRelaxed{$mo=EType.RELAXED;}
	|	AtomicAddReturnRelease{$mo=EType.RELEASE;}
	|	AtomicAddReturnAcquire{$mo=EType.ACQUIRE;}
	|	AtomicAddReturn{$mo=EType.MB;}) LPar value=re Comma address=re RPar #reReturnAdd
|	(	AtomicSubReturnRelaxed{$mo=EType.RELAXED;}
	|	AtomicSubReturnRelease{$mo=EType.RELEASE;}
	|	AtomicSubReturnAcquire{$mo=EType.ACQUIRE;}
	|	AtomicSubReturn{$mo=EType.MB;}) LPar value=re Comma address=re RPar #reReturnSub
|	(	AtomicIncReturnRelaxed{$mo=EType.RELAXED;}
	|	AtomicIncReturnRelease{$mo=EType.RELEASE;}
	|	AtomicIncReturnAcquire{$mo=EType.ACQUIRE;}
	|	AtomicIncReturn{$mo=EType.MB;}) LPar address=re RPar #reReturnInc
|	(	AtomicDecReturnRelaxed{$mo=EType.RELAXED;}
	|	AtomicDecReturnRelease{$mo=EType.RELEASE;}
	|	AtomicDecReturnAcquire{$mo=EType.ACQUIRE;}
	|	AtomicDecReturn{$mo=EType.MB;}) LPar address=re RPar #reReturnDec
|	(	AtomicFetchAddRelaxed{$mo=EType.RELAXED;}
	|	AtomicFetchAddRelease{$mo=EType.RELEASE;}
	|	AtomicFetchAddAcquire{$mo=EType.ACQUIRE;}
	|	AtomicFetchAdd{$mo=EType.MB;}) LPar value=re Comma address=re RPar #reFetchAdd
|	(	AtomicFetchSubRelaxed{$mo=EType.RELAXED;}
	|	AtomicFetchSubRelease{$mo=EType.RELEASE;}
	|	AtomicFetchSubAcquire{$mo=EType.ACQUIRE;}
	|	AtomicFetchSub{$mo=EType.MB;}) LPar value=re Comma address=re RPar #reFetchSub
|	(	AtomicFetchIncRelaxed{$mo=EType.RELAXED;}
	|	AtomicFetchIncRelease{$mo=EType.RELEASE;}
	|	AtomicFetchIncAcquire{$mo=EType.ACQUIRE;}
	|	AtomicFetchInc{$mo=EType.MB;}) LPar address=re RPar #reFetchInc
|	(	AtomicFetchDecRelaxed{$mo=EType.RELAXED;}
	|	AtomicFetchDecRelease{$mo=EType.RELEASE;}
	|	AtomicFetchDecAcquire{$mo=EType.ACQUIRE;}
	|	AtomicFetchDec{$mo=EType.MB;}) LPar address=re RPar #reFetchDec
|	(	XchgRelaxed{$mo=EType.RELAXED;}
	|	XchgRelease{$mo=EType.RELEASE;}
	|	XchgAcquire{$mo=EType.ACQUIRE;}
	|	Xchg{$mo=EType.MB;}) LPar address=re Comma value=re RPar #reXchg
|	(	CmpXchgRelaxed{$mo=EType.RELAXED;}
	|	CmpXchgRelease{$mo=EType.RELEASE;}
	|	CmpXchgAcquire{$mo=EType.ACQUIRE;}
	|	CmpXchg{$mo=EType.MB;}) LPar address=re Comma cmp=re Comma value=re RPar #reCmpXchg
|	AtomicSubAndTest LPar value=re Comma address=re RPar #reTestSub
|	AtomicIncAndTest LPar address=re RPar #reTestInc
|	AtomicDecAndTest LPar address=re RPar #reTestDec
|	AtomicAddUnless LPar address=re Comma value=re Comma cmp=re RPar #reAddUnless
|	(	(AtomicReadAcquire|SmpLoadAcquire){$mo=EType.ACQUIRE;}
	|	(AtomicRead|RcuDereference|ReadOnce){$mo=EType.RELAXED;}) LPar Ast? address=re RPar #reLoad
|	Ast address=re #reDereference
//|	SpinTrylock LPar address=re RPar #reSpinTryLock
//|	SpiIsLocked LPar address=re RPar #reSpinIsLocked
|	False #reFalse
|	True #reTrue
|	Excl re #reNot
|	lhs=re AmpAmp rhs=re #reAnd
|	lhs=re BarBar rhs=re #reOr
|	lhs=re EqualsEquals rhs=re #reEqual
|	lhs=re NotEquals rhs=re #reNotEqual
|	lhs=re LessEquals rhs=re #reLessEqual
|	lhs=re GreaterEquals rhs=re #reGreaterEqual
|	lhs=re Less rhs=re #reLess
|	lhs=re Greater rhs=re #reGreater
|	lhs=re Plus rhs=re #reSum
|	lhs=re Minus rhs=re #reDiff
|	lhs=re Amp rhs=re #reBitAnd
|	lhs=re Bar rhs=re #reBitOr
|	lhs=re Circ rhs=re #reXor
|	LPar re RPar #reParenthesis
|	LPar typeSpecifier Ast* RPar re #reCast
|	varName #reVarName
|	constant #reConst
;
nre
:	nreX
|	nreFence
;
nreX locals [String mo]
:	AtomicAdd LPar value=re Comma address=re RPar #nreAdd
|	AtomicSub LPar value=re Comma address=re RPar #nreSub
|	AtomicInc LPar address=re RPar #nreInc
|	AtomicDec LPar address=re RPar #nreDec
|	(	AtomicSet{$mo=EType.RELAXED;}
	|	(AtomicSetRelease|SmpStoreRelease|RcuAssignPointer){$mo=EType.RELEASE;}
	|	SmpStoreMb{$mo=EType.MB;}) LPar Ast? address=re Comma value=re RPar #nreStore
|	WriteOnce LPar Ast address=re Comma value=re RPar #nreWriteOnce
|	Ast? varName Equals re #nreAssignment
|	typeSpecifier varName (Equals re)? #nreRegDeclaration
//|	SpinLock LPar address=re RPar #nreSpinLock
//|	SpinUnlock LPar address=re RPar #nreSpinUnlock
//|	SpinUnlockWait LPar address=re RPar #nreSpinUnlockWait
;
nreFence locals [String name]
:	FenceSmpMb{$name="Mb";}
|	FenceSmpWMb{$name="Wmb";}
|	FenceSmpRMb{$name="Rmb";}
|	FenceSmpMbBeforeAtomic{$name="Before-atomic";}
|	FenceSmpMbAfterAtomic{$name="After-atomic";}
|	FenceSmpMbAfterSpinLock{$name="After-spinlock";}
|	RcuReadLock{$name=EType.RCU_LOCK;}
|	RcuReadUnlock{$name=EType.RCU_UNLOCK;}
|	(RcuSync | RcuSyncExpedited) {$name=EType.RCU_SYNC;} LPar RPar
;
variableList
:	Locations LBracket (threadVariable | varName) (Semi (threadVariable | varName))* Semi? RBracket
;
threadVariable returns [int tid, String name]
:	t=threadId Colon n=varName {$tid=$t.id; $name=$n.text;}
;
initConstantValue
:	AtomicInit LPar constant RPar
|	constant
;
initArray
:	LBrace arrayElement (Comma arrayElement)* RBrace
;
arrayElement
:	constant
|	Ast? (Amp? varName | LPar Amp? varName RPar)
;
pointerTypeSpecifier
:	Volatile? basicTypeSpecifier Ast
|	Volatile? atomicTypeSpecifier Ast
;
typeSpecifier
:	Volatile? basicTypeSpecifier Ast*
|	Volatile? atomicTypeSpecifier Ast*
;
basicTypeSpecifier
:	Int
|	IntPtr
|	Char
;
atomicTypeSpecifier
:	AtomicT
|	SpinlockT
;
varName
:	Underscore* Identifier (Underscore (Identifier | DigitSequence)*)*
;
// Allowed outside of thread body (otherwise might conflict with pointer cast)
comment
:	LPar Ast .*? Ast RPar
;
Locations
:	'locations'
;
While
:	'while'
;
If
:	'if'
;
Else
:	'else'
;
True
:	'true'
;
False
:	'false'
;
Volatile
:	'volatile'
;
Int
:	'int'
;
IntPtr
:	'intptr_t'
;
Char
:	'char'
;
AtomicT
:	'atomic_t'
;
SpinlockT
:	'spinlock_t'
;
AmpAmp
:	'&&'
;
BarBar
:	'||'
;
LitmusLanguage
:	'C'
;
AssertionNot
:	Tilde
|	'not'
;
BlockComment
:	'/*' .*? '*/' ->channel(HIDDEN)
;
LineComment
:	'//' .*? Newline ->channel(HIDDEN)
;