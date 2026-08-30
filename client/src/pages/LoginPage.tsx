/**
 * 登录页：用户名密码登录，成功后保存登录态并跳转首页。
 */
import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { setAuth } from '../api/auth'

export default function LoginPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  // 提交登录表单
  const handleSubmit = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const { token, user } = await api.login(values.username, values.password)
      setAuth(token, user) // 保存登录态
      message.success('登录成功')
      navigate('/projects') // 跳转到项目列表
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card title="API 自动化测试平台" style={{ width: 380 }}>
        <Form layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="用户名" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="密码" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading}>
            登录
          </Button>
        </Form>
        <div style={{ marginTop: 16, color: '#999', textAlign: 'center', fontSize: 12 }}>
          默认账号：admin / admin@123
        </div>
      </Card>
    </div>
  )
}
