import { Button, Form, Input, Select, Space } from 'antd'

const TYPE_OPTIONS = [
  { value: 'statusCode', label: '状态码' },
  { value: 'jsonPath', label: 'JSONPath' },
  { value: 'header', label: '响应头' },
  { value: 'regex', label: '正则' },
]

const OPERATOR_OPTIONS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'notContains', label: '不包含' },
  { value: 'regex', label: '匹配正则' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
]

/** 断言列表编辑器（Assertion[]） */
export default function AssertionEditor() {
  return (
    <Form.List name="assertions">
      {(fields, { add, remove }) => (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>断言</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }} wrap>
              <Form.Item
                name={[field.name, 'type']}
                rules={[{ required: true, message: '请选择类型' }]}
                style={{ marginBottom: 0 }}
              >
                <Select style={{ width: 120 }} options={TYPE_OPTIONS} placeholder="类型" />
              </Form.Item>
              <Form.Item name={[field.name, 'expression']} style={{ marginBottom: 0 }}>
                <Input placeholder="表达式 / 响应头名 / 正则" style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name={[field.name, 'operator']} initialValue="eq" style={{ marginBottom: 0 }}>
                <Select style={{ width: 110 }} options={OPERATOR_OPTIONS} />
              </Form.Item>
              <Form.Item
                name={[field.name, 'expected']}
                rules={[{ required: true, message: '请输入期望值' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="期望值" style={{ width: 140 }} />
              </Form.Item>
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          <Button type="dashed" block onClick={() => add({ type: 'statusCode', operator: 'eq' })}>
            添加断言
          </Button>
        </div>
      )}
    </Form.List>
  )
}
