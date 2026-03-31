import { ConfigProvider } from 'antd';
import { AppLayout } from './components/Layout/AppLayout';
import { useAutoSave } from './hooks/useAutoSave';

function App() {
  useAutoSave();
  return (
    <ConfigProvider
      theme={{
        token: {
          colorBgContainer: '#ffffff',
          colorBgElevated: '#fafafa',
          colorBorder: '#e0e0e0',
          colorText: '#1a1a1a',
          colorTextSecondary: '#666',
          colorPrimary: '#1677ff',
          borderRadius: 6,
          fontSize: 13,
        },
      }}
    >
      <AppLayout />
    </ConfigProvider>
  );
}

export default App;
