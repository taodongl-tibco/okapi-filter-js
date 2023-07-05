lexer grammar JavaScriptLexer;

tokens { COMMENT, SEPARATOR, WHITE_SPACE, NULL, BOOL, ID, STRING}

MultiLineComment:               '/*' .*? '*/'             -> type(COMMENT);
SingleLineComment:              '//' ~[\r\n\u2028\u2029]* -> type(COMMENT);
Comma:                          (',' | ':')               -> type(SEPARATOR);
OBJECT_START:                   '{'                       ;
OBJECT_END:                     '}'                       ;
WhiteSpaces:                    [\t\u000B\u000C\u0020\u00A0]+ -> type(WHITE_SPACE);
LineTerminator:                 [\r\n\u2028\u2029]            -> type(WHITE_SPACE);
NullLiteral:                    'null'                    -> type(NULL);
BooleanLiteral:                 ('true'|'false')          -> type(BOOL);
Identifier:                     IdentifierStart IdentifierPart* -> type(ID);
/// String Literals
StringLiteral:                 ('"' DoubleStringCharacter* '"' | '\'' SingleStringCharacter* '\'') -> type(STRING);
OTHERS:                         .;
// Fragment rules

fragment DoubleStringCharacter
    : ~["\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
    ;

fragment SingleStringCharacter
    : ~['\\\r\n]
    | '\\' EscapeSequence
    | LineContinuation
    ;

fragment EscapeSequence
    : CharacterEscapeSequence
    | '0' // no digit ahead! TODO
    | HexEscapeSequence
    | UnicodeEscapeSequence
    | ExtendedUnicodeEscapeSequence
    ;

fragment CharacterEscapeSequence
    : SingleEscapeCharacter
    | NonEscapeCharacter
    ;

fragment HexEscapeSequence
    : 'x' HexDigit HexDigit
    ;

fragment UnicodeEscapeSequence
    : 'u' HexDigit HexDigit HexDigit HexDigit
    | 'u' '{' HexDigit HexDigit+ '}'
    ;

fragment ExtendedUnicodeEscapeSequence
    : 'u' '{' HexDigit+ '}'
    ;

fragment SingleEscapeCharacter
    : ['"\\bfnrtv]
    ;

fragment NonEscapeCharacter
    : ~['"\\bfnrtv0-9xu\r\n]
    ;

fragment EscapeCharacter
    : SingleEscapeCharacter
    | [0-9]
    | [xu]
    ;

fragment LineContinuation
    : '\\' [\r\n\u2028\u2029]
    ;

fragment HexDigit
    : [_0-9a-fA-F]
    ;

fragment DecimalIntegerLiteral
    : '0'
    | [1-9] [0-9_]*
    ;

fragment ExponentPart
    : [eE] [+-]? [0-9_]+
    ;

fragment IdentifierPart
    : IdentifierStart
    | [\p{Mn}]
    | [\p{Nd}]
    | [\p{Pc}]
    | '\u200C'
    | '\u200D'
    ;

fragment IdentifierStart
    : [\p{L}]
    | [$_]
    | '\\' UnicodeEscapeSequence
    ;