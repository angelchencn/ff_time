import type * as Monaco from 'monaco-editor';

export const FF_LANGUAGE_ID = 'fast-formula';

export const FF_KEYWORDS = [
  'DEFAULT', 'FOR', 'IS', 'INPUT', 'OUTPUT', 'LOCAL',
  'IF', 'THEN', 'ELSE', 'ELSIF', 'ENDIF',
  'WHILE', 'LOOP', 'ENDLOOP', 'RETURN',
  'AND', 'OR', 'NOT', 'WAS', 'DEFAULTED',
];

export const FF_TYPE_KEYWORDS = ['NUMBER', 'TEXT', 'DATE'];

export const FF_BUILTIN_FUNCTIONS = [
  'TO_NUMBER', 'TO_CHAR', 'TO_DATE',
  'ROUND', 'ABS', 'GREATEST', 'LEAST',
  'LENGTH', 'SUBSTR', 'INSTR', 'RPAD', 'LPAD',
  'UPPER', 'LOWER', 'TRIM', 'LTRIM', 'RTRIM',
  'ADD_MONTHS', 'MONTHS_BETWEEN', 'LAST_DAY',
  'NEXT_DAY', 'TRUNC', 'SYSDATE',
  'GET_TABLE_VALUE', 'DEFINE_BALANCE',
];

export function registerFastFormulaLanguage(monaco: typeof Monaco): void {
  // Only register once
  const languages = monaco.languages.getLanguages();
  if (languages.some((l) => l.id === FF_LANGUAGE_ID)) {
    return;
  }

  monaco.languages.register({ id: FF_LANGUAGE_ID, extensions: ['.ff'], aliases: ['Fast Formula'] });

  monaco.languages.setLanguageConfiguration(FF_LANGUAGE_ID, {
    brackets: [
      ['(', ')'],
    ],
    autoClosingPairs: [
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" },
    ],
    surroundingPairs: [
      { open: '(', close: ')' },
      { open: '"', close: '"' },
      { open: "'", close: "'" },
    ],
    comments: {
      blockComment: ['/*', '*/'],
    },
    folding: {
      markers: {
        start: /\b(IF|WHILE|LOOP)\b/,
        end: /\b(ENDIF|ENDLOOP)\b/,
      },
    },
  });

  monaco.languages.setMonarchTokensProvider(FF_LANGUAGE_ID, {
    keywords: FF_KEYWORDS,
    typeKeywords: FF_TYPE_KEYWORDS,
    builtins: FF_BUILTIN_FUNCTIONS,
    ignoreCase: true,

    tokenizer: {
      root: [
        // Comments
        [/\/\*/, 'comment', '@comment'],

        // Strings
        [/"([^"\\]|\\.)*$/, 'string.invalid'],
        [/"/, 'string', '@string_double'],
        [/'([^'\\]|\\.)*$/, 'string.invalid'],
        [/'/, 'string', '@string_single'],

        // Numbers
        [/\d+\.\d*([eE][+-]?\d+)?/, 'number.float'],
        [/\.\d+([eE][+-]?\d+)?/, 'number.float'],
        [/\d+[eE][+-]?\d+/, 'number.float'],
        [/\d+/, 'number'],

        // Dates
        [/'\d{2}-\w{3}-\d{4}'/, 'string.date'],

        // Identifiers / keywords
        [
          /[a-zA-Z_][\w$]*/,
          {
            cases: {
              '@keywords': 'keyword',
              '@typeKeywords': 'type',
              '@builtins': 'support.function',
              '@default': 'identifier',
            },
          },
        ],

        // Operators
        [/[+\-*/=<>!]+/, 'operator'],
        [/[(),.;]/, 'delimiter'],

        // Whitespace
        [/[ \t\r\n]+/, 'white'],
      ],

      comment: [
        [/[^/*]+/, 'comment'],
        [/\*\//, 'comment', '@pop'],
        [/[/*]/, 'comment'],
      ],

      string_double: [
        [/[^\\"]+/, 'string'],
        [/\\./, 'string.escape'],
        [/"/, 'string', '@pop'],
      ],

      string_single: [
        [/[^\\']+/, 'string'],
        [/\\./, 'string.escape'],
        [/'/, 'string', '@pop'],
      ],
    },
  });
}
