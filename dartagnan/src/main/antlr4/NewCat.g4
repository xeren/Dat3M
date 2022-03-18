grammar NewCat;

mcm
:   (NAME)? definition+ EOF
;

definition
:   ACYCLIC e = expression (AS NAME)?                               # acyclicDefinition
|   IRREFLEXIVE e = expression (AS NAME)?                           # irreflexiveDefinition
|   EMPTY e = expression (AS NAME)?                                 # emptyDefinition
|   LET n = NAME EQ e = expression                                  # letDefinition
|   LET REC NAME EQ expression (AND NAME EQ expression)*            # letRecDefinition
;

expression
:   e1 = expression STAR e2 = expression                            # exprCartesian
|   e = expression (POW)? STAR                                      # exprTransRef
|   e = expression (POW)? PLUS                                      # exprTransitive
|   e = expression (POW)? INV                                       # exprInverse
|   e = expression OPT                                              # exprOptional
|   NOT e = expression                                              # exprComplement
|   e1 = expression SEMI e2 = expression                            # exprComposition
|   e1 = expression BAR e2 = expression                             # exprUnion
|   e1 = expression BSLASH e2 = expression                          # exprMinus
|   e1 = expression AMP e2 = expression                             # exprIntersection
|   LBRAC DOMAIN LPAR e = expression RPAR RBRAC                     # exprDomainIdentity
|   LBRAC RANGE LPAR e = expression RPAR RBRAC                      # exprRangeIdentity
|   (TOID LPAR e = expression RPAR | LBRAC e = expression RBRAC)    # exprIdentity
|   FENCEREL LPAR n = NAME RPAR                                     # exprFencerel
|   LPAR e = expression RPAR                                        # expr
|   n = NAME                                                        # exprBasic
;

LET     :   'let';
REC     :   'rec';
AND     :   'and';
AS      :   'as';
TOID    :   'toid';

ACYCLIC     :   'acyclic';
IRREFLEXIVE :   'irreflexive';
EMPTY       :   'empty';

EQ      :   '=';
STAR    :   '*';
PLUS    :   '+';
OPT     :   '?';
INV     :   '-1';
NOT     :   '~';
AMP     :   '&';
BAR     :   '|';
SEMI    :   ';';
BSLASH  :   '\\';
POW     :   ('^');

LPAR    :   '(';
RPAR    :   ')';
LBRAC   :   '[';
RBRAC   :   ']';

FENCEREL    :   'fencerel';
DOMAIN      :   'domain';
RANGE       :   'range';

NAME    : [A-Za-z0-9\-_.]+;

LINE_COMMENT
    :   '//' ~[\n]*
        -> skip
    ;

BLOCK_COMMENT
    :   '(*' (.)*? '*)'
        -> skip
    ;

WS
    :   [ \t\r\n]+
        -> skip
    ;

INCLUDE
    :   'include "' .*? '"'
        -> skip
    ;

MODELNAME
    :   '"' .*? '"'
        -> skip
    ;