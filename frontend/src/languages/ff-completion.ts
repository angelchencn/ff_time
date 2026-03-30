import type * as Monaco from 'monaco-editor';
import { FF_KEYWORDS, FF_TYPE_KEYWORDS, FF_BUILTIN_FUNCTIONS, FF_LANGUAGE_ID } from './fast-formula';

export function registerFFCompletionProvider(
  monaco: typeof Monaco,
  dbiNames: string[]
): Monaco.IDisposable {
  return monaco.languages.registerCompletionItemProvider(FF_LANGUAGE_ID, {
    provideCompletionItems(
      model: Monaco.editor.ITextModel,
      position: Monaco.Position
    ): Monaco.languages.CompletionList {
      const word = model.getWordUntilPosition(position);
      const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const suggestions: Monaco.languages.CompletionItem[] = [
        ...FF_KEYWORDS.map((keyword) => ({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          range,
        })),
        ...FF_TYPE_KEYWORDS.map((type) => ({
          label: type,
          kind: monaco.languages.CompletionItemKind.TypeParameter,
          insertText: type,
          range,
        })),
        ...FF_BUILTIN_FUNCTIONS.map((fn) => ({
          label: fn,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: `${fn}($0)`,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail: 'Built-in function',
          range,
        })),
        ...dbiNames.map((dbi) => ({
          label: dbi,
          kind: monaco.languages.CompletionItemKind.Variable,
          insertText: dbi,
          detail: 'Database Item',
          range,
        })),
      ];

      return { suggestions };
    },
  });
}
