import { useMemo } from 'react';
import { Button, Form, Input } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { useEditorStore } from '../../stores/editorStore';
import { useSimulationStore } from '../../stores/simulationStore';
import { simulateCode } from '../../services/api';

interface InputDeclaration {
  name: string;
  type: string;
}

function parseInputDeclarations(code: string): InputDeclaration[] {
  const declarations: InputDeclaration[] = [];

  let match: RegExpExecArray | null;

  // First pass: build a type map from DEFAULT FOR lines
  // Matches: DEFAULT FOR name (TYPE) IS value OR DEFAULT FOR name IS value (TYPE)
  const typeMap: Record<string, string> = {};
  const defaultRegex = /^\s*DEFAULT\s+FOR\s+(\w+)\s+(?:\(\s*(NUMBER|TEXT|DATE)\s*\)\s+)?IS\s+(.*?)$/gim;
  while ((match = defaultRegex.exec(code)) !== null) {
    const name = match[1].toUpperCase();
    const explicitType = match[2]?.toUpperCase();
    const value = match[3].trim();

    if (explicitType) {
      typeMap[name] = explicitType;
    } else {
      // Infer type from the default value
      const trailingType = value.match(/\(\s*(NUMBER|TEXT|DATE)\s*\)\s*$/i);
      if (trailingType) {
        typeMap[name] = trailingType[1].toUpperCase();
      } else if (/^'/.test(value)) {
        // Starts with a quote → TEXT
        typeMap[name] = 'TEXT';
      } else {
        typeMap[name] = 'NUMBER';
      }
    }
  }

  // Check if INPUTS ARE exists
  const inputsAreRegex = /INPUTS\s+ARE\s+([\s\S]*?)(?=\n\s*\n|\n\s*(?:IF|DEFAULT|LOCAL|OUTPUT|RETURN|WHILE|\/\*|\w+\s*=))/gi;
  let hasInputsAre = false;

  while ((match = inputsAreRegex.exec(code)) !== null) {
    hasInputsAre = true;
    const raw = match[1].replace(/\n/g, ' ');
    const parts = raw.split(',');
    for (const part of parts) {
      const cleaned = part.trim();
      if (!cleaned) continue;
      const typeMatch = cleaned.match(/^(\w+)\s*\(\s*(NUMBER|TEXT|DATE)\s*\)/i);
      if (typeMatch) {
        declarations.push({ name: typeMatch[1], type: typeMatch[2].toUpperCase() });
      } else {
        const nameOnly = cleaned.match(/^(\w+)/);
        if (nameOnly) {
          // Look up type from DEFAULT FOR, fallback to NUMBER
          const resolvedType = typeMap[nameOnly[1].toUpperCase()] || 'NUMBER';
          declarations.push({ name: nameOnly[1], type: resolvedType });
        }
      }
    }
  }

  // If no INPUTS ARE, only show INPUT IS variables (not DEFAULT FOR)
  if (!hasInputsAre) {
    const inputIsRegex = /^\s*INPUT\s+IS\s+(\w+)/gim;
    while ((match = inputIsRegex.exec(code)) !== null) {
      declarations.push({ name: match[1], type: 'NUMBER' });
    }
  }

  // De-duplicate by name (case-insensitive)
  const seen = new Set<string>();
  return declarations.filter((d) => {
    const key = d.name.toUpperCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function InputForm() {
  const { code } = useEditorStore();
  const { inputData, setInputField, setOutputData, setTrace, setStatus, setError } =
    useSimulationStore();

  const declarations = useMemo(() => parseInputDeclarations(code), [code]);

  async function handleRun() {
    setStatus('running');
    setError(null);

    const inputs: Record<string, string | number> = {};
    for (const decl of declarations) {
      const raw = inputData[decl.name] ?? '';
      if (decl.type === 'NUMBER') {
        inputs[decl.name] = raw ? parseFloat(raw) || 0 : 0;
      } else {
        inputs[decl.name] = raw || '';
      }
    }

    try {
      const result = await simulateCode(code, inputs);
      if (result.status === 'SUCCESS') {
        setOutputData(result.output_data);
        setTrace(result.execution_trace.map(
          (s) => `Line ${s.line}: ${s.statement} → ${s.result}`
        ));
        setStatus('success');
      } else {
        setError(result.error ?? 'Simulation failed');
        setStatus('error');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
      setStatus('error');
    }
  }

  return (
    <div style={{ padding: 16 }}>
      {declarations.length === 0 ? (
        <p style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>
          No INPUT or DEFAULT declarations found. Add them to your formula to generate input fields.
        </p>
      ) : (
        <Form layout="vertical" size="small">
          {declarations.map((decl) => (
            <Form.Item
              key={decl.name}
              label={
                <span style={{ color: 'var(--text-primary)', fontSize: 12 }}>
                  {decl.name}{' '}
                  <span style={{ color: 'var(--text-tertiary)' }}>({decl.type})</span>
                </span>
              }
            >
              <Input
                value={inputData[decl.name] ?? ''}
                onChange={(e) => setInputField(decl.name, e.target.value)}
                placeholder={decl.type === 'NUMBER' ? '0' : decl.type === 'DATE' ? 'DD-MON-YYYY' : ''}
                style={{}}
              />
            </Form.Item>
          ))}
        </Form>
      )}

      <Button
        type="primary"
        icon={<PlayCircleOutlined />}
        onClick={handleRun}
        block
        style={{ marginTop: 8 }}
      >
        Run Simulation
      </Button>
    </div>
  );
}
