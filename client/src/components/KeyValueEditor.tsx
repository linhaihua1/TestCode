import { Button, Form, Input, Space } from 'antd'

interface Props {
  /** 表单字段名，对应 KeyValue[] 结构 */
  name: string
  label: string
  keyPlaceholder?: string
  valuePlaceholder?: string
}

/** 键值对动态编辑器（用于变量、请求头、Query 等） */
export default function KeyValueEditor({ name, label, keyPlaceholder = '键', valuePlaceholder = '值' }: Props) {
  return (
    <Form.List name={name}>
      {(fields, { add, remove }) => (
        <div>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>{label}</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
              <Form.Item
                name={[field.name, 'key']}
                rules={[{ required: true, message: '请输入键' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder={keyPlaceholder} style={{ width: 200 }} />
              </Form.Item>
              <Form.Item name={[field.name, 'value']} style={{ marginBottom: 0 }}>
                <Input placeholder={valuePlaceholder} style={{ width: 320 }} />
              </Form.Item>
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          <Button type="dashed" onClick={() => add()} block style={{ marginTop: 4 }}>
            添加{label}
          </Button>
        </div>
      )}
    </Form.List>
  )
}
