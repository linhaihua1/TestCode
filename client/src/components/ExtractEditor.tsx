import { Button, Form, Input, Select, Space } from 'antd'

const TYPE_OPTIONS = [
  { value: 'jsonPath', label: 'JSONPath' },
  { value: 'header', label: '响应头' },
  { value: 'regex', label: '正则' },
]

/** 提取规则列表编辑器（ExtractRule[]） */
export default function ExtractEditor() {
  return (
    <Form.List name="extracts">
      {(fields, { add, remove }) => (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>提取（供后续步骤引用）</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
              <Form.Item
                name={[field.name, 'name']}
                rules={[{ required: true, message: '请输入变量名' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="变量名" style={{ width: 140 }} />
              </Form.Item>
              <Form.Item
                name={[field.name, 'type']}
                rules={[{ required: true, message: '请选择类型' }]}
                style={{ marginBottom: 0 }}
              >
                <Select style={{ width: 120 }} options={TYPE_OPTIONS} placeholder="类型" />
              </Form.Item>
              <Form.Item
                name={[field.name, 'expression']}
                rules={[{ required: true, message: '请输入表达式' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="表达式" style={{ width: 260 }} />
              </Form.Item>
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          <Button type="dashed" block onClick={() => add({ type: 'jsonPath' })}>
            添加提取
          </Button>
        </div>
      )}
    </Form.List>
  )
}
