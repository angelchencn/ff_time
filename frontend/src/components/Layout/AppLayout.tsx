import { useCallback, useRef, useState } from 'react';
import { EditorWithChat } from '../Editor/EditorWithChat';
import { SimulationPanel } from '../SimulationPanel/SimulationPanel';
import { TemplatesPanel } from '../Templates/TemplatesPanel';
import { Toolbar } from './Toolbar';
import { StatusBar } from './StatusBar';
import { useValidation } from '../../hooks/useValidation';

function DragHandle({ onDrag }: { onDrag: (dx: number) => void }) {
  const dragging = useRef(false);
  const lastX = useRef(0);

  const onMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      dragging.current = true;
      lastX.current = e.clientX;

      const onMouseMove = (ev: MouseEvent) => {
        if (!dragging.current) return;
        const dx = ev.clientX - lastX.current;
        lastX.current = ev.clientX;
        onDrag(dx);
      };

      const onMouseUp = () => {
        dragging.current = false;
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    },
    [onDrag]
  );

  return (
    <div
      onMouseDown={onMouseDown}
      style={{
        width: 5,
        cursor: 'col-resize',
        backgroundColor: 'transparent',
        flexShrink: 0,
        position: 'relative',
        zIndex: 10,
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: 0,
          bottom: 0,
          left: 2,
          width: 1,
          backgroundColor: 'var(--border-default)',
        }}
      />
    </div>
  );
}

export function AppLayout() {
  useValidation();

  const [rightWidth, setRightWidth] = useState(360);
  const [showTemplates, setShowTemplates] = useState(false);

  const handleRightDrag = useCallback((dx: number) => {
    setRightWidth((w) => Math.max(240, Math.min(600, w - dx)));
  }, []);

  if (showTemplates) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100vh',
          backgroundColor: 'var(--bg-base)',
          overflow: 'hidden',
        }}
      >
        <TemplatesPanel onBack={() => setShowTemplates(false)} />
        <StatusBar />
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100vh',
        backgroundColor: 'var(--bg-base)',
        overflow: 'hidden',
      }}
    >
      <Toolbar onManageTemplates={() => setShowTemplates(true)} />

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: Editor + Chat input */}
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', minWidth: 300 }}>
          <EditorWithChat />
        </div>

        <DragHandle onDrag={handleRightDrag} />

        {/* Right: Simulation Panel */}
        <div
          style={{
            width: rightWidth,
            minWidth: 240,
            overflow: 'hidden',
            flexShrink: 0,
          }}
        >
          <SimulationPanel />
        </div>
      </div>

      <StatusBar />
    </div>
  );
}
