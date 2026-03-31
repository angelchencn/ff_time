import type * as Monaco from 'monaco-editor';

export const FF_LANGUAGE_ID = 'fast-formula';

export const FF_KEYWORDS = [
  'ALIAS', 'AND', 'ARE', 'AS',
  'DEFAULT', 'DEFAULTED',
  'ELSE', 'ELSIF', 'END', 'ENDIF', 'ENDLOOP', 'EXECUTE', 'EXIT',
  'FOR',
  'IF', 'INPUT', 'INPUTS', 'IS',
  'LIKE', 'LOCAL', 'LOOP',
  'NOT',
  'OR', 'OUTPUT',
  'RETURN',
  'THEN',
  'USING',
  'WAS', 'WHILE',
];

export const FF_TYPE_KEYWORDS = ['NUMBER', 'TEXT', 'DATE'];

export const FF_BUILTIN_FUNCTIONS = [
  // Numeric
  'ABS', 'CEIL', 'FLOOR', 'GREATEST', 'GREATEST_OF', 'LEAST', 'LEAST_OF',
  'POWER', 'ROUND', 'ROUNDUP', 'ROUND_UP', 'TRUNC', 'TRUNCATE',
  // String
  'CHR', 'INITCAP', 'INSTR', 'INSTRB', 'LENGTH', 'LENGTHB',
  'LOWER', 'LPAD', 'LTRIM', 'REPLACE', 'RPAD', 'RTRIM',
  'SUBSTR', 'SUBSTRING', 'SUBSTRB', 'TRANSLATE', 'TRIM', 'UPPER',
  // Date
  'ADD_DAYS', 'ADD_MONTHS', 'ADD_YEARS', 'DAYS_BETWEEN', 'HOURS_BETWEEN',
  'LAST_DAY', 'MONTHS_BETWEEN', 'NEW_TIME', 'NEXT_DAY',
  // Conversion
  'CONVERT', 'DATE_TO_TEXT', 'NUM_TO_CHAR',
  'TO_CHAR', 'TO_DATE', 'TO_NUM', 'TO_NUMBER', 'TO_TEXT',
  // Lookup/Table
  'GET_LOOKUP_MEANING', 'GET_TABLE_VALUE', 'RAISE_ERROR',
  'RATES_HISTORY', 'CALCULATE_HOURS_WORKED',
  // Globals
  'SET_TEXT', 'SET_NUMBER', 'SET_DATE',
  'GET_TEXT', 'GET_NUMBER', 'GET_DATE',
  'ISNULL', 'CLEAR_GLOBALS', 'REMOVE_GLOBALS',
  // Accruals
  'GET_ABSENCE', 'GET_ACCRUAL_BAND', 'GET_CARRY_OVER',
  'GET_NET_ACCRUAL', 'GET_WORKING_DAYS', 'GET_PAYROLL_PERIOD',
  'GET_PERIOD_DATES', 'GET_START_DATE', 'GET_ASSIGNMENT_STATUS',
  // Formula
  'CALL_FORMULA', 'LOOP_CONTROL', 'PUT_MESSAGE', 'DEBUG',
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
        start: /\b(IF|WHILE|LOOP)\b/i,
        end: /\b(END\s*IF|END\s*LOOP|ENDIF|ENDLOOP)\b/i,
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
