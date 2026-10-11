import type { ModuleNode } from '@/types';

/**
 * 深度过滤模块树：保留命中关键字（不区分大小写）的节点及其所有祖先。
 *
 * <p>这是目录树搜索体验的核心逻辑：如果只保留命中节点而剪掉父节点，
 * 会导致命中节点无处挂载。采用「命中节点 + 其祖先保留」的策略，
 * 同时命中节点的子树完整保留（避免父命中却丢掉子节点）。
 * 抽离为纯函数以便单元测试。</p>
 *
 * @param nodes 树节点数组
 * @param keyword 过滤关键字（空字符串时不过滤）
 * @returns 过滤后的树；若关键字为空则原样返回
 */
export function filterTreeDeeply(nodes: ModuleNode[], keyword: string): ModuleNode[] {
  if (!keyword) return nodes;
  const out: ModuleNode[] = [];
  for (const n of nodes) {
    const match = n.title.toLowerCase().includes(keyword.toLowerCase());
    if (match) {
      // 父节点命中：保留完整子树，便于查看目录下的所有内容
      out.push(n);
      continue;
    }
    const children = filterTreeDeeply(n.children || [], keyword);
    if (children.length > 0) {
      // 子节点命中：保留该父节点作为挂载点，但只保留命中的子分支
      out.push({ ...n, children });
    }
  }
  return out;
}

/**
 * 判断某节点是否命中关键字（或其后代命中）。
 *
 * @param node 待判定节点
 * @param keyword 关键字
 * @returns true 表示节点自身命中
 */
export function nodeMatches(node: ModuleNode, keyword: string): boolean {
  return node.title.toLowerCase().includes(keyword.toLowerCase());
}
