import React, { useState, useEffect } from 'react'
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom'
import {
  Layout,
  Menu,
  Avatar,
  Dropdown,
  Space,
  Typography,
  Button,
  theme,
} from 'antd'
import {
  DashboardOutlined,
  KeyOutlined,
  UserOutlined,
  LogoutOutlined,
  ApiOutlined,
  SafetyOutlined,
  SettingOutlined,
  HistoryOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import Overview from './dashboard/Overview'
import ApiKeys from './dashboard/ApiKeys'
import OAuth from './dashboard/OAuth'
import Settings from './dashboard/Settings'
import RequestLogs from './dashboard/RequestLogs'
import axios from 'axios'

const { Header, Sider, Content } = Layout
const { Text } = Typography

const Dashboard: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false)
  const [systemName, setSystemName] = useState('Claude Relay')
  const navigate = useNavigate()
  const location = useLocation()
  const { username, logout } = useAuthStore()
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  useEffect(() => {
    // 获取系统名称
    fetchSystemName()
    
    // 监听系统名称更新事件
    window.addEventListener('system-name-updated', fetchSystemName)
    
    return () => {
      window.removeEventListener('system-name-updated', fetchSystemName)
    }
  }, [])

  const fetchSystemName = async () => {
    try {
      const response = await axios.get('/admin/settings/system-name')
      if (response.data && response.data.systemName) {
        setSystemName(response.data.systemName)
      }
    } catch (error) {
      console.error('Failed to fetch system name:', error)
    }
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const userMenu = {
    items: [
      {
        key: 'profile',
        icon: <UserOutlined />,
        label: '个人信息',
      },
      {
        type: 'divider' as const,
      },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: '退出登录',
        onClick: handleLogout,
      },
    ],
  }

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '概览',
    },
    {
      key: '/dashboard/api-keys',
      icon: <KeyOutlined />,
      label: 'API Key 管理',
    },
    {
      key: '/dashboard/oauth',
      icon: <SafetyOutlined />,
      label: 'OAuth 授权',
    },
    {
      key: '/dashboard/request-logs',
      icon: <HistoryOutlined />,
      label: '请求日志',
    },
    {
      key: '/dashboard/settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    },
  ]

  return (
    <Layout className="min-h-screen">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        className="shadow-lg"
        theme="light"
      >
        <div className="h-16 flex items-center justify-center border-b border-gray-200 bg-white">
          <Space>
            <ApiOutlined className="text-2xl text-blue-600" />
            {!collapsed && (
              <Text className="font-bold text-lg text-gray-800">
                {systemName}
              </Text>
            )}
          </Space>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ height: 'calc(100% - 64px)', borderRight: 0 }}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,21,41,.08)',
          }}
        >
          <Button
            type="text"
            icon={collapsed ? 
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
              </svg> : 
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h8M4 18h16" />
              </svg>
            }
            onClick={() => setCollapsed(!collapsed)}
            style={{
              fontSize: '16px',
              width: 48,
              height: 48,
            }}
          />

          <Dropdown menu={userMenu} placement="bottomRight">
            <Space className="cursor-pointer">
              <Avatar
                style={{
                  backgroundColor: '#1890ff',
                }}
                icon={<UserOutlined />}
              />
              <Text>{username || '管理员'}</Text>
            </Space>
          </Dropdown>
        </Header>

        <Content
          style={{
            margin: '24px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
          }}
        >
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/api-keys" element={<ApiKeys />} />
            <Route path="/oauth" element={<OAuth />} />
            <Route path="/request-logs" element={<RequestLogs />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}

export default Dashboard