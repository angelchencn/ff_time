import { AppLayout } from './components/Layout/AppLayout';
import { useAutoSave } from './hooks/useAutoSave';

function App() {
  useAutoSave();
  return <AppLayout />;
}

export default App;
