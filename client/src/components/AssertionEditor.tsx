/**
 * 断言列表编辑器组件
 *
 * 职责：基于 Form.List 渲染可增删的断言规则列表，
 * 每条断言由类型、表达式、运算符、期望值四部分组成，用于校验接口响应。
 */
import { Button, Form, Input, Select, Space } from 'antd'

// 断言类型下拉选项
const TYPE_OPTIONS = [
  { value: 'statusCode', label: '状态码' },
  { value: 'jsonPath', label: 'JSONPath' },
  { value: 'header', label: '响应头' },
  { value: 'regex', label: '正则' },
]

// 比较运算符下拉选项
const OPERATOR_OPTIONS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'notContains', label: '不包含' },
  { value: 'regex', label: '匹配正则' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' },
]

/** 断言列表编辑器（Assertion[]），name 支持嵌套路径（多步骤用例） */
export default function AssertionEditor({ name = 'assertions' }: { name?: string | (string | number)[] }) {
  return (
    // Form.List 管理断言数组字段的增删
    <Form.List name={name}>
      {(fields, { add, remove }) => (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>断言</div>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }} wrap>
              {/* 断言类型选择，必填 */}
              <Form.Item
                name={[field.name, 'type']}
                rules={[{ required: true, message: '请选择类型' }]}
                style={{ marginBottom: 0 }}
              >
                <Select style={{ width: 120 }} options={TYPE_OPTIONS} placeholder="类型" />
              </Form.Item>
              {/* 表达式输入（JSONPath / 响应头名 / 正则） */}
              <Form.Item name={[field.name, 'expression']} style={{ marginBottom: 0 }}>
                <Input placeholder="表达式 / 响应头名 / 正则" style={{ width: 200 }} />
              </Form.Item>
              {/* 比较运算符选择，默认等于 */}
              <Form.Item name={[field.name, 'operator']} initialValue="eq" style={{ marginBottom: 0 }}>
                <Select style={{ width: 110 }} options={OPERATOR_OPTIONS} />
              </Form.Item>
              {/* 期望值输入，必填 */}
              <Form.Item
                name={[field.name, 'expected']}
                rules={[{ required: true, message: '请输入期望值' }]}
                style={{ marginBottom: 0 }}
              >
                <Input placeholder="期望值" style={{ width: 140 }} />
              </Form.Item>
              {/* 删除当前断言 */}
              <Button type="text" danger onClick={() => remove(field.name)}>
                删除
              </Button>
            </Space>
          ))}
          {/* 添加一条默认类型为状态码、运算符为等于的断言 */}
          <Button type="dashed" block onClick={() => add({ type: 'statusCode', operator: 'eq' })}>
            添加断言
          </Button>
        </div>
      )}
    </Form.List>
  )
}
