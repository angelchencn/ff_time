/**
 * Extract code blocks from a markdown-like string (AI response).
 * Falls back to detecting raw Fast Formula code if no fenced blocks found.
 */
export function extractCodeBlocks(text: string): string[] {
  const regex = /```[^\n]*\n([\s\S]*?)```/g;
  const blocks: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    const code = match[1].trim();
    if (code) blocks.push(code);
  }
  if (blocks.length === 0) {
    const ffKeywords = /\b(DEFAULT\s+FOR|INPUTS?\s+(IS|ARE)|OUTPUTS?\s+(IS|ARE)|RETURN|IF\s+.+\s+THEN|END\s+IF)\b/i;
    if (ffKeywords.test(text)) {
      blocks.push(text.trim());
    }
  }
  return blocks;
}
