/*
 * Copyright (c) 2023 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Broadcom, Inc. - initial API and implementation
 */

lexer grammar CompilerDirectivesLexer;

WS : [ \t\f]+ -> channel(HIDDEN);

COMMACHAR: ',' ' '* ;

LITERAL: STRINGLITERAL;
ALPHNUM: A L P H N U M;
NOALPHNUM: N O A L P H N U M;
ZON: Z O N;
NOZON: N O Z O N;
PAC: P A C;
NOPAC: N O P A C;
NOBIN: N O B I N;
CPP: C P P;
TRUNCBIN: T R U N C B I N;
NOTRUNCBIN: N O T R U N C B I N;
DATEPROC: D A T E P R O C;
DP: D P;
FLAG : F L A G;
NOFLAG : N O F L A G;
TRIG : T R I G;
NOTRIG : N O T R I G;
EPILOG : E P I L O G;
GDS : G D S;
GRAPHIC : G R A P H I C;
LEASM : L E A S M;
LIB: L I B;
LIN: L I N;
MARGINS : M A R G I N S;
NATLANG : N A T L A N G;
CS : C S;
EN : E N;
KA : K A;
NUMPROC : N U M P R O C;
MIG : M I G;
NOCMPR2 : N O C M P R '2';
NODATEPROC : N O D A T E P R O C;
NODP : N O D P;
NODE : N O D E;
NOEPILOG : N O E P I L O G;
NOFLAGMIG : N O F L A G M I G;
NOGRAPHIC : N O G R A P H I C;
NOLIB : N O L I B;
NOOPSEQUENCE : N O O P S E Q U E N C E;
NOOPTIMIZE : N O O P T I M I Z E;
NOOPT : N O O P T;
NOP : N O P;
NOPROLOG : N O P R O L O G;
NOSTDTRUNC : N O S T D T R U N C;
NSEQ : N S E Q;
OPMARGINS : O P M A R G I N S;
OPSEQUENCE : O P S E Q U E N C E;
OP : O P;
PROLOG : P R O L O G;
RES : R E S;
SIZE : S I Z E;
SZ : S Z;
MAX : M A X;
YEARWINDOW : Y E A R W I N D O W;
YW : Y W;

ZLEN: Z L E N;
NOZLEN: N O Z L E N;

// cobolCompilerOption
ADATA: A D A T A;
NOADATA: N O A D A T A;
ADV: A D V;
NOADV: N O A D V;
AFP: A F P;
NOVOLATILE: N O V O L A T I L E;
VOLATILE: V O L A T I L E;
ARCH: A R C H;
ARITH: A R I T H;
AR: A R;
AWO: A W O;
NOAWO: N O A W O;
BLOCK0: B L O C K '0';
NOBLOCK0: N O B L O C K '0';
BUFSIZE: B U F S I Z E;
BUF: B U F;
NOCICS: N O C I C S;
CODEPAGE: C O D E P A G E;
CP: C P;
COMPILE: C O M P I L E;
NOCOMPILE: N O C O M P I L E;
NOC: N O C;
COPYLOC: C O P Y L O C;
CPLC: C P L C;
DWARF : D W A R F;
NODWARF : N O D W A R F;
EJPD : E J P D;
NOEJPD : N O E J P D;
SEPARATE : S E P A R A T E;
NOSEPARATE : N O S E P A R A T E;
DSNAME : D S N A M E;
NODSNAME : N O D S N A M E;
NOSEP: N O S E P;
ENDPERIOD: E N D P E R I O D;
ENDP: E N D P;
NOENDPERIOD: N O E N D P E R I O D;
EVENPACK: E V E N P A C K;
EVENP: E V E N P;
NOEVENPACK: N O E V E N P A C K;
LAXPERF: L A X P E R F;
LXPRF: L X P R F;
NOLAXPERF: N O L A X P E R F;
SLACKBYTES: S L A C K B Y T E S;
SLCKB: S L C K B;
NOSLACKBYTES: N O S L A C K B Y T E S;
OMITODOMIN: O M I T O D O M I N;
OOM: O O M;
NOOMITODOMIN: N O O M I T O D O M I N;
UNREF: U N R E F;
NOUNREFALL: N O U N R E F A L L;
NOUNRA: N O U N R A;
NOUNREFSOURCE: N O U N R E F S O U R C E;
NOUNRS: N O U N R S;
LAXREDEF: L A X R E D E F;
LXRDF: L X R D F;
NOLAXREDEF: N O L A X R E D E F;
DSN: D S N;
PATH : P A T H;
SYSLIB : S Y S L I B;
NOCOPYLOC: N O C O P Y L O C;
NOCPLC: N O C P L C;
COPYRIGHT: C O P Y R I G H T;
CPYR: C P Y R;
NOCOPYRIGHT: N O C O P Y R I G H T;
NOCPYR: N O C P Y R;
CURRENCY: C U R R E N C Y;
CURR: C U R R;
CURRENCY_SYMBOL : [\p{Sc}];
NOCURRENCY: N O C U R R E N C Y;
NOCURR: N O C U R R;
DATA: D A T A;
NODBCS: N O D B C S;
DECK: D E C K;
NODECK: N O D E C K;
NOD: N O D;
DEFINE: D E F I N E;
DEF: D E F;
NODEFINE: N O D E F I N E;
NODEF: N O D E F;
DIAGTRUNC: D I A G T R U N C;
DTR: D T R;
NODIAGTRUNC: N O D I A G T R U N C;
NODTR: N O D T R;
DISPSIGN: D I S P S I G N;
DS: D S;
SEP: S E P;
DLL: D L L;
NODLL: N O D L L;
DUMP: D U M P;
DU: D U;
CLEANSIGN: C L E A N S I G N;
NOCLEANSIGN: N O C L E A N S I G N;
NOCS: N O C S;
FORCENUMCMP: F O R C E N U M C M P;
FNC: F N C;
NOFORCENUMCMP: N O F O R C E N U M C M P;
NOFNC: N O F N C;
NODUMP: N O D U M P;
NODU: N O D U;
DYNAM: D Y N A M;
DYN: D Y N;
NODYNAM: N O D Y N A M;
NODYN: N O D Y N;
EXIT: E X I T;
EX: E X;
INEXIT: I N E X I T;
INX: I N X;
LIBEXIT: L I B E X I T;
LIBX: L I B X;
PRTEXIT: P R T E X I T;
PRTX: P R T X;
ADEXIT: A D E X I T;
ADX: A D X;
MSGEXIT: M S G E X I T;
MSGX: M S G X;
NOINEXIT: N O I N E X I T;
NOINX: N O I N X;
NOLIBEXIT: N O L I B E X I T;
NOLIBX: N O L I B X;
NOPRTEXIT: N O P R T E X I T;
NOPRTX: N O P R T X;
NOADEXIT: N O A D E X I T;
NOADX: N O A D X;
NOMSGEXIT: N O M S G E X I T;
NOMSGX: N O M S G X;
NOEXIT: N O E X I T;
NOEX: N O E X;
EXPORTALL: E X P O R T A L L;
EXP: E X P;
NOEXPORTALL: N O E X P O R T A L L;
NOEXP: N O E X P;
FASTSRT: F A S T S R T;
FSRT: F S R T;
NOFASTSRT: N O F A S T S R T;
NOFSRT: N O F S R T;
NOF: N O F;
FLAGSTD: F L A G S T D;
DN: D N;
ND: N D;
NS: N S;
SD: S D;
SN: S N;
NOFLAGSTD: N O F L A G S T D;
HGPR: H G P R;
PRESERVE: P R E S E R V E;
NOPRESERVE: N O P R E S E R V E;
INITCHECK: I N I T C H E C K;
IC: I C;
LAX: L A X;
STRICT: S T R I C T;
NOINITCHECK: N O I N I T C H E C K;
NOIC: N O I C;
INITIAL: I N I T I A L;
NOINITIAL: N O I N I T I A L;
INLINE: I N L I N E;
INL: I N L;
NOINLINE: N O I N L I N E;
NOINL: N O I N L;
INTDATE: I N T D A T E;
ANSI: A N S I;
LILIAN: L I L I A N;
INVDATA: I N V D A T A;
INVD: I N V D;
NOINVDATA: N O I N V D A T A;
NOINVD: N O I N V D;
LANGUAGE: L A N G U A G E;
LANG: L A N G;
ENGLISH: E N G L I S H;
JAPANESE: J A P A N E S E;
JA: J A;
JP: J P;
UENGLISH: U E N G L I S H;
UE: U E;
LINECOUNT: L I N E C O U N T;
LC: L C;
LIST: L I S T;
NOLIST: N O L I S T;
LP: L P;
MAP: M A P;
HEX: H E X;
DEC: D E C;
NOMAP: N O M A P;
MAXPCF: M A X P C F;
MDECK: M D E C K;
MD: M D;
NOMDECK: N O M D E C K;
NOMD: N O M D;
NAME: N A M E;
ALIAS: A L I A S;
NOALIAS: N O A L I A S;
NONAME: N O N A M E;
NSYMBOL: N S Y M B O L;
NATIONAL: N A T I O N A L;
NAT: N A T;
DBCS: D B C S;
NUMBER: N U M B E R;
NUM: N U M;
NONUMBER: N O N U M B E R;
NONUM: N O N U M;
NUMCHECK: N U M C H E C K;
NC: N C;
NONUMCHECK: N O N U M C H E C K;
NONC: N O N C;
NOPFD: N O P F D;
PFD: P F D;
OBJECT: O B J E C T;
OBJ: O B J;
NOOBJECT: N O O B J E C T;
NOOBJ: N O O B J;
OFFSET: O F F S E T;
OFF: O F F;
NOOFFSET: N O O F F S E T;
NOOFF: N O O F F;
OPTFILE: O P T F I L E;
OPTIMIZE: O P T I M I Z E;
OPT: O P T;
OUTDD: O U T D D;
OUT: O U T;
PARMCHECK: P A R M C H E C K;
PC: P C;
MSG: M S G;
ABD: A B D;
NOPARMCHECK: N O P A R M C H E C K;
NOPC: N O P C;
PGMNAME: P G M N A M E;
PGMN: P G M N;
CO: C O;
COMPAT: C O M P A T;
LM: L M;
LONGMIXED: L O N G M I X E D;
LONGUPPER: L O N G U P P E R;
LU: L U;
MIXED: M I X E D;
UPPER: U P P E R;
QUALIFY: Q U A L I F Y;
QUA: Q U A;
EXTEND: E X T E N D;
RENT: R E N T;
NORENT: N O R E N T;
RMODE: R M O D E;
ANY: A N Y;
AUTO: A U T O;
RULES: R U L E S;
NORULES: N O R U L E S;
SEQUENCE: S E Q U E N C E;
SEQ: S E Q;
NOSEQUENCE: N O S E Q U E N C E;
NOSEQ: N O S E Q;
SERVICE: S E R V I C E;
SERV: S E R V;
NOSERVICE: N O S E R V I C E;
NOSERV: N O S E R V;
SOURCE: S O U R C E;
NOSOURCE: N O S O U R C E;
NOS: N O S;
SPACE: S P A C E;
SQL: S Q L;
NOSQL: N O S Q L;
SQLCCSID: S Q L C C S I D;
SQLC: S Q L C;
NOSQLCCSID: N O S Q L C C S I D;
NOSQLC: N O S Q L C;
SQLIMS: S Q L I M S;
NOSQLIMS: N O S Q L I M S;
SSRANGE: S S R A N G E;
SSR: S S R;
NOSSRANGE: N O S S R A N G E;
NOSSR: N O S S R;
STGOPT: S T G O P T;
SO: S O;
NOSTGOPT: N O S T G O P T;
NOSO: N O S O;
SUPPRESS: S U P P R E S S;
SUPP: S U P P;
NOSUPPRESS: N O S U P P R E S S;
NOSUPP: N O S U P P;
TERMINAL: T E R M I N A L;
TERM: T E R M;
NOTERMINAL: N O T E R M I N A L;
NOTERM: N O T E R M;
TEST: T E S T;
NOTEST: N O T E S T;
THREAD: T H R E A D;
NOTHREAD: N O T H R E A D;
TRUNC: T R U N C;
BIN: B I N;
STD: S T D;
TUNE: T U N E;
VBREF: V B R E F;
NOVBREF: N O V B R E F;
VLR: V L R;
STANDARD: S T A N D A R D;
VSAMOPENFS: V S A M O P E N F S;
VS: V S;
SUCC: S U C C;
WORD: W O R D;
WD: W D;
NOWORD: N O W O R D;
NOWD: N O W D;
XMLPARSE: X M L P A R S E;
XP: X P;
XMLSS: X M L S S;
XREF: X R E F;
FULL: F U L L;
SHORT: S H O R T;
NOXREF: N O X R E F;
NOX: N O X;
ZONECHECK: Z O N E C H E C K;
ZC: Z C;
NOZONECHECK: N O Z O N E C H E C K;
NOZC: N O Z C;
ZONEDATA: Z O N E D A T A;
ZD: Z D;
ZWB: Z W B;
NOZWB: N O Z W B;

XOPTS: X O P T S;
APOST: A P O S T;
CBLCARD: C B L C A R D;
CICS: C I C S;
COBOL2: C O B O L '2';
COBOL3: C O B O L '3';
CPSM: C P S M;
DEBUG: D E B U G;
DLI: D L I;
EDF: E D F;
EXCI: E X C I;
FEPI: F E P I;
LENGTH: L E N G T H;
LINKAGE: L I N K A G E;
NOCBLCARD: N O C B L C A R D;
NOCPSM: N O C P S M;
NODEBUG: N O D E B U G;
NOEDF: N O E D F;
NOFEPI: N O F E P I;
NOLENGTH: N O L E N G T H;
NOLINKAGE: N O L I N K A G E;
NOOPTIONS: N O O P T I O N S;
NOSPIE: N O S P I E;
OPTIONS: O P T I O N S;
QUOTE: Q U O T E;
SP: S P;
SPIE: S P I E;
SYSEIB: S Y S E I B;

PLUSCHAR : '+';
MINUSCHAR : '-';
INTEGERLITERAL: (PLUSCHAR | MINUSCHAR)? DIGIT+;
INTEGERLITERAL_WITH_K: INTEGERLITERAL K;

// One-char tokens
C_CHAR: C;
D_CHAR: D;
E_CHAR: E;
F_CHAR: F;
H_CHAR: H;
I_CHAR: I;
M_CHAR: M;
N_CHAR: N;
O_CHAR: O;
Q_CHAR: Q;
S_CHAR: S;
U_CHAR: U;
W_CHAR: W;
X_CHAR: X;

EQUALCHAR : '=';
LPARENCHAR : '(';
RPARENCHAR : ')';
DOT: '.';

IDENTIFIER : [a-zA-Z0-9][-_a-zA-Z0-9]*;
FILENAME : IDENTIFIER+ '.' IDENTIFIER+;

fragment STRINGLITERAL:
	'"' (~["\n\r] | '""' | '\'')* '"'
	| '\'' (~['\n\r] | '\'\'' | '"')* '\'';

fragment DIGIT: [0-9];
fragment A:('a'|'A');
fragment B:('b'|'B');
fragment C:('c'|'C');
fragment D:('d'|'D');
fragment E:('e'|'E');
fragment F:('f'|'F');
fragment G:('g'|'G');
fragment H:('h'|'H');
fragment I:('i'|'I');
fragment J:('j'|'J');
fragment K:('k'|'K');
fragment L:('l'|'L');
fragment M:('m'|'M');
fragment N:('n'|'N');
fragment O:('o'|'O');
fragment P:('p'|'P');
fragment Q:('q'|'Q');
fragment R:('r'|'R');
fragment S:('s'|'S');
fragment T:('t'|'T');
fragment U:('u'|'U');
fragment V:('v'|'V');
fragment W:('w'|'W');
fragment X:('x'|'X');
fragment Y:('y'|'Y');
fragment Z:('z'|'Z');