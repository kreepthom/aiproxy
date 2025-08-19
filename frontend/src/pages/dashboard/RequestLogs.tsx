import React, { useState, useEffect } from 'react'
import {
  Card,
  Table,
  Tag,
  Space,
  DatePicker,
  Select,
  Input,
  Button,
  Typography,
  Tooltip,
  Row,
  Col,
  Badge,
  message,
} from 'antd'
import {
  SearchOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  FilterOutlined,
  ExportOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import axios from 'axios'

const { Title, Text } = Typography
const { RangePicker } = DatePicker
const { Option } = Select

interface RequestLog {
  id: string
  apiKey: string
  accountId: string
  accountEmail: string
  endpoint: string
  method: string
  model?: string
  status: 'success' | 'failed' | 'pending'
  statusCode: number
  responseTime: number
  tokensUsed: number
  errorMessage?: string
  timestamp: string
  requestSize: number
  responseSize: number
  retryCount?: number
  failedAccounts?: string
  finalAccount?: string
}

const RequestLogs: React.FC = () => {
  const [logs, setLogs] = useState<RequestLog[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [filters, setFilters] = useState({
    apiKey: '',
    accountId: '',
    status: undefined as string | undefined,
    endpoint: undefined as string | undefined,
    dateRange: [] as any[],
  })

  useEffect(() => {
    fetchLogs()
  }, [currentPage, pageSize])

  const fetchLogs = async () => {
    setLoading(true)
    try {
      const params: any = {
        page: currentPage - 1, // 后端使用0开始的页码
        size: pageSize
      }
      
      // 添加搜索参数
      if (filters.apiKey) params.apiKey = filters.apiKey
      if (filters.accountId) params.accountId = filters.accountId
      if (filters.status) params.status = filters.status
      if (filters.endpoint) params.endpoint = filters.endpoint
      if (filters.dateRange && filters.dateRange.length === 2) {
        const [start, end] = filters.dateRange
        if (start && end && start.valueOf && end.valueOf) {
          params.startTime = start.valueOf()
          params.endTime = end.valueOf()
        }
      }
      
      const response = await axios.get('/admin/request-logs', { params })
      if (response.data.data) {
        setLogs(response.data.data)
        setTotal(response.data.total)
      } else {
        // 兼容旧格式
        setLogs(response.data)
        setTotal(response.data.length)
      }
    } catch (error) {
      console.error('Failed to fetch logs:', error)
      setLogs([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    setCurrentPage(1) // 搜索时重置到第一页
    fetchLogs()
  }

  const handleReset = () => {
    setFilters({
      apiKey: '',
      accountId: '',
      status: undefined,
      endpoint: undefined,
      dateRange: [],
    })
    setCurrentPage(1)
    fetchLogs()
  }

  const handleExport = async (type: string) => {
    try {
      const response = await axios.get('/admin/request-logs/export', {
        params: { type },
        responseType: 'text'
      })
      
      // 创建下载链接
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      const url = URL.createObjectURL(blob)
      
      link.setAttribute('href', url)
      link.setAttribute('download', `request_logs_${type}_${Date.now()}.csv`)
      link.style.visibility = 'hidden'
      
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      message.success(`导出${type === 'all' ? '全部' : type === 'failed' ? '失败' : type === 'slow' ? '慢请求' : '成功'}日志成功`)
    } catch (error) {
      console.error('Export failed:', error)
      message.error('导出失败')
    }
  }

  const columns: ColumnsType<RequestLog> = [
    {
      title: '时间',
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 180,
      render: (timestamp: string) => (
        <div style={{ fontSize: '12px' }}>
          <div>{dayjs(timestamp).format('YYYY-MM-DD')}</div>
          <div style={{ color: '#8c8c8c' }}>{dayjs(timestamp).format('HH:mm:ss')}</div>
        </div>
      ),
    },
    {
      title: 'API Key',
      dataIndex: 'apiKey',
      key: 'apiKey',
      width: 120,
      render: (text: string) => <Text code>{text}</Text>,
    },
    {
      title: '账号',
      dataIndex: 'accountId',
      key: 'accountId',
      width: 120,
      render: (accountId: string, record) => {
        const shortId = accountId ? accountId.substring(0, 8) : '-'
        return (
          <Tooltip title={
            <div>
              <div>账号ID: {accountId}</div>
              <div>邮箱: {record.accountEmail || 'unknown'}</div>
            </div>
          }>
            <Text 
              style={{ 
                cursor: 'pointer',
                color: '#1890ff',
                fontSize: '12px'
              }}
              onClick={() => {
                // 可以添加跳转到账号详情的逻辑
                message.info(`账号: ${record.accountEmail}`)
              }}
            >
              {shortId}...
            </Text>
          </Tooltip>
        )
      },
    },
    {
      title: '模型',
      dataIndex: 'model',
      key: 'model',
      width: 150,
      render: (model?: string) => {
        if (!model) return <Text type="secondary">-</Text>
        const modelShort = model.includes('opus') ? 'Opus' : 
                          model.includes('sonnet') ? 'Sonnet' : 
                          model.includes('haiku') ? 'Haiku' : model
        const color = model.includes('opus') ? 'purple' : 
                     model.includes('sonnet') ? 'blue' : 
                     model.includes('haiku') ? 'green' : 'default'
        return (
          <Tooltip title={model}>
            <Tag color={color}>{modelShort}</Tag>
          </Tooltip>
        )
      },
    },
    {
      title: '端点',
      dataIndex: 'endpoint',
      key: 'endpoint',
      width: 150,
      render: (endpoint: string, record) => (
        <Space>
          <Tag color={record.method === 'POST' ? 'green' : 'blue'}>{record.method}</Tag>
          <Text>{endpoint}</Text>
        </Space>
      ),
    },
    {
      title: '重试',
      key: 'retry',
      width: 120,
      render: (_, record) => {
        if (!record.retryCount || record.retryCount === 0) {
          return <Text type="secondary">-</Text>
        }
        
        const failedList = record.failedAccounts ? 
          (typeof record.failedAccounts === 'string' ? 
            JSON.parse(record.failedAccounts) : record.failedAccounts) : []
        
        // 如果状态是失败，说明所有账号都失败了
        const isAllFailed = record.status === 'failed'
        
        return (
          <Tooltip title={
            <div>
              <div>重试次数: {record.retryCount}</div>
              {failedList.length > 0 && (
                <div>
                  <div>失败账号列表:</div>
                  {failedList.map((acc: string, idx: number) => (
                    <div key={idx} style={{ marginLeft: 10 }}>
                      {idx + 1}. {acc}
                    </div>
                  ))}
                </div>
              )}
              {record.finalAccount && !isAllFailed && (
                <div>最终成功账号: {record.finalAccount}</div>
              )}
              {isAllFailed && (
                <div style={{ color: '#ff4d4f' }}>所有账号均失败</div>
              )}
            </div>
          }>
            {isAllFailed ? (
              <Tag color="error" style={{ margin: 0 }}>
                重试{record.retryCount}次失败
              </Tag>
            ) : (
              <Tag color="warning" style={{ margin: 0 }}>
                重试{record.retryCount}次
              </Tag>
            )}
          </Tooltip>
        )
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      filters: [
        { text: '成功', value: 'success' },
        { text: '失败', value: 'failed' },
        { text: '进行中', value: 'pending' },
      ],
      onFilter: (value, record) => record.status === value,
      render: (status: string, record) => {
        const statusConfig = {
          success: { color: 'success', icon: <CheckCircleOutlined />, text: '成功' },
          failed: { color: 'error', icon: <CloseCircleOutlined />, text: '失败' },
          pending: { color: 'processing', icon: <ClockCircleOutlined />, text: '进行中' },
        }
        const config = statusConfig[status as keyof typeof statusConfig]
        return (
          <Tag color={config.color} icon={config.icon}>
            {config.text}
          </Tag>
        )
      },
    },
    {
      title: '状态码',
      dataIndex: 'statusCode',
      key: 'statusCode',
      width: 80,
      render: (code: number) => (
        <Badge
          status={code >= 200 && code < 300 ? 'success' : code >= 400 ? 'error' : 'warning'}
          text={code}
        />
      ),
    },
    {
      title: '响应时间',
      dataIndex: 'responseTime',
      key: 'responseTime',
      width: 100,
      sorter: (a, b) => a.responseTime - b.responseTime,
      render: (time: number) => {
        const color = time < 1000 ? 'green' : time < 3000 ? 'orange' : 'red'
        return <Text style={{ color }}>{time}ms</Text>
      },
    },
    {
      title: 'Tokens',
      dataIndex: 'tokensUsed',
      key: 'tokensUsed',
      width: 100,
      sorter: (a, b) => a.tokensUsed - b.tokensUsed,
      render: (tokens: number) => (
        <Text>{tokens > 0 ? tokens.toLocaleString() : '-'}</Text>
      ),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      width: 200,
      ellipsis: true,
      render: (error?: string) => error ? (
        <Tooltip title={error}>
          <Text type="danger" ellipsis>{error}</Text>
        </Tooltip>
      ) : <Text type="secondary">-</Text>,
    },
  ]

  return (
    <div>
      <Title level={3} className="mb-6">
        请求日志
      </Title>

      {/* 筛选器 */}
      <Card className="mb-4">
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Input
              placeholder="API Key"
              prefix={<SearchOutlined />}
              value={filters.apiKey}
              onChange={(e) => setFilters({ ...filters, apiKey: e.target.value })}
              onPressEnter={handleSearch}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Input
              placeholder="账号邮箱"
              value={filters.accountId}
              onChange={(e) => setFilters({ ...filters, accountId: e.target.value })}
              onPressEnter={handleSearch}
            />
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Select
              placeholder="状态"
              style={{ width: '100%' }}
              value={filters.status}
              onChange={(value) => setFilters({ ...filters, status: value })}
              allowClear
            >
              <Option value="success">成功</Option>
              <Option value="failed">失败</Option>
              <Option value="pending">进行中</Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Select
              placeholder="端点"
              style={{ width: '100%' }}
              value={filters.endpoint}
              onChange={(value) => setFilters({ ...filters, endpoint: value })}
              allowClear
            >
              <Option value="/v1/messages">Messages</Option>
              <Option value="/v1/completions">Completions</Option>
              <Option value="/v1/models">Models</Option>
            </Select>
          </Col>
          <Col xs={24} md={12}>
            <RangePicker
              style={{ width: '100%' }}
              value={filters.dateRange.length === 2 ? filters.dateRange as any : null}
              onChange={(dates) => setFilters({ ...filters, dateRange: dates as any || [] })}
              showTime
              format="YYYY-MM-DD HH:mm"
              placeholder={['开始时间', '结束时间']}
            />
          </Col>
          <Col xs={24} md={12}>
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={handleSearch}
              >
                搜索
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={handleReset}
              >
                重置
              </Button>
              <Button.Group>
                <Button
                  icon={<ExportOutlined />}
                  onClick={() => handleExport('all')}
                >
                  导出全部
                </Button>
                <Button
                  onClick={() => handleExport('failed')}
                >
                  导出失败
                </Button>
                <Button
                  onClick={() => handleExport('slow')}
                >
                  导出慢请求
                </Button>
                <Button
                  onClick={() => handleExport('success')}
                >
                  导出成功
                </Button>
              </Button.Group>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 日志表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={logs}
          loading={loading}
          rowKey="id"
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: total,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`,
            pageSizeOptions: ['10', '20', '50', '100'],
            onChange: (page, size) => {
              setCurrentPage(page)
              if (size !== pageSize) {
                setPageSize(size!)
                setCurrentPage(1) // 改变每页条数时重置到第一页
              }
            },
          }}
          scroll={{ x: 1500 }}
        />
      </Card>
    </div>
  )
}

export default RequestLogs