import React, { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic, Typography, Progress, Table, Tag, Space, Tooltip, Badge, Button } from 'antd'
import {
  ApiOutlined,
  KeyOutlined,
  UserOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudServerOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  SyncOutlined,
  RiseOutlined,
  FallOutlined,
  SwapOutlined,
  RocketOutlined,
  FireOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import { 
  LineChart, 
  Line, 
  AreaChart,
  Area,
  BarChart, 
  Bar, 
  PieChart, 
  Pie, 
  Cell,
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip as RechartsTooltip, 
  Legend,
  ResponsiveContainer 
} from 'recharts'
import axios from 'axios'
import dayjs from 'dayjs'

const { Title, Text } = Typography

interface Stats {
  totalKeys: number
  activeKeys: number
  totalRequests: number
  successRate: number
  totalAccounts: number
  activeAccounts: number
  accountPoolHealth: number
  totalTokensUsed: number
  todayRequests: number
  todayTokens: number
  avgResponseTime: number
  errorRate: number
  currentRPM?: number
  currentTPM?: number
}

interface TokenUsageData {
  date: string
  tokens: number
  requests: number
}

interface ModelUsageData {
  name: string
  value: number
  color: string
}

interface ApiKeyUsageData {
  key: string
  usage: number
}

const Overview: React.FC = () => {
  const [stats, setStats] = useState<Stats>({
    totalKeys: 0,
    activeKeys: 0,
    totalRequests: 0,
    successRate: 0,
    totalAccounts: 0,
    activeAccounts: 0,
    accountPoolHealth: 0,
    totalTokensUsed: 0,
    todayRequests: 0,
    todayTokens: 0,
    avgResponseTime: 0,
    errorRate: 0,
  })
  
  const [tokenUsageData, setTokenUsageData] = useState<TokenUsageData[]>([])
  const [modelUsageData, setModelUsageData] = useState<ModelUsageData[]>([])
  const [apiKeysUsageData, setApiKeysUsageData] = useState<ApiKeyUsageData[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchData()
    // 每30秒刷新一次数据
    const interval = setInterval(fetchData, 30000)
    return () => clearInterval(interval)
  }, [])

  const fetchData = async () => {
    try {
      // 获取仪表板所有数据
      const dashboardResponse = await axios.get('/api/stats/dashboard')
      const dashboard = dashboardResponse.data
      
      // 从overview中提取统计数据
      const overview = dashboard.overview || {}
      setStats({
        totalKeys: overview.totalKeys || 0,
        activeKeys: overview.activeKeys || 0,
        totalRequests: overview.totalRequests || 0,
        successRate: parseFloat(overview.successRate || '0'),
        totalAccounts: overview.totalAccounts || 0,
        activeAccounts: overview.activeAccounts || 0,
        accountPoolHealth: overview.accountPoolHealth || 0,
        totalTokensUsed: overview.totalTokensUsed || 0,
        todayRequests: overview.todayRequests || 0,
        todayTokens: overview.todayTokens || 0,
        avgResponseTime: overview.avgResponseTime || 0,
        errorRate: overview.errorRate || 0,
        currentRPM: overview.currentRPM || 0,
        currentTPM: overview.currentTPM || 0,
      })
      
      // 设置Token使用趋势数据
      const tokenTrend = dashboard.tokenTrend || []
      const formattedTokenData = tokenTrend.map((item: any) => ({
        date: dayjs(item.date).format('MM/DD'),
        tokens: item.tokens || 0,
        requests: item.requests || 0,
      }))
      setTokenUsageData(formattedTokenData)
      
      // 设置模型使用分布数据
      const modelDist = dashboard.modelDistribution || []
      setModelUsageData(modelDist)
      
      // 设置API Keys使用数据
      const apiKeysUsage = dashboard.apiKeysUsage || []
      setApiKeysUsageData(apiKeysUsage)
      
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
      // 如果API失败，显示默认数据
      setStats({
        totalKeys: 0,
        activeKeys: 0,
        totalRequests: 0,
        successRate: 0,
        totalAccounts: 0,
        activeAccounts: 0,
        accountPoolHealth: 0,
        totalTokensUsed: 0,
        todayRequests: 0,
        todayTokens: 0,
        avgResponseTime: 0,
        errorRate: 0,
      })
    } finally {
      setLoading(false)
    }
  }

  // 自定义图表颜色
  const COLORS = ['#8884d8', '#82ca9d', '#ffc658', '#ff7c7c', '#8dd1e1']

  return (
    <div className="p-6 bg-gray-50/50 min-h-screen">
      <Title level={3} className="mb-6 text-gray-800">
        系统概览
      </Title>

      {/* 核心指标卡片 - 第一行 */}
      <Row gutter={[16, 16]} className="mb-4">
        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-blue-50 via-white to-blue-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between">
              <div>
                <Text className="text-xs text-gray-500 font-medium">总API Keys</Text>
                <div className="text-3xl font-bold mt-2 text-gray-800">{stats.totalKeys}</div>
                <div className="flex items-center mt-3">
                  <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse mr-2"></div>
                  <Text className="text-sm text-gray-600">活跃: {stats.activeKeys}</Text>
                </div>
              </div>
              <div className="bg-blue-100 p-3 rounded-xl">
                <KeyOutlined className="text-3xl text-blue-600" />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-green-50 via-white to-green-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between">
              <div>
                <Text className="text-xs text-gray-500 font-medium">服务账户</Text>
                <div className="text-3xl font-bold mt-2 text-gray-800">{stats.totalAccounts}</div>
                <div className="flex items-center mt-3">
                  <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse mr-2"></div>
                  <Text className="text-sm text-gray-600">正常: {stats.activeAccounts}</Text>
                </div>
              </div>
              <div className="bg-green-100 p-3 rounded-xl">
                <TeamOutlined className="text-3xl text-green-600" />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-purple-50 via-white to-purple-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between">
              <div>
                <Text className="text-xs text-gray-500 font-medium">今日请求</Text>
                <div className="text-3xl font-bold mt-2 text-gray-800">{stats.todayRequests.toLocaleString()}</div>
                <div className="flex items-center mt-3">
                  <RiseOutlined className="text-green-500 mr-1" />
                  <Text className="text-sm font-semibold text-green-500">+12.5%</Text>
                </div>
              </div>
              <div className="bg-purple-100 p-3 rounded-xl">
                <ApiOutlined className="text-3xl text-purple-600" />
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-cyan-50 via-white to-cyan-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between">
              <div>
                <Text className="text-xs text-gray-500 font-medium">系统状态</Text>
                <div className="text-2xl font-bold mt-2 text-green-600">正常</div>
                <div className="flex items-center mt-3">
                  <Text className="text-sm text-gray-600">成功率: {stats.successRate}%</Text>
                </div>
              </div>
              <div className="bg-cyan-100 p-3 rounded-xl">
                <CheckCircleOutlined className="text-3xl text-cyan-600" />
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 性能指标卡片 - 第二行 */}
      <Row gutter={[16, 16]} className="mb-6">
        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-orange-50 via-white to-orange-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between mb-2">
              <Text className="text-xs text-gray-500 font-medium">今日Token</Text>
              <FireOutlined className="text-lg text-orange-500" />
            </div>
            <div className="text-2xl font-bold text-gray-800">
              {stats.todayTokens.toLocaleString()} <span className="text-sm font-normal text-gray-500">个</span>
            </div>
            <Progress 
              percent={75} 
              strokeColor={{ from: '#fb923c', to: '#f97316' }}
              showInfo={false}
              strokeWidth={8}
              className="mt-3"
            />
            <Text className="text-xs text-gray-500 mt-2">每分钟消耗速率</Text>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-yellow-50 via-white to-yellow-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between mb-2">
              <Text className="text-xs text-gray-500 font-medium">总Token消耗</Text>
              <ThunderboltOutlined className="text-lg text-yellow-500" />
            </div>
            <div className="text-2xl font-bold text-gray-800">
              {(stats.totalTokensUsed / 1000).toFixed(1)} <span className="text-sm font-normal text-gray-500">k</span>
            </div>
            <div className="mt-3">
              <Text className="text-xs text-gray-500">每分钟消耗速率</Text>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-violet-50 via-white to-violet-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between mb-2">
              <Text className="text-xs text-gray-500 font-medium">实时RPM</Text>
              <RocketOutlined className="text-lg text-violet-500" />
            </div>
            <div className="text-2xl font-bold text-gray-800">
              {stats.currentRPM || 0} <span className="text-sm font-normal text-gray-500">/分钟</span>
            </div>
            <div className="mt-3">
              <Text className="text-xs text-gray-500">每分钟请求数</Text>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card 
            loading={loading} 
            className="hover:shadow-xl transition-all duration-300 border-0 bg-gradient-to-br from-teal-50 via-white to-teal-50/30"
            bodyStyle={{ padding: '20px' }}
          >
            <div className="flex items-center justify-between mb-2">
              <Text className="text-xs text-gray-500 font-medium">实时TPM</Text>
              <SwapOutlined className="text-lg text-teal-500" />
            </div>
            <div className="text-2xl font-bold text-gray-800">
              {stats.currentTPM || 0} <span className="text-sm font-normal text-gray-500">/分钟</span>
            </div>
            <div className="mt-3">
              <Text className="text-xs text-gray-500">每分钟Token数</Text>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 图表区域 */}
      <Row gutter={[16, 16]} className="mb-6">
        {/* Token使用趋势 */}
        <Col xs={24} lg={16}>
          <Card 
            title={<span className="text-gray-700 font-medium">Token使用趋势</span>}
            className="shadow-lg hover:shadow-xl transition-all duration-300 border-0"
            bodyStyle={{ padding: '20px' }}
            extra={
              <Space>
                <Tag color="blue" className="cursor-pointer">7天</Tag>
                <Tag color="green" className="cursor-pointer">30天</Tag>
              </Space>
            }
          >
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={tokenUsageData}>
                <defs>
                  <linearGradient id="colorTokens" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#8884d8" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#8884d8" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#82ca9d" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#82ca9d" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="date" />
                <YAxis yAxisId="left" />
                <YAxis yAxisId="right" orientation="right" />
                <RechartsTooltip />
                <Legend />
                <Area
                  yAxisId="left"
                  type="monotone"
                  dataKey="tokens"
                  stroke="#8884d8"
                  fillOpacity={1}
                  fill="url(#colorTokens)"
                  name="Token消耗"
                />
                <Area
                  yAxisId="right"
                  type="monotone"
                  dataKey="requests"
                  stroke="#82ca9d"
                  fillOpacity={1}
                  fill="url(#colorRequests)"
                  name="请求次数"
                />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        {/* 模型使用分布 */}
        <Col xs={24} lg={8}>
          <Card 
            title={<span className="text-gray-700 font-medium">模型使用分布</span>}
            className="shadow-lg hover:shadow-xl transition-all duration-300 border-0"
            bodyStyle={{ padding: '20px' }}
          >
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={modelUsageData}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                  outerRadius={80}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {modelUsageData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <RechartsTooltip />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      {/* API Keys使用趋势 */}
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card 
            title={<span className="text-gray-700 font-medium">API Keys 使用趋势</span>}
            className="shadow-lg hover:shadow-xl transition-all duration-300 border-0"
            bodyStyle={{ padding: '20px' }}
            extra={
              <Space>
                <Button type="link" size="small" className="text-blue-500 hover:text-blue-600">查看详情</Button>
                <Button type="link" size="small" className="text-gray-500 hover:text-gray-600">导出数据</Button>
              </Space>
            }
          >
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={apiKeysUsageData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="key" />
                <YAxis />
                <RechartsTooltip />
                <Bar dataKey="usage" fill="#82ca9d" />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default Overview