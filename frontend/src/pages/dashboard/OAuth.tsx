import React, { useState, useEffect } from 'react'
import {
  Card,
  Button,
  Steps,
  Form,
  Input,
  Alert,
  Space,
  Typography,
  message,
  Spin,
  Result,
  Divider,
  Table,
  Tag,
  Switch,
  Popconfirm,
  Modal,
  Select,
  Empty,
  Badge,
  Row,
  Col,
  Statistic,
} from 'antd'
import {
  SafetyOutlined,
  LinkOutlined,
  KeyOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  ArrowRightOutlined,
  DeleteOutlined,
  ReloadOutlined,
  PlusOutlined,
  UserOutlined,
  ApiOutlined,
  ClockCircleOutlined,
  CheckOutlined,
  CloseOutlined,
  GlobalOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import axios from 'axios'

const { Title, Text, Paragraph } = Typography
const { Step } = Steps

interface AuthorizationData {
  authorization_url: string
  code_verifier: string
  state: string
  success: boolean
  instructions: string
}

interface ClaudeAccount {
  id: string
  email: string
  accessToken: string
  refreshToken: string
  tokenExpiresAt: string
  createdAt: string
  lastUsedAt: string
  enabled: boolean
  totalRequests: number
  totalTokens: number
  status: string
  type?: string
}

const OAuth: React.FC = () => {
  const [currentStep, setCurrentStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [authData, setAuthData] = useState<AuthorizationData | null>(null)
  const [authCode, setAuthCode] = useState('')
  const [accountAdded, setAccountAdded] = useState(false)
  const [accounts, setAccounts] = useState<ClaudeAccount[]>([])
  const [accountsLoading, setAccountsLoading] = useState(false)
  const [addModalVisible, setAddModalVisible] = useState(false)
  const [selectedAccountType, setSelectedAccountType] = useState<string>('')
  const [reauthAccountId, setReauthAccountId] = useState<string | null>(null)
  const [reauthAccountEmail, setReauthAccountEmail] = useState<string>('')
  const [form] = Form.useForm()

  useEffect(() => {
    fetchAccounts()
  }, [])

  const fetchAccounts = async () => {
    setAccountsLoading(true)
    try {
      const response = await axios.get('/oauth/accounts')
      // 确保response.data是数组
      if (Array.isArray(response.data)) {
        setAccounts(response.data)
      } else {
        setAccounts([])
        console.warn('Accounts response is not an array:', response.data)
      }
    } catch (error) {
      console.error('Failed to fetch accounts:', error)
      setAccounts([])
    } finally {
      setAccountsLoading(false)
    }
  }

  const handleStatusChange = async (id: string, enabled: boolean) => {
    try {
      await axios.put(`/oauth/accounts/${id}/status`, {
        enabled: enabled.toString()
        // 后端会自动保证状态一致性，不需要前端指定status
      })
      message.success(enabled ? '账号已启用' : '账号已停用')
      fetchAccounts()
    } catch (error) {
      message.error('更新状态失败')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await axios.delete(`/oauth/accounts/${id}`)
      message.success('账号已删除')
      fetchAccounts()
    } catch (error) {
      message.error('删除失败')
    }
  }

  const handleReauth = (account: ClaudeAccount) => {
    setReauthAccountId(account.id)
    setReauthAccountEmail(account.email)
    setSelectedAccountType('claude')
    setAddModalVisible(true)
    generateAuthUrl()
  }

  const showAddModal = () => {
    setAddModalVisible(true)
    setCurrentStep(0)
    setAuthData(null)
    setAuthCode('')
    setAccountAdded(false)
    setReauthAccountId(null)
    setReauthAccountEmail('')
  }

  const handleAccountTypeSelect = (type: string) => {
    setSelectedAccountType(type)
    if (type === 'claude') {
      generateAuthUrl()
    } else {
      message.info(`${type} 账号类型即将支持`)
      setAddModalVisible(false)
    }
  }

  const generateAuthUrl = async () => {
    setLoading(true)
    try {
      const response = await axios.get('/admin/accounts/authorize-url')
      setAuthData(response.data)
      setCurrentStep(1)
      message.success('授权链接已生成')
    } catch (error: any) {
      console.error('OAuth error:', error.response)
      if (error.response?.status === 401) {
        message.error('请先登录')
        setTimeout(() => {
          window.location.href = '/login'
        }, 1500)
      } else {
        message.error(error.response?.data?.message || '生成授权链接失败')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleAuthCallback = async () => {
    if (!authCode || !authData) {
      message.error('请输入授权码')
      return
    }

    setLoading(true)
    try {
      const requestBody: any = {
        code: authCode,
        code_verifier: authData.code_verifier,
      }
      
      // 如果是重新授权，添加账号ID
      if (reauthAccountId) {
        requestBody.account_id = reauthAccountId
        requestBody.update_existing = true
      }
      
      const response = await axios.post('/oauth/token', requestBody)
      
      if (response.data.success) {
        setAccountAdded(true)
        setCurrentStep(2)
        message.success(reauthAccountId ? '账号重新授权成功！' : '账号添加成功！')
        // 如果返回了账号列表，更新本地状态
        if (response.data.accounts) {
          setAccounts(response.data.accounts)
        } else {
          fetchAccounts()
        }
      } else {
        message.error(response.data.error || '操作失败')
      }
    } catch (error: any) {
      message.error(error.response?.data?.error || '操作失败')
    } finally {
      setLoading(false)
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  }

  const resetFlow = () => {
    setCurrentStep(0)
    setAuthData(null)
    setAuthCode('')
    setAccountAdded(false)
    setSelectedAccountType('')
    setAddModalVisible(false)
    form.resetFields()
  }

  const openAuthUrl = () => {
    if (authData?.authorization_url) {
      window.open(authData.authorization_url, '_blank')
    }
  }

  const columns = [
    {
      title: '账号类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (type: string) => {
        const icon = type === 'claude' ? <RobotOutlined /> : <ApiOutlined />
        const color = type === 'claude' ? 'purple' : 'blue'
        return (
          <Tag icon={icon} color={color}>
            {type?.toUpperCase() || 'CLAUDE'}
          </Tag>
        )
      }
    },
    {
      title: '邮箱/标识',
      dataIndex: 'email',
      key: 'email',
      width: 200,
      ellipsis: true,
      render: (email: string) => (
        <Text>{email}</Text>
      )
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_, record: ClaudeAccount) => {
        // 根据 enabled 字段决定实际状态
        const displayStatus = record.enabled ? 'ACTIVE' : 'DISABLED'
        const statusConfig = {
          'ACTIVE': { color: 'success', icon: <CheckOutlined />, text: '正常' },
          'EXPIRED': { color: 'warning', icon: <ClockCircleOutlined />, text: '已过期' },
          'DISABLED': { color: 'default', icon: <CloseOutlined />, text: '已停用' },
          'RATE_LIMITED': { color: 'error', icon: <ThunderboltOutlined />, text: '限流中' }
        }
        const config = statusConfig[displayStatus] || statusConfig['DISABLED']
        return (
          <Badge status={config.color as any} text={config.text} />
        )
      }
    },
    {
      title: '启用状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (enabled: boolean, record: ClaudeAccount) => (
        <Switch
          checked={enabled}
          onChange={(checked) => handleStatusChange(record.id, checked)}
          checkedChildren="启用"
          unCheckedChildren="停用"
        />
      )
    },
    {
      title: '使用统计',
      key: 'stats',
      width: 160,
      render: (_, record: ClaudeAccount) => (
        <div>
          <div>请求: {record.totalRequests || 0}</div>
          <div>Token: {record.totalTokens || 0}k</div>
        </div>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (date: string) => {
        const d = new Date(date)
        return (
          <div>
            <div>{d.toLocaleDateString('zh-CN')}</div>
            <div style={{ fontSize: '12px', color: '#999' }}>{d.toLocaleTimeString('zh-CN')}</div>
          </div>
        )
      }
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right' as const,
      render: (_: any, record: ClaudeAccount) => (
        <div>
          <Button 
            type="link" 
            size="small"
            icon={<SafetyOutlined />}
            onClick={() => handleReauth(record)}
            style={{ padding: '0 4px' }}
          >
            重新授权
          </Button>
          <br />
          <Popconfirm
            title="确定要删除这个账号吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button 
              type="link" 
              danger 
              size="small"
              icon={<DeleteOutlined />}
              style={{ padding: '0 4px', marginTop: '4px' }}
            >
              删除
            </Button>
          </Popconfirm>
        </div>
      )
    }
  ]

  const accountTypes = [
    {
      key: 'claude',
      title: 'Claude',
      icon: <RobotOutlined style={{ fontSize: 48 }} />,
      description: 'Anthropic Claude AI',
      color: '#8B5CF6',
      available: true
    },
    {
      key: 'openai',
      title: 'OpenAI',
      icon: <GlobalOutlined style={{ fontSize: 48 }} />,
      description: 'ChatGPT & GPT API',
      color: '#10B981',
      available: false
    },
    {
      key: 'gemini',
      title: 'Gemini',
      icon: <ApiOutlined style={{ fontSize: 48 }} />,
      description: 'Google Gemini AI',
      color: '#3B82F6',
      available: false
    }
  ]

  // 统计数据 - 活跃账号应该同时满足 enabled=true 和 status=ACTIVE
  const activeAccounts = accounts.filter(a => a.enabled && a.status === 'ACTIVE').length
  const totalRequests = accounts.reduce((sum, a) => sum + (a.totalRequests || 0), 0)
  const totalAccounts = accounts.length

  return (
    <div>
      <div className="mb-6">
        <Title level={3}>账号管理中心</Title>
        <Paragraph type="secondary">
          管理和配置各种AI服务账号，支持OAuth授权和API Key两种接入方式
        </Paragraph>
      </div>

      {/* 统计卡片 */}
      <Row gutter={16} className="mb-6">
        <Col span={8}>
          <Card>
            <Statistic
              title="总账号数"
              value={totalAccounts}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="活跃账号"
              value={activeAccounts}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="总请求数"
              value={totalRequests}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      {/* 账号列表 */}
      <Card
        title={
          <Space>
            <KeyOutlined />
            <Text strong>已授权账号</Text>
          </Space>
        }
        extra={
          <Space>
            <Button 
              icon={<ReloadOutlined />}
              onClick={fetchAccounts}
              loading={accountsLoading}
            >
              刷新
            </Button>
            <Button 
              type="primary"
              icon={<PlusOutlined />}
              onClick={showAddModal}
            >
              添加账号
            </Button>
          </Space>
        }
      >
        {accounts.length > 0 ? (
          <Table
            columns={columns}
            dataSource={accounts}
            rowKey="id"
            loading={accountsLoading}
            scroll={{ x: 1200 }}
            pagination={{
              pageSize: 10,
              showTotal: (total) => `共 ${total} 个账号`
            }}
          />
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无账号"
            className="py-12"
          >
            <Button type="primary" icon={<PlusOutlined />} onClick={showAddModal}>
              立即添加
            </Button>
          </Empty>
        )}
      </Card>

      {/* 添加账号弹窗 */}
      <Modal
        title={reauthAccountId ? `重新授权: ${reauthAccountEmail}` : "添加新账号"}
        open={addModalVisible}
        onCancel={resetFlow}
        footer={null}
        width={800}
        destroyOnClose
      >
        {!selectedAccountType && (
          <div>
            <Title level={4} className="mb-4">选择账号类型</Title>
            <Row gutter={16}>
              {accountTypes.map(type => (
                <Col span={8} key={type.key}>
                  <Card
                    hoverable={type.available}
                    className={`text-center ${!type.available ? 'opacity-50' : ''}`}
                    onClick={() => type.available && handleAccountTypeSelect(type.key)}
                    style={{ borderColor: type.available ? type.color : undefined }}
                  >
                    <div style={{ color: type.color }}>
                      {type.icon}
                    </div>
                    <Title level={5} className="mt-3">{type.title}</Title>
                    <Text type="secondary">{type.description}</Text>
                    {!type.available && (
                      <div className="mt-2">
                        <Tag color="default">即将支持</Tag>
                      </div>
                    )}
                  </Card>
                </Col>
              ))}
            </Row>
          </div>
        )}

        {selectedAccountType === 'claude' && (
          <div>
            <Steps current={currentStep} className="mb-6">
              <Step title="生成授权链接" icon={<LinkOutlined />} />
              <Step title="完成授权" icon={<SafetyOutlined />} />
              <Step title="添加成功" icon={<CheckCircleOutlined />} />
            </Steps>

            {currentStep === 1 && authData && (
              <Spin spinning={loading}>
                <div className="space-y-4">
                  <Alert
                    message="请按照以下步骤完成授权"
                    description="每次授权都需要使用新的链接。如果授权失败，请点击'刷新链接'按钮生成新的授权链接。"
                    type="info"
                    showIcon
                  />

                  <div className="bg-gray-50 p-4 rounded-lg">
                    <div className="flex items-center justify-between mb-2">
                      <Text strong>步骤 1：访问授权链接</Text>
                      <Button
                        size="small"
                        icon={<ReloadOutlined />}
                        onClick={() => {
                          setAuthData(null);
                          generateAuthUrl();
                        }}
                      >
                        刷新链接
                      </Button>
                    </div>
                    <div className="mt-2 flex items-center space-x-2">
                      <Input
                        value={authData.authorization_url}
                        readOnly
                        className="flex-1"
                      />
                      <Button
                        icon={<CopyOutlined />}
                        onClick={() => copyToClipboard(authData.authorization_url)}
                      >
                        复制
                      </Button>
                      <Button
                        type="primary"
                        icon={<LinkOutlined />}
                        onClick={openAuthUrl}
                      >
                        打开链接
                      </Button>
                    </div>
                  </div>

                  <div className="bg-gray-50 p-4 rounded-lg">
                    <Text strong>步骤 2：保存验证信息</Text>
                    <div className="mt-2">
                      <Text type="secondary" className="text-sm">Code Verifier:</Text>
                      <div className="flex items-center space-x-2 mt-1">
                        <Input
                          value={authData.code_verifier}
                          readOnly
                          className="flex-1 font-mono text-sm"
                        />
                        <Button
                          size="small"
                          icon={<CopyOutlined />}
                          onClick={() => copyToClipboard(authData.code_verifier)}
                        />
                      </div>
                    </div>
                  </div>

                  <div className="bg-gray-50 p-4 rounded-lg">
                    <Text strong>步骤 3：输入授权码</Text>
                    <Form form={form} onFinish={handleAuthCallback} className="mt-3">
                      <Form.Item
                        name="authCode"
                        rules={[{ required: true, message: '请输入授权码' }]}
                      >
                        <Input.TextArea
                          placeholder="请输入从 Claude 获取的授权码"
                          rows={3}
                          value={authCode}
                          onChange={(e) => setAuthCode(e.target.value)}
                        />
                      </Form.Item>
                      <Space>
                        <Button onClick={resetFlow}>取消</Button>
                        <Button type="primary" htmlType="submit" loading={loading}>
                          完成授权
                        </Button>
                      </Space>
                    </Form>
                  </div>
                </div>
              </Spin>
            )}

            {currentStep === 2 && accountAdded && (
              <Result
                status="success"
                title="账号添加成功！"
                subTitle="您的 Claude 账号已成功添加到系统中"
                extra={[
                  <Button type="primary" key="close" onClick={resetFlow}>
                    关闭
                  </Button>,
                  <Button key="add" onClick={() => {
                    setCurrentStep(0)
                    setSelectedAccountType('')
                    setAccountAdded(false)
                  }}>
                    继续添加
                  </Button>
                ]}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}

export default OAuth