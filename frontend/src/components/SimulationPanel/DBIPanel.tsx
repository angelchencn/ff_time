import { useEffect, useState, useMemo } from 'react';
import { Input, Table, Tag, Typography, Select, Space } from 'antd';
import { SearchOutlined, DatabaseOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import axios from 'axios';
import { useServerStore } from '../../stores/serverStore';

const { Text } = Typography;

interface DBI {
  name: string;
  data_type: string;
  module: string;
  description: string;
}

const TYPE_COLORS: Record<string, string> = {
  NUMBER: 'blue',
  TEXT: 'green',
  DATE: 'orange',
};

const MODULE_COLORS: Record<string, string> = {
  TIME_LABOR: 'purple',
  PERSON: 'cyan',
  PAYROLL: 'gold',
  ABSENCE: 'magenta',
  BENEFITS: 'lime',
  COMPENSATION: 'volcano',
  RECRUITING: 'geekblue',
  OTHER: 'default',
};

export function DBIPanel() {
  const { current } = useServerStore();
  const [allItems, setAllItems] = useState<DBI[]>([]);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [moduleFilter, setModuleFilter] = useState<string | undefined>(undefined);
  const [typeFilter, setTypeFilter] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [modules, setModules] = useState<{ module: string; count: number }[]>([]);

  function getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    return headers;
  }

  // Load modules for filter dropdown
  useEffect(() => {
    axios.get(`${current.baseUrl}${current.apiPrefix}/dbi/modules`, { headers: getHeaders() }).then((r) => setModules(r.data)).catch(() => {});
  }, [current]);

  // Load DBIs
  useEffect(() => {
    setLoading(true);
    const params: Record<string, string | number> = { limit: 50000 };
    if (search) params.search = search;
    if (moduleFilter && moduleFilter !== '') params.module = moduleFilter;
    if (typeFilter && typeFilter !== '') params.data_type = typeFilter;

    axios
      .get(`${current.baseUrl}${current.apiPrefix}/dbi`, { params, headers: getHeaders() })
      .then((r) => {
        setAllItems(r.data.items ?? r.data);
        setTotal(r.data.total ?? (r.data.items ?? r.data).length);
      })
      .catch(() => setAllItems([]))
      .finally(() => setLoading(false));
  }, [search, moduleFilter, typeFilter, current]);

  const columns: ColumnsType<DBI> = useMemo(() => [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      sorter: (a: DBI, b: DBI) => a.name.localeCompare(b.name),
      defaultSortOrder: 'ascend',
      render: (name: string) => (
        <Text strong style={{ fontSize: 11, fontFamily: 'var(--font-mono)' }}>{name}</Text>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'data_type',
      key: 'data_type',
      width: 80,
      sorter: (a: DBI, b: DBI) => a.data_type.localeCompare(b.data_type),
      render: (type: string) => (
        <Tag color={TYPE_COLORS[type] ?? 'default'} style={{ fontSize: 10 }}>{type}</Tag>
      ),
    },
    {
      title: 'Module',
      dataIndex: 'module',
      key: 'module',
      width: 110,
      sorter: (a: DBI, b: DBI) => a.module.localeCompare(b.module),
      render: (module: string) => (
        <Tag color={MODULE_COLORS[module] ?? 'default'} style={{ fontSize: 10 }}>{module}</Tag>
      ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      sorter: (a: DBI, b: DBI) => (a.description || '').localeCompare(b.description || ''),
      render: (desc: string) => (
        <Text style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{desc}</Text>
      ),
    },
  ], []);

  return (
    <div style={{ padding: '12px 16px', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
        <DatabaseOutlined style={{ color: 'var(--text-tertiary)' }} />
        <Text style={{ fontSize: 12, color: 'var(--text-tertiary)', fontFamily: 'var(--font-mono)' }}>
          {total.toLocaleString()} database items
        </Text>
      </div>

      <Space.Compact style={{ marginBottom: 8, width: '100%' }}>
        <Input
          placeholder="Search DBIs..."
          prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)' }} />}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          size="small"
          allowClear
          style={{ flex: 1 }}
        />
        <Select
          value={moduleFilter}
          onChange={setModuleFilter}
          size="small"
          allowClear
          placeholder="Module"
          style={{ width: 130 }}
          options={[
            { value: '', label: `All (${modules.reduce((s, m) => s + m.count, 0)})` },
            ...modules.map((m) => ({
              value: m.module,
              label: `${m.module} (${m.count})`,
            })),
          ]}
        />
        <Select
          value={typeFilter}
          onChange={setTypeFilter}
          size="small"
          allowClear
          placeholder="Type"
          style={{ width: 100 }}
          options={[
            { value: 'NUMBER', label: 'NUMBER' },
            { value: 'TEXT', label: 'TEXT' },
            { value: 'DATE', label: 'DATE' },
          ]}
        />
      </Space.Compact>

      <div style={{ flex: 1, overflow: 'hidden' }}>
        <Table
          dataSource={allItems}
          columns={columns}
          rowKey="name"
          size="small"
          loading={loading}
          pagination={{ pageSize: 50, size: 'small', showTotal: (t) => `${t} items`, showSizeChanger: false }}
          scroll={{ y: 'calc(100vh - 320px)' }}
          style={{ fontSize: 11 }}
        />
      </div>
    </div>
  );
}
