grammar CSL;

@header { package org.vnu.sme.goal.dsl.csl.parser; }

// CSL core v4 — shared organizational structure and state.
// Object has exactly three concrete kinds: Entity, Role and OrgCtx.
// OrgCtx owns declarations, NOT a fixed population of runtime instances.
// Role specialization is UML-style inheritance of the SAME role instance.
// Agent/player/enactment belong to M0 and have no declaration syntax here.
//
// Associations and state invariants belong to CslModel, outside orgContext.
// Entity/Role/OrgCtx names are model-wide identifiers; forward references work.
// CslSemanticValidator resolves them and checks the Ecore well-formedness rules:
//   same-kind acyclic inheritance, acyclic context nesting, unique names,
//   entity-centered associations (at most one non-Entity end), valid bounds.
// Those reference-dependent rules cannot be enforced by a context-free rule.
// Grammar itself enforces containment, attribute modifiers and association arity.
// No Group, membership counts, canPlay or process operations in the new core.

// Canonical Composite State Language entry point.
model : 'csl' VERSION IDENT '{' topLevelDecl* '}' EOF ;

topLevelDecl
    : enumDecl
    | datatypeDecl
    | entityDecl
    | roleDecl
    | orgContextDecl
    | entityRelationDecl
    | compatibilityDecl
    | invariantDecl
    ;

enumDecl : 'enum' IDENT '{' IDENT (',' IDENT)* ','? '}' ;
datatypeDecl : 'datatype' IDENT ';' ;

entityDecl : 'entity' IDENT specializesClause? (';' | attributeBlock) ;
roleDecl : 'role' IDENT specializesClause? (';' | attributeBlock) ;
// Single inheritance; Ecore generalization references also have upper bound 1.
specializesClause : ('specializes' | 'extends') IDENT ;

// OrgCtx inherits Object.name and Object.attributes. Nesting is containment,
// not generalization and not a generic OrgCtx--OrgCtx association.
orgContextDecl : 'orgContext' IDENT '{' orgContextItem* '}' ;
orgContextItem
    : attributeDecl
    | entityDecl
    | roleDecl
    | orgContextDecl
    | compatibilityDecl
    ;

attributeBlock : '{' attributeDecl* '}' ;
attributeDecl : 'attribute'? IDENT ':' IDENT attributeModifier? defaultClause? ';' ;
// Scalar properties are [1] by default; optional means [0..1]. Each modifier
// occurs at most once; optional/required cannot occur together.
attributeModifier
    : ('optional' | 'required') 'mutable'?
    | 'mutable' ('optional' | 'required')?
    ;
defaultClause : 'default' defaultValue ;
defaultValue : STRING_LITERAL | OCL_STRING | '-'? (INT | REAL) | BOOLEAN | IDENT ;

// The first end of aggregation/composition is the whole. These UML forms are
// binary; ordinary associations may have 2..* ordered ends, as in Ecore.
entityRelationDecl
    : 'association' IDENT '{' endpointDecl endpointDecl+ '}'
    | relationKind IDENT '{' endpointDecl endpointDecl '}'
    ;
relationKind : 'aggregation' | 'composition' ;
endpointDecl : IDENT cardinality ('role'? IDENT)? ';' ;

// Stored symmetrically on the two Role definitions. Placement in an OrgCtx
// is shorthand only: compatibility is not an association or a runtime link.
compatibilityDecl : IDENT 'compatible' IDENT ';' ;

// Non-negative integer bounds; '*' means upper=-1. Resolution additionally
// checks lower<=upper (unless unbounded). UML [0..0] is valid.
cardinality
    : '[' INT ']'
    | '[' INT '..' (INT | '*') ']'
    | '[' '*' ']'
    ;

// These are M1 domain-state invariants, distinct from M2 Ecore constraints.
// Bodies are captured for the OCL parser; CSL does not define behavior.
invariantDecl : 'context' IDENT 'inv' IDENT ':' oclExpression ';' ;
oclExpression : oclToken+ ;
oclToken
    : IDENT | oclKeyword | STRING_LITERAL | OCL_STRING | INT | REAL | BOOLEAN
    | '.' | '->' | '(' | ')' | '[' | ']' | '{' | '}'
    | ',' | '|' | '@' | '#' | '::' | ':'
    | '=' | '<>' | '<' | '<=' | '>' | '>='
    | '+' | '-' | '*' | '/'
    ;
oclKeyword
    : 'csl' | 'enum' | 'datatype' | 'entity' | 'role'
    | 'specializes' | 'extends' | 'orgContext'
    | 'association' | 'aggregation' | 'composition' | 'compatible'
    | 'context' | 'inv' | 'attribute' | 'optional' | 'required'
    | 'mutable' | 'default'
    ;

VERSION : 'v' [0-9]+ '.' [0-9]+ ;
BOOLEAN : 'true' | 'false' ;
// The sign is parsed separately so x-1 is never lexed as IDENT SIGNED_NUMBER.
REAL : [0-9]+ '.' [0-9]+ ;
INT : [0-9]+ ;
STRING_LITERAL : '"' ('\\' . | ~["\\\r\n])* '"' ;
OCL_STRING : '\'' ('\'\'' | '\\' . | ~['\\\r\n])* '\'' ;
IDENT : [a-zA-Z_] [a-zA-Z0-9_]* ;
WS : [ \t\r\n\f]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
