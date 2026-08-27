/*
* Copyright (C) 2017, Ulrich Wolffgang <ulrich.wolffgang@proleap.io>
* All rights reserved.
*
* This software may be modified and distributed under the terms
* of the MIT license. See the LICENSE file for details.
*/

parser grammar CobolExpressionParser;
options {tokenVocab = CobolLexer; }

startRule : arithmeticExpression EOF;

specialRegister
   : ADDRESS OF generalIdentifier
   | LENGTH OF? generalIdentifier | LINAGE_COUNTER
   ;

generalIdentifier
   : specialRegister | qualifiedDataName | functionCall
   ;

qualifiedDataName
   : variableUsageName tableCall? referenceModifier? inData*
   ;

inData
   : (IN | OF) variableUsageName tableCall? referenceModifier?
   ;

tableCall
   : LPARENCHAR (ALL | arithmeticExpression) (COMMACHAR? (ALL | arithmeticExpression))* RPARENCHAR
   ;

variableUsageName
   : cobolWord
   ;

referenceModifier
   : LPARENCHAR characterPosition COLONCHAR length? RPARENCHAR
   ;

characterPosition
   : arithmeticExpression
   ;

length
   : arithmeticExpression
   ;

argument
   : arithmeticExpression
   | TRAILING | LEADING
   ;

functionCall
   : FUNCTION functionName (LPARENCHAR argument (COMMACHAR? argument)* RPARENCHAR)* referenceModifier?
   ;

functionName
   : INTEGER | LENGTH | RANDOM | SUM | MAX | WHEN_COMPILED | cobolWord
   ;

// arithmetic expression ----------------------------------

arithmeticExpression
   : multDivs plusMinus*
   ;

plusMinus
   : (PLUSCHAR | MINUSCHAR) multDivs
   ;

multDivs
   : powers multDiv*
   ;

multDiv
   : (ASTERISKCHAR | SLASHCHAR) powers
   ;

powers
   : (PLUSCHAR | MINUSCHAR)? basis power*
   ;

power
   : DOUBLEASTERISKCHAR basis
   ;

basis
   : dialectNodeFiller | generalIdentifier | literal | LPARENCHAR arithmeticExpression RPARENCHAR
   ;

cobolWord
   : IDENTIFIER | SYMBOL | INTEGER | CHANNEL | PROCESS | REMOVE | WAIT | ANY | LIST | NAME | THREAD | U_CHAR
   | cobolKeywords
   ;

cobolKeywords
   : ADDRESS | BOTTOM | COUNT | CR | FIELD | FIRST | MMDDYYYY | PRINTER | DAY | TIME | DATE | DAY_OF_WEEK
   | REMARKS | RESUME | TIMER | TODAYS_DATE | TODAYS_NAME | TOP | YEAR | YYYYDDD | YYYYMMDD | WHEN_COMPILED
   | DISK | KEYBOARD | PORT | READER | REMOTE | VIRTUAL | LIBRARY | DEFINITION | PARSE | BOOL | ESCAPE | INITIALIZED
   | LOC | BYTITLE | BYFUNCTION | ABORT | ORDERLY | ASSOCIATED_DATA | ASSOCIATED_DATA_LENGTH | CLOSE | CURRENCY
   | DATA | DBCS | EXIT | EXTEND | INITIAL | NATIONAL | OBJECT | OFF | QUOTE | SEPARATE | SEQUENCE
   | SERVICE | STANDARD | SUPPRESS | TERMINAL | TEST | VOLATILE
   ;

symbolicCharacter
   : cobolWord
   ;

figurativeConstant
   : ALL literal | HIGH_VALUE | HIGH_VALUES | LOW_VALUE | LOW_VALUES | NULL | NULLS | QUOTE | QUOTES | SPACE | SPACES | ZEROS | ZEROES | ZERO
   ;

booleanLiteral
   : TRUE | FALSE
   ;

numericLiteral
   : NUMERICLITERAL | ZERO | integerLiteral
   ;

integerLiteral
   : INTEGERLITERAL | LEVEL_NUMBER | LEVEL_NUMBER_66 | LEVEL_NUMBER_77 | LEVEL_NUMBER_88
   ;

//cics_conditions: EOC | EODS | INVMPSZ | INVPARTN | INVREQ | MAPFAIL | PARTNFAIL | RDATT | UNEXPIN;

literal
   : NONNUMERICLITERAL | figurativeConstant | numericLiteral | booleanLiteral | charString | dialectLiteral | utfLiteral | hexadecimalUtfLiteral
   ;

dialectLiteral: dialectNodeFiller+;

utfLiteral: U_CHAR NONNUMERICLITERAL;

hexadecimalUtfLiteral: U_CHAR HEX_NUMBERS;

charString
   : FINALCHARSTRING
   ;

dialectNodeFiller
    : (ZERO_WIDTH_SPACE)+
    ;

