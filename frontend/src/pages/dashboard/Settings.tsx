import React, { useState, useEffect } from 'react'
import {
  Card,
  Form,
  Input,
  Button,
  Switch,
  InputNumber,
  Typography,
  Divider,
  Space,
  message,
  Tabs,
  Select,
  Alert,
} from 'antd'
import {
  SaveOutlined,
  ReloadOutlined,
  SecurityScanOutlined,
  ApiOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import axios from 'axios'

const { Title, Text } = Typography
const { TabPane } = Tabs
const { Option } = Select

const Settings: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [generalForm] = Form.useForm()
  const [securityForm] = Form.useForm()
  const [rateLimitForm] = Form.useForm()

  useEffect(() => {
    // 加载常规设置
    loadGeneralSettings()
    // 加载安全设置
    loadSecuritySettings()
    // 加载速率限制设置
    loadRateLimitSettings()
  }, [])

  const loadGeneralSettings = async () => {
    try {
      const response = await axios.get('/admin/settings/general')
      if (response.data) {
        generalForm.setFieldsValue(response.data)
      }
    } catch (error) {
      console.error('Failed to load general settings:', error)
    }
  }

  const loadSecuritySettings = async () => {
    try {
      const response = await axios.get('/admin/settings/security')
      if (response.data) {
        // 不设置密码相关字段
        const { oldPassword, newPassword, confirmPassword, ...otherFields } = response.data
        securityForm.setFieldsValue(otherFields)
      }
    } catch (error) {
      console.error('Failed to load security settings:', error)
    }
  }

  const loadRateLimitSettings = async () => {
    try {
      const response = await axios.get('/admin/settings/rate-limit')
      if (response.data) {
        rateLimitForm.setFieldsValue(response.data)
      }
    } catch (error) {
      console.error('Failed to load rate limit settings:', error)
    }
  }

  const handleSaveGeneral = async (values: any) => {
    setLoading(true)
    try {
      const response = await axios.put('/admin/settings/general', values)
      message.success('常规设置已保存')
      
      // 如果系统名称改变了，触发更新事件
      if (values.systemName) {
        window.dispatchEvent(new Event('system-name-updated'))
      }
    } catch (error) {
      message.error('保存失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSaveSecurity = async (values: any) => {
    setLoading(true)
    try {
      await axios.put('/admin/settings/security', values)
      message.success('安全设置已保存')
    } catch (error) {
      message.error('保存失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSaveRateLimit = async (values: any) => {
    setLoading(true)
    try {
      await axios.put('/admin/settings/rate-limit', values)
      message.success('速率限制设置已保存')
    } catch (error) {
      message.error('保存失败')
    } finally {
      setLoading(false)
    }
  }

  const handleChangePassword = async (values: any) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的密码不一致')
      return
    }
    
    setLoading(true)
    try {
      await axios.post('/admin/settings/change-password', {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      message.success('密码修改成功')
      securityForm.resetFields()
    } catch (error) {
      message.error('密码修改失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <Title level={3} className="mb-6">
        系统设置
      </Title>

      <Tabs defaultActiveKey="general">
        <TabPane
          tab={
            <span>
              <ApiOutlined />
              常规设置
            </span>
          }
          key="general"
        >
          <Card>
            <Form
              form={generalForm}
              layout="vertical"
              onFinish={handleSaveGeneral}
            >
              <Form.Item
                name="systemName"
                label="系统名称"
                rules={[{ required: true, message: '请输入系统名称' }]}
              >
                <Input placeholder="Claude Relay" />
              </Form.Item>

              <Form.Item
                name="claudeBaseUrl"
                label="Claude API 基础 URL"
                rules={[{ required: true, message: '请输入 Claude API URL' }]}
              >
                <Input placeholder="https://api.anthropic.com" />
              </Form.Item>

              <Form.Item
                name="geminiBaseUrl"
                label="Gemini API 基础 URL"
                rules={[{ required: true, message: '请输入 Gemini API URL' }]}
              >
                <Input placeholder="https://generativelanguage.googleapis.com" />
              </Form.Item>

              <Form.Item
                name="sessionTimeout"
                label="会话超时时间（小时）"
                rules={[{ required: true, message: '请设置会话超时时间' }]}
              >
                <InputNumber min={1} max={168} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="enableLogging"
                label="启用日志记录"
                valuePropName="checked"
              >
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>

              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={loading} icon={<SaveOutlined />}>
                    保存设置
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={() => generalForm.resetFields()}>
                    重置
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </TabPane>

        <TabPane
          tab={
            <span>
              <SecurityScanOutlined />
              安全设置
            </span>
          }
          key="security"
        >
          <Card>
            <Alert
              message="修改密码"
              description="定期修改密码可以提高系统安全性"
              type="info"
              showIcon
              className="mb-4"
            />
            
            <Form
              form={securityForm}
              layout="vertical"
              onFinish={handleChangePassword}
            >
              <Form.Item
                name="oldPassword"
                label="当前密码"
                rules={[{ required: true, message: '请输入当前密码' }]}
              >
                <Input.Password placeholder="请输入当前密码" />
              </Form.Item>

              <Form.Item
                name="newPassword"
                label="新密码"
                rules={[
                  { required: true, message: '请输入新密码' },
                  { min: 8, message: '密码至少8个字符' },
                ]}
              >
                <Input.Password placeholder="请输入新密码" />
              </Form.Item>

              <Form.Item
                name="confirmPassword"
                label="确认新密码"
                rules={[
                  { required: true, message: '请确认新密码' },
                ]}
              >
                <Input.Password placeholder="请再次输入新密码" />
              </Form.Item>

              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading}>
                  修改密码
                </Button>
              </Form.Item>
            </Form>

            <Divider />

            <Form
              form={securityForm}
              layout="vertical"
              onFinish={handleSaveSecurity}
            >
              <Form.Item
                name="enableTwoFactor"
                label="双因素认证"
                valuePropName="checked"
              >
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>

              <Form.Item
                name="allowedIps"
                label="IP 白名单"
                tooltip="留空表示允许所有 IP，多个 IP 用逗号分隔"
              >
                <Input.TextArea
                  placeholder="例如：192.168.1.1, 10.0.0.0/24"
                  rows={3}
                />
              </Form.Item>

              <Form.Item
                name="maxLoginAttempts"
                label="最大登录尝试次数"
              >
                <InputNumber min={1} max={10} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item>
                <Button type="primary" htmlType="submit" loading={loading} icon={<SaveOutlined />}>
                  保存安全设置
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </TabPane>

        <TabPane
          tab={
            <span>
              <ThunderboltOutlined />
              速率限制
            </span>
          }
          key="rateLimit"
        >
          <Card>
            <Form
              form={rateLimitForm}
              layout="vertical"
              onFinish={handleSaveRateLimit}
            >
              <Form.Item
                name="enableRateLimit"
                label="启用速率限制"
                valuePropName="checked"
              >
                <Switch checkedChildren="开启" unCheckedChildren="关闭" />
              </Form.Item>

              <Form.Item
                name="defaultRequestsPerMinute"
                label="默认每分钟请求数"
                rules={[{ required: true, message: '请设置每分钟请求数' }]}
              >
                <InputNumber min={1} max={1000} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="defaultRequestsPerHour"
                label="默认每小时请求数"
                rules={[{ required: true, message: '请设置每小时请求数' }]}
              >
                <InputNumber min={1} max={10000} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="defaultTokensPerDay"
                label="默认每日 Token 限制"
                rules={[{ required: true, message: '请设置每日 Token 限制' }]}
              >
                <InputNumber min={1000} max={10000000} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item
                name="burstSize"
                label="突发请求容量"
                tooltip="允许短时间内超过速率限制的请求数"
              >
                <InputNumber min={1} max={100} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={loading} icon={<SaveOutlined />}>
                    保存设置
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={() => rateLimitForm.resetFields()}>
                    重置
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </TabPane>
      </Tabs>
    </div>
  )
}

export default Settings