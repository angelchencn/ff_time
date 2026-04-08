import { ConfigProvider } from 'antd';
import { AppLayout } from './components/Layout/AppLayout';

function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorBgContainer: '#faf8f5',
          colorBgElevated: '#eee8e0',
          colorBorder: '#d6cdc2',
          colorText: '#2c1810',
          colorTextSecondary: '#5c4a3e',
          colorPrimary: '#b8602a',
          borderRadius: 6,
          fontSize: 13,
          fontFamily: "'DM Sans', -apple-system, BlinkMacSystemFont, sans-serif",
        },
        components: {
          Tabs: {
            itemColor: '#5c4a3e',
            itemActiveColor: '#b8602a',
            itemSelectedColor: '#b8602a',
            inkBarColor: '#b8602a',
          },
          Select: {
            colorBgContainer: '#faf8f5',
            colorBorder: '#d6cdc2',
            optionSelectedBg: '#b8602a15',
            optionActiveBg: '#eee8e0',
          },
          Button: {
            colorPrimary: '#b8602a',
            colorPrimaryHover: '#c96e34',
            defaultBg: '#faf8f5',
            defaultBorderColor: '#d6cdc2',
            defaultColor: '#5c4a3e',
          },
          Input: {
            colorBgContainer: '#faf8f5',
            colorBorder: '#d6cdc2',
            activeBorderColor: '#b8602a',
            activeShadow: '0 0 0 2px #b8602a15',
          },
        },
      }}
    >
      <AppLayout />
    </ConfigProvider>
  );
}

export default App;
