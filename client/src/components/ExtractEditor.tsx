/**
 * 提取规则列表编辑器组件
 *
 * 职责：基于 Form.List 渲染可增删的提取规则列表，
 * 每条规则由变量名、类型、表达式组成，用于从响应中抽取变量供后续步骤引用。
 */
import { Button, Form, Input, Select, Space } from 'antd'

// 提取类型下拉选项
const TYPE_OPTIONS = [
  { value: 'jsonPath', label: 'JSONPath' },
  { value: 'header', label: '响应头' },
  { value: 'regex', label: '正则' },
]

/** 提取规则列表编辑器（ExtractRule[]） */
export default function ExtractEditor() {
  return (
    // Form.List 管理提取规则数组字段的增删
    <Form.List name="extracts">
      {(fields, { add, remove }) => (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>提取（供后续步骤引用）</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
              {/* 提取结果保存到的变量名，必填 */}
              <Form.Item
                name={[field.name, 'name']}
                rules={[{ required: true, message: '请输入变量名' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="变量名" style={{ width: 140 }} />
              </Form.Item>
              {/* 提取类型选择，必填 */}
              <Form.Item
                name={[field.name, 'type']}
                rules={[{ required: true, message: '请选择类型' }]}
                style={{ marginBottom: 0 }}
              >
                <Select style={{ width: 120 }} options={TYPE_OPTIONS} placeholder="类型" />
              </Form.Item>
              {/* 提取表达式输入，必填 */}
              <Form.Item
                name={[field.name, 'expression']}
                rules={[{ required: true, message: '请输入表达式' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="表达式" style={{ width: 260 }} />
              </Form.Item>
              {/* 删除当前提取规则 */}
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          {/* 添加一条默认类型为 JSONPath 的提取规则 */}
          <Button type="dashed" block onClick={() => add({ type: 'jsonPath' })}>
            添加提取
          </Button>
        </div>
      )}
    </Form.List>
  )
}
