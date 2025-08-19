import React, { useState, useEffect } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Popconfirm,
  Typography,
  Tooltip,
  Switch,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  CopyOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import axios from 'axios'
import dayjs from 'dayjs'
import { useAuthStore } from '../../store/authStore'

const { Title, Text, Paragraph } = Typography
const { Option } = Select

interface ApiKey {
  id: string
  key: string
  name: string
  description?: string
  enabled: boolean
  createdAt: string
  expiresAt?: string
  lastUsedAt?: string | null
  totalRequests?: number
  totalTokens?: number
  rateLimitRule?: {
    requestsPerMinute: number
    tokensPerDay: number
  }
  allowedClients?: string[]
}

const ApiKeys: React.FC = () => {
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingKey, setEditingKey] = useState<ApiKey | null>(null)
  const [showKeys, setShowKeys] = useState<{ [key: string]: boolean }>({})
  const [form] = Form.useForm()
  const { token, isAuthenticated } = useAuthStore()

  useEffect(() => {
    fetchKeys()
  }, [])

  const fetchKeys = async () => {
    if (!isAuthenticated || !token) {
      message.error('请先登录')
      return
    }
    
    setLoading(true)
    try {
      const response = await axios.get('/admin/api-keys')
      setKeys(response.data)
    } catch (error: any) {
      console.error('获取API Keys失败:', error)
      if (error.response?.status === 401) {
        message.error('未授权访问，请重新登录')
        // 可以在这里跳转到登录页
      } else {
        message.error('获取API Keys失败')
      }
      setKeys([]) // 设置为空数组而不是模拟数据
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = () => {
    form.resetFields()
    setEditingKey(null)
    setModalVisible(true)
  }

  const handleEdit = (record: ApiKey) => {
    setEditingKey(record)
    form.setFieldsValue(record)
    setModalVisible(true)
  }

  const handleDelete = async (id: string) => {
    try {
      // 使用ID而不是key来删除
      await axios.delete(`/admin/api-keys/id/${id}`)
      message.success('删除成功')
      fetchKeys()
    } catch (error: any) {
      if (error.response?.status === 401) {
        message.error('未授权访问，请重新登录')
      } else {
        message.error('删除失败')
      }
    }
  }

  const handleSubmit = async (values: any) => {
    try {
      if (editingKey) {
        await axios.put(`/admin/api-keys/${editingKey.id}`, values)
        message.success('更新成功')
      } else {
        const response = await axios.post('/admin/api-keys', values)
        message.success('创建成功')
        
        // 如果创建成功，显示生成的API Key
        if (response.data?.success && response.data?.api_key) {
          const apiKey = response.data.api_key
          Modal.info({
            title: '创建成功',
            content: (
              <div>
                <p>API Key 已创建成功！</p>
                <p><strong>Key:</strong> <code>{apiKey.key}</code></p>
                <p style={{ color: '#ff4d4f', fontSize: '12px' }}>
                  请保存好此 API Key，后续无法再次查看完整内容
                </p>
              </div>
            ),
            okText: '我已保存'
          })
        }
      }
      setModalVisible(false)
      fetchKeys()
    } catch (error: any) {
      console.error('API Key操作失败:', error)
      if (error.response?.status === 401) {
        message.error('未授权访问，请重新登录')
      } else {
        message.error(editingKey ? '更新失败' : '创建失败')
      }
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  }

  const toggleKeyVisibility = (id: string) => {
    setShowKeys(prev => ({ ...prev, [id]: !prev[id] }))
  }

  const maskKey = (key: string) => {
    return `${key.substring(0, 8)}...${key.substring(key.length - 8)}`
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 150,
    },
    {
      title: 'API Key',
      dataIndex: 'key',
      key: 'key',
      width: 300,
      render: (key: string, record: ApiKey) => (
        <Space>
          <Text code className="select-all">
            {showKeys[record.id] ? key : maskKey(key)}
          </Text>
          <Tooltip title={showKeys[record.id] ? '隐藏' : '显示'}>
            <Button
              size="small"
              icon={showKeys[record.id] ? <EyeInvisibleOutlined /> : <EyeOutlined />}
              onClick={() => toggleKeyVisibility(record.id)}
            />
          </Tooltip>
          <Tooltip title="复制">
            <Button
              size="small"
              icon={<CopyOutlined />}
              onClick={() => copyToClipboard(key)}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (enabled: boolean) => {
        const color = enabled ? 'success' : 'default'
        const text = enabled ? '启用' : '禁用'
        return <Tag color={color}>{text}</Tag>
      },
    },
    {
      title: '请求数',
      dataIndex: 'totalRequests',
      key: 'totalRequests',
      width: 100,
      render: (count: number) => count ? count.toLocaleString() : '0',
    },
    {
      title: '速率限制',
      dataIndex: 'rateLimitRule',
      key: 'rateLimitRule',
      width: 120,
      render: (rule: any) => rule?.requestsPerMinute ? `${rule.requestsPerMinute}/分钟` : '1000/分钟',
    },
    {
      title: '最后使用',
      dataIndex: 'lastUsedAt',
      key: 'lastUsedAt',
      width: 150,
      render: (date: string) => date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '从未使用',
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 150,
      render: (date: string) => dayjs(date).format('YYYY-MM-DD'),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right' as const,
      width: 120,
      render: (_: any, record: ApiKey) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          />
          <Popconfirm
            title="确定删除这个 API Key 吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <Title level={3}>API Key 管理</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchKeys}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            创建 API Key
          </Button>
        </Space>
      </div>

      <Card>
        <Table
          columns={columns}
          dataSource={keys}
          loading={loading}
          rowKey="id"
          scroll={{ x: 1200 }}
          pagination={{
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Card>

      <Modal
        title={editingKey ? '编辑 API Key' : '创建 API Key'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="例如：生产环境 Key" />
          </Form.Item>

          <Form.Item
            name="rateLimit"
            label="速率限制（请求/分钟）"
            rules={[{ required: true, message: '请设置速率限制' }]}
          >
            <InputNumber min={1} max={10000} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="expiresIn"
            label="有效期（天）"
            rules={[{ required: true, message: '请设置有效期' }]}
          >
            <InputNumber min={1} max={365} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="allowedClients"
            label="客户端限制（可选）"
            tooltip="留空表示不限制客户端"
          >
            <Select
              mode="tags"
              placeholder="输入客户端名称，如 Chrome, Python"
              style={{ width: '100%' }}
            />
          </Form.Item>

          <Form.Item
            name="status"
            label="状态"
            valuePropName="checked"
          >
            <Switch checkedChildren="激活" unCheckedChildren="禁用" />
          </Form.Item>

          <Form.Item className="mb-0">
            <Space className="w-full justify-end">
              <Button onClick={() => setModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">
                {editingKey ? '更新' : '创建'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ApiKeys