import { useEffect, useState } from 'react';
import { Input, Table, Tag, Typography } from 'antd';
import { SearchOutlined, DatabaseOutlined } from '@ant-design/icons';
import { fetchDBIs, type DBI } from '../../services/api';

const { Text } = Typography;

const TYPE_COLORS: Record<string, string> = {
  NUMBER: 'blue',
  TEXT: 'green',
  DATE: 'orange',
};

const MODULE_COLORS: Record<string, string> = {
  TIME_LABOR: 'purple',
  PERSON: 'cyan',
  PAYROLL: 'gold',
};

const columns = [
  {
    title: 'Name',
    dataIndex: 'name',
    key: 'name',
    render: (name: string) => (
      <Text strong style={{ fontSize: 12, fontFamily: 'monospace' }}>{name}</Text>
    ),
  },
  {
    title: 'Type',
    dataIndex: 'data_type',
    key: 'data_type',
    width: 80,
    render: (type: string) => (
      <Tag color={TYPE_COLORS[type] ?? 'default'} style={{ fontSize: 11 }}>
        {type}
      </Tag>
    ),
  },
  {
    title: 'Module',
    dataIndex: 'module',
    key: 'module',
    width: 100,
    render: (module: string) => (
      <Tag color={MODULE_COLORS[module] ?? 'default'} style={{ fontSize: 10 }}>
        {module}
      </Tag>
    ),
  },
  {
    title: 'Description',
    dataIndex: 'description',
    key: 'description',
    render: (desc: string) => (
      <Text style={{ fontSize: 11, color: '#666' }}>{desc}</Text>
    ),
  },
];

export function DBIPanel() {
  const [dbis, setDbis] = useState<DBI[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchDBIs()
      .then((data) => setDbis(data as DBI[]))
      .catch(() => setDbis([]))
      .finally(() => setLoading(false));
  }, []);

  const filtered = search
    ? dbis.filter(
        (d) =>
          d.name.toLowerCase().includes(search.toLowerCase()) ||
          (d.description ?? '').toLowerCase().includes(search.toLowerCase()) ||
          (d.module ?? '').toLowerCase().includes(search.toLowerCase())
      )
    : dbis;

  return (
    <div style={{ padding: '12px 16px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        <DatabaseOutlined style={{ color: '#999' }} />
        <Text style={{ fontSize: 12, color: '#999' }}>{dbis.length} database items</Text>
      </div>

      <Input
        placeholder="Search DBIs..."
        prefix={<SearchOutlined style={{ color: '#bbb' }} />}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        size="small"
        allowClear
        style={{ marginBottom: 8 }}
      />

      <div style={{ flex: 1, overflowY: 'auto' }}>
        <Table
          dataSource={filtered}
          columns={columns}
          rowKey="name"
          size="small"
          loading={loading}
          pagination={false}
          scroll={{ y: 'calc(100vh - 260px)' }}
          style={{ fontSize: 12 }}
        />
      </div>
    </div>
  );
}
