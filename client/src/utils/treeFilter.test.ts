import { describe, it, expect } from 'vitest';
import { filterTreeDeeply, nodeMatches } from '@/utils/treeFilter';
import type { ModuleNode } from '@/types';

/**
 * 模块树过滤纯函数测试（VirtualTree 组件的核心逻辑）。
 *
 * <p>验证「命中节点 + 祖先保留、父命中保留子树、未命中剪枝」的过滤契约，
 * 这是目录树在大规模模块下搜索体验的关键，回归风险高。
 */
describe('treeFilter 模块树过滤', () => {
  function makeNode(
    key: string,
    title: string,
    type: ModuleNode['type'],
    children: ModuleNode[] = [],
  ): ModuleNode {
    return { key, title, type, sortOrder: 0, children };
  }

  const tree: ModuleNode[] = [
    makeNode('root-1', '接口模块', 'case', [
      makeNode('child-1', '登录用例', 'case'),
      makeNode('child-2', '订单用例', 'api'),
    ]),
    makeNode('root-2', 'UI 模块', 'ui', [makeNode('child-3', '登录页面', 'ui')]),
  ];

  function titles(nodes: ModuleNode[]): string[] {
    return nodes.flatMap((n) => [n.title, ...titles(n.children || [])]);
  }

  it('空关键字时原样返回（不拷贝）', () => {
    expect(filterTreeDeeply(tree, '')).toBe(tree);
  });

  it('关键字命中子节点时，其祖先节点一并保留', () => {
    const result = filterTreeDeeply(tree, '登录');
    const t = titles(result);
    expect(t).toContain('接口模块');
    expect(t).toContain('UI 模块');
    expect(t).toContain('登录用例');
    expect(t).toContain('登录页面');
    expect(t).not.toContain('订单用例');
  });

  it('父节点自身命中时，其子树一并保留', () => {
    const result = filterTreeDeeply(tree, '接口');
    const t = titles(result);
    expect(t).toContain('接口模块');
    expect(t).toContain('登录用例');
    expect(t).toContain('订单用例');
    expect(t).not.toContain('UI 模块');
  });

  it('关键字全部未命中时，过滤为空', () => {
    expect(filterTreeDeeply(tree, '不存在的关键字')).toHaveLength(0);
  });

  it('过滤不区分大小写', () => {
    const result = filterTreeDeeply(tree, 'UI');
    expect(titles(result)).toContain('UI 模块');
  });

  it('nodeMatches 判定节点自身是否命中', () => {
    expect(nodeMatches(makeNode('k', '登录用例', 'case'), '登录')).toBe(true);
    expect(nodeMatches(makeNode('k', '订单用例', 'api'), '登录')).toBe(false);
  });
});
