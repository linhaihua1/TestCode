import { describe, it, expect } from 'vitest'
import { buildJmx, buildJmxMulti } from './jmx-builder.js'
import { parseJmx } from './jmx-parser.js'
import type { PerfCaseModel } from './types.js'

const model: PerfCaseModel = {
  name: '登录接口压测',
  threads: 20,
  rampUp: 5,
  loops: 10,
  duration: 0,
  thinkTime: 200,
  onSampleError: 'continue',
  variables: [{ key: 'baseUrl', value: 'http://127.0.0.1:4000', enabled: true }],
  steps: [
    {
      id: 's1',
      name: '登录',
      method: 'POST',
      url: '${baseUrl}/api/auth/login',
      headers: [{ key: 'Content-Type', value: 'application/json', enabled: true }],
      body: '{"username":"admin","password":"admin@123"}',
      assertions: [{ type: 'responseCode', operator: 'equals', expected: '200' }],
      enabled: true,
    },
    {
      id: 's2',
      name: '查询列表',
      method: 'GET',
      url: '${baseUrl}/api/projects',
      query: [{ key: 'page', value: '1', enabled: true }],
      assertions: [{ type: 'responseText', operator: 'contains', expected: '"total"' }],
      enabled: true,
    },
  ],
}

describe('jmx-builder 生成标准 JMeter 测试计划', () => {
  const xml = buildJmx(model)

  it('包含 jmeterTestPlan 根与 hashTree 结构', () => {
    expect(xml.startsWith('<?xml')).toBe(true)
    expect(xml).toContain('<jmeterTestPlan')
    expect(xml).toContain('</jmeterTestPlan>')
    expect(xml.split('<hashTree').length - 1).toBeGreaterThan(0)
  })

  it('线程组参数正确', () => {
    expect(xml).toContain('<stringProp name="ThreadGroup.num_threads">20</stringProp>')
    expect(xml).toContain('<stringProp name="ThreadGroup.ramp_time">5</stringProp>')
    expect(xml).toContain('<stringProp name="LoopController.loops">10</stringProp>')
    expect(xml).toContain('<boolProp name="ThreadGroup.scheduler">false</boolProp>')
  })

  it('raw 请求体走 postBodyRaw，Query 参数走 Arguments', () => {
    expect(xml).toContain('<boolProp name="HTTPSampler.postBodyRaw">true</boolProp>')
    expect(xml).toContain('&quot;username&quot;')
    expect(xml).toContain('<stringProp name="Argument.name">page</stringProp>')
  })

  it('断言使用 JMeter 的历史拼写属性名 Asserion.test_strings', () => {
    expect(xml).toContain('Asserion.test_strings')
    expect(xml).toContain('<stringProp name="Assertion.test_field">Assertion.response_code</stringProp>')
    expect(xml).toContain('<stringProp name="Assertion.test_field">Assertion.response_data</stringProp>')
  })

  it('思考时间生成固定定时器', () => {
    expect(xml).toContain('<ConstantTimer')
    expect(xml).toContain('<stringProp name="ConstantTimer.delay">200</stringProp>')
  })

  it('duration>0 时启用调度器且循环为无限', () => {
    const by = buildJmx({ ...model, duration: 60, thinkTime: 0 })
    expect(by).toContain('<boolProp name="ThreadGroup.scheduler">true</boolProp>')
    expect(by).toContain('<stringProp name="ThreadGroup.duration">60</stringProp>')
    expect(by).toContain('<stringProp name="LoopController.loops">-1</stringProp>')
    expect(by).not.toContain('<ConstantTimer')
  })
})

describe('jmx-parser 从 .jmx 还原用例模型', () => {
  it('与 builder 往返一致（round-trip）', () => {
    const parsed = parseJmx(buildJmx(model))
    expect(parsed.name).toBe('登录接口压测')
    expect(parsed.threads).toBe(20)
    expect(parsed.rampUp).toBe(5)
    expect(parsed.loops).toBe(10)
    expect(parsed.thinkTime).toBe(200)
    expect(parsed.onSampleError).toBe('continue')
    expect(parsed.variables).toEqual([{ key: 'baseUrl', value: 'http://127.0.0.1:4000', enabled: true }])
    expect(parsed.steps).toHaveLength(2)

    const [login, list] = parsed.steps
    expect(login.name).toBe('登录')
    expect(login.method).toBe('POST')
    expect(login.url).toBe('${baseUrl}/api/auth/login')
    expect(login.body).toBe('{"username":"admin","password":"admin@123"}')
    expect(login.headers).toEqual([{ key: 'Content-Type', value: 'application/json', enabled: true }])
    expect(login.assertions).toEqual([{ type: 'responseCode', operator: 'equals', expected: '200' }])

    expect(list.method).toBe('GET')
    expect(list.query).toEqual([{ key: 'page', value: '1', enabled: true }])
    expect(list.assertions).toEqual([{ type: 'responseText', operator: 'contains', expected: '"total"' }])
  })

  it('可解析标准 JMeter GUI 写法（protocol/domain/port 分离）', () => {
    const guiStyle = `<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.2.1">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="GUI 计划" enabled="true">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" testclass="Arguments" testname="User Defined Variables" enabled="true">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="线程组" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">stoptest</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" testclass="LoopController" enabled="true">
          <stringProp name="LoopController.loops">3</stringProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">8</stringProp>
        <stringProp name="ThreadGroup.ramp_time">2</stringProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
        <stringProp name="ThreadGroup.duration"></stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="首页" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" testclass="Arguments" enabled="true">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
          <stringProp name="HTTPSampler.domain">example.com</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.protocol">https</stringProp>
          <stringProp name="HTTPSampler.path">/api/ping</stringProp>
          <stringProp name="HTTPSampler.method">GET</stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>`
    const parsed = parseJmx(guiStyle)
    expect(parsed.name).toBe('GUI 计划')
    expect(parsed.threads).toBe(8)
    expect(parsed.rampUp).toBe(2)
    expect(parsed.loops).toBe(3)
    expect(parsed.onSampleError).toBe('stoptest')
    expect(parsed.steps).toHaveLength(1)
    expect(parsed.steps[0].url).toBe('https://example.com:8080/api/ping')
  })

  it('二次往返保持稳定（导出→导入→导出）', () => {
    const once = buildJmx(model)
    const twice = buildJmx(parseJmx(once))
    expect(parseJmx(twice)).toEqual(parseJmx(once))
  })

  it('非 JMeter 文件给出明确错误', () => {
    expect(() => parseJmx('<html>nope</html>')).toThrow(/不是有效的 JMeter 测试计划/)
    expect(() => parseJmx('')).toThrow()
  })
})

describe('buildJmxMulti 多用例合并导出', () => {
  const second: PerfCaseModel = {
    ...model,
    name: '下单接口压测',
    threads: 5,
    rampUp: 2,
    loops: 3,
    thinkTime: 0,
    variables: [{ key: 'token', value: 'abc', enabled: true }],
    steps: [{ id: 'x1', name: '下单', method: 'POST', url: '${baseUrl}/api/order', enabled: true }],
  }
  const xml = buildJmxMulti([model, second], '批量导出')

  it('一个测试计划内包含多个线程组', () => {
    expect(xml).toContain('testname="批量导出"')
    expect(xml.split('<ThreadGroup ').length - 1).toBe(2)
    expect(xml).toContain('testname="登录接口压测"')
    expect(xml).toContain('testname="下单接口压测"')
    expect(xml).toContain('<stringProp name="ThreadGroup.num_threads">20</stringProp>')
    expect(xml).toContain('<stringProp name="ThreadGroup.num_threads">5</stringProp>')
  })

  it('合并各用例自定义变量（按 key 去重）', () => {
    expect(xml).toContain('<stringProp name="Argument.name">baseUrl</stringProp>')
    expect(xml).toContain('<stringProp name="Argument.name">token</stringProp>')
  })

  it('单用例与多用例结构一致', () => {
    expect(buildJmx(model)).toBe(buildJmxMulti([model]))
  })

  it('空列表只生成空测试计划', () => {
    const empty = buildJmxMulti([])
    expect(empty).toContain('<jmeterTestPlan')
    expect(empty).not.toContain('<ThreadGroup ')
    expect(() => parseJmx(empty)).toThrow()
  })
})
