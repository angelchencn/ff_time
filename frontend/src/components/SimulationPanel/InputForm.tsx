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

  // Check if INPUTS ARE exists — if so, use those as the authoritative input list
  // Match INPUTS ARE and grab everything until the next statement keyword or blank line
  const inputsAreRegex = /INPUTS\s+ARE\s+([\s\S]*?)(?=\n\s*\n|\n\s*(?:IF|DEFAULT|LOCAL|OUTPUT|RETURN|WHILE|\/\*|\w+\s*=))/gi;
  let hasInputsAre = false;
  let match: RegExpExecArray | null;

  while ((match = inputsAreRegex.exec(code)) !== null) {
    hasInputsAre = true;
    // Join all lines, split by comma
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
          declarations.push({ name: nameOnly[1], type: 'NUMBER' });
        }
      }
    }
  }

  // If no INPUTS ARE, fall back to DEFAULT FOR as inputs
  if (!hasInputsAre) {
    const defaultForRegex = /^\s*DEFAULT\s+FOR\s+(\w+)\s+IS\b/gim;
    while ((match = defaultForRegex.exec(code)) !== null) {
      declarations.push({ name: match[1], type: 'NUMBER' });
    }

    // Also check INPUT IS <name>
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
      inputs[decl.name] = decl.type === 'NUMBER' ? parseFloat(raw) || 0 : raw;
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
        <p style={{ color: '#888', fontSize: 13 }}>
          No INPUT or DEFAULT declarations found. Add them to your formula to generate input fields.
        </p>
      ) : (
        <Form layout="vertical" size="small">
          {declarations.map((decl) => (
            <Form.Item
              key={decl.name}
              label={
                <span style={{ color: '#333', fontSize: 12 }}>
                  {decl.name}{' '}
                  <span style={{ color: '#999' }}>({decl.type})</span>
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
