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
  // Match: INPUT <NAME> <TYPE> or DEFAULT <NAME> = ...
  const inputRegex = /^\s*INPUT\s+(\w+)\s+(NUMBER|TEXT|DATE)/gim;
  const defaultRegex = /^\s*DEFAULT\s+(\w+)\s+(?:IS\s+)?[\w.'"]+\s+TO\s+(NUMBER|TEXT|DATE)/gim;

  let match: RegExpExecArray | null;

  while ((match = inputRegex.exec(code)) !== null) {
    declarations.push({ name: match[1], type: match[2] });
  }

  while ((match = defaultRegex.exec(code)) !== null) {
    declarations.push({ name: match[1], type: match[2] });
  }

  // De-duplicate by name
  const seen = new Set<string>();
  return declarations.filter((d) => {
    if (seen.has(d.name)) return false;
    seen.add(d.name);
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
      if (result.success) {
        setOutputData(result.outputs);
        setTrace(result.trace);
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
                <span style={{ color: '#ccc', fontSize: 12 }}>
                  {decl.name}{' '}
                  <span style={{ color: '#888' }}>({decl.type})</span>
                </span>
              }
            >
              <Input
                value={inputData[decl.name] ?? ''}
                onChange={(e) => setInputField(decl.name, e.target.value)}
                placeholder={decl.type === 'NUMBER' ? '0' : decl.type === 'DATE' ? 'DD-MON-YYYY' : ''}
                style={{ backgroundColor: '#2a2a2a', borderColor: '#444', color: '#e0e0e0' }}
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
