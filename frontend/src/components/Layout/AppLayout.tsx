import { useEditorStore } from '../../stores/editorStore';
import { ChatPanel } from '../ChatPanel/ChatPanel';
import { FFEditor } from '../Editor/FFEditor';
import { SimulationPanel } from '../SimulationPanel/SimulationPanel';
import { Toolbar } from './Toolbar';
import { StatusBar } from './StatusBar';
import { useValidation } from '../../hooks/useValidation';

export function AppLayout() {
  useValidation();

  const { mode } = useEditorStore();

  const chatWidth = mode === 'chat' ? '35%' : '20%';

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        backgroundColor: '#0d0d0d',
        overflow: 'hidden',
      }}
    >
      <Toolbar />

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: Chat Panel */}
        <div
          style={{
            width: chatWidth,
            minWidth: 200,
            borderRight: '1px solid #333',
            overflow: 'hidden',
            transition: 'width 0.2s ease',
          }}
        >
          <ChatPanel />
        </div>

        {/* Center: Editor */}
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <FFEditor height="100%" />
        </div>

        {/* Right: Simulation Panel */}
        <div
          style={{
            width: '30%',
            minWidth: 240,
            borderLeft: '1px solid #333',
            overflow: 'hidden',
          }}
        >
          <SimulationPanel />
        </div>
      </div>

      <StatusBar />
    </div>
  );
}
