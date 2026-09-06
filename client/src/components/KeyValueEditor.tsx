/**
 * 键值对动态编辑器组件
 *
 * 职责：基于 antd 的 Form.List 渲染可增删的键值对列表，
 * 复用为变量、请求头、Query 参数等的通用编辑控件。
 */
import { Button, Form, Input, Space } from 'antd'
import type { NamePath } from 'antd/es/form/interface'

interface Props {
  /** 表单字段名，对应 KeyValue[] 结构；支持嵌套路径以便在 Form.List 内复用 */
  name: NamePath
  label: string
  keyPlaceholder?: string // 键输入框占位提示
  valuePlaceholder?: string // 值输入框占位提示
}

/** 键值对动态编辑器（用于变量、请求头、Query 等） */
export default function KeyValueEditor({ name, label, keyPlaceholder = '键', valuePlaceholder = '值' }: Props) {
  return (
    // Form.List 管理键值对数组字段的增删
    <Form.List name={name}>
      {(fields, { add, remove }) => (
        <div>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>{label}</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
              {/* 键输入框，必填 */}
              <Form.Item
                name={[field.name, 'key']}
                rules={[{ required: true, message: '请输入键' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder={keyPlaceholder} style={{ width: 200 }} />
              </Form.Item>
              {/* 值输入框 */}
              <Form.Item name={[field.name, 'value']} style={{ marginBottom: 0 }}>
                <Input placeholder={valuePlaceholder} style={{ width: 320 }} />
              </Form.Item>
              {/* 删除当前行 */}
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          {/* 追加一行新的键值对 */}
          <Button type="dashed" onClick={() => add()} block style={{ marginTop: 4 }}>
            添加{label}
          </Button>
        </div>
      )}
    </Form.List>
  )
}
