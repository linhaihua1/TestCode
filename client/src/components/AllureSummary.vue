<template>
  <section class="allure-summary">
    <!-- 状态统计卡片（Allure 标志性大数字） -->
    <div class="allure-cards">
      <div
        v-for="item in cardItems"
        :key="item.key"
        class="allure-card"
        :class="[`allure-card--${item.key}`, { 'is-active': active === item.key }]"
        @click="onCardClick(item.key)"
      >
        <div class="allure-card__num">{{ item.value }}</div>
        <div class="allure-card__label">{{ item.label }}</div>
        <div class="allure-card__pct">{{ item.pct }}%</div>
      </div>
    </div>

    <!-- 状态堆叠进度条 -->
    <div class="allure-bar">
      <div
        v-for="seg in segments"
        :key="seg.key"
        class="allure-bar__seg"
        :class="`allure-bar__seg--${seg.key}`"
        :style="{ width: seg.width }"
        :title="`${seg.label}：${seg.value} (${seg.pct}%)`"
      />
    </div>

    <!-- 状态占比环形图 -->
    <div class="allure-chart">
      <div class="allure-donut" :style="{ background: donutBackground }">
        <div class="allure-donut__hole">
          <div class="allure-donut__total">{{ total }}</div>
          <div class="allure-donut__caption">用例</div>
        </div>
      </div>
      <ul class="allure-legend">
        <li
          v-for="item in cardItems"
          :key="item.key"
          class="allure-legend__item"
          :class="{ 'is-muted': active && active !== item.key }"
          @click="onCardClick(item.key)"
        >
          <span class="allure-legend__dot" :class="`dot--${item.key}`" />
          <span class="allure-legend__label">{{ item.label }}</span>
          <span class="allure-legend__value">{{ item.value }}</span>
          <span class="allure-legend__pct">{{ item.pct }}%</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface StatusStat {
  /** 状态标识：passed | failed | broken | skipped */
  key: 'passed' | 'failed' | 'broken' | 'skipped'
  /** 显示名称 */
  label: string
  /** 数量 */
  value: number
}

const props = defineProps<{
  /** 状态统计（缺省自动补 0） */
  stats: StatusStat[]
}>()

const emit = defineEmits<{
  (e: 'filter', key: string | null): void
}>()

// 供外部 v-model:active 使用（可选）
const active = defineModel<string | null>('active', { default: null })

/** 归一化后的四类状态（确保顺序固定：通过/失败/异常/跳过） */
const normalized = computed<StatusStat[]>(() => {
  const map = new Map(props.stats.map((s) => [s.key, s.value]))
  const order: StatusStat['key'][] = ['passed', 'failed', 'broken', 'skipped']
  const labels: Record<StatusStat['key'], string> = {
    passed: '通过',
    failed: '失败',
    broken: '异常',
    skipped: '跳过'
  }
  return order.map((k) => ({ key: k, label: labels[k], value: map.get(k) || 0 }))
})

const total = computed(() =>
  normalized.value.reduce((acc, s) => acc + s.value, 0)
)

const cardItems = computed(() =>
  normalized.value.map((s) => ({
    ...s,
    pct: total.value === 0 ? 0 : Number(((s.value * 100) / total.value).toFixed(1))
  }))
)

const segments = computed(() =>
  cardItems.value
    .filter((s) => s.value > 0)
    .map((s) => ({ ...s, width: s.pct + '%' }))
)

/** 环形图背景：conic-gradient 拼接各状态占比 */
const donutBackground = computed(() => {
  if (total.value === 0) return '#e2e8f0'
  const colors: Record<StatusStat['key'], string> = {
    passed: '#16a34a',
    failed: '#dc2626',
    broken: '#ea580c',
    skipped: '#94a3b8'
  }
  let acc = 0
  const stops = cardItems.value
    .filter((s) => s.value > 0)
    .map((s) => {
      const from = acc
      acc += s.pct
      return `${colors[s.key]} ${from}% ${acc}%`
    })
  return `conic-gradient(${stops.join(', ')})`
})

function onCardClick(key: string) {
  // 再次点击已选中的卡片取消过滤
  active.value = active.value === key ? null : key
  emit('filter', active.value)
}
</script>

<style scoped>
.allure-summary {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  background: var(--bg-card);
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-lg);
  padding: var(--sp-5);
  box-shadow: var(--sd-sm);
  margin-bottom: var(--sp-5);
}

/* ============ 统计卡片 ============ */
.allure-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-3);
}

.allure-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: var(--sp-4);
  border-radius: var(--rd-md);
  background: var(--bg-subtle);
  border: 1px solid var(--bd-subtle);
  cursor: pointer;
  user-select: none;
  transition: transform var(--dur-fast) var(--ease),
              box-shadow var(--dur-fast) var(--ease),
              border-color var(--dur-fast) var(--ease);
}

.allure-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--sd-md);
  border-color: var(--bd-strong);
}

.allure-card.is-active {
  border-color: currentColor;
  box-shadow: var(--sd-focus);
}

.allure-card__num {
  font-size: var(--fs-3xl);
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.allure-card__label {
  font-size: var(--fs-sm);
  color: var(--tx-3);
}

.allure-card__pct {
  font-size: var(--fs-xs);
  color: var(--tx-4);
}

/* 各状态配色（文字 + 图标同色） */
.allure-card--passed { color: var(--c-success); }
.allure-card--failed { color: var(--c-error); }
.allure-card--broken { color: var(--c-warning); }
.allure-card--skipped { color: var(--tx-4); }

.allure-card--passed .allure-card__num { color: var(--c-success); }
.allure-card--failed .allure-card__num { color: var(--c-error); }
.allure-card--broken .allure-card__num { color: var(--c-warning); }
.allure-card--skipped .allure-card__num { color: var(--tx-4); }

/* ============ 堆叠进度条 ============ */
.allure-bar {
  display: flex;
  height: 10px;
  border-radius: var(--rd-full);
  overflow: hidden;
  background: var(--bg-subtle);
}

.allure-bar__seg {
  height: 100%;
  transition: width var(--dur-base) var(--ease);
}

.allure-bar__seg--passed { background: var(--c-success); }
.allure-bar__seg--failed { background: var(--c-error); }
.allure-bar__seg--broken { background: var(--c-warning); }
.allure-bar__seg--skipped { background: var(--tx-4); }

/* ============ 环形图 + 图例 ============ */
.allure-chart {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
}

.allure-donut {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.allure-donut__hole {
  width: 78px;
  height: 78px;
  border-radius: 50%;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.allure-donut__total {
  font-size: var(--fs-xl);
  font-weight: 700;
  line-height: 1;
  color: var(--tx-1);
  font-variant-numeric: tabular-nums;
}

.allure-donut__caption {
  font-size: var(--fs-xs);
  color: var(--tx-3);
  margin-top: 2px;
}

.allure-legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.allure-legend__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--tx-2);
  cursor: pointer;
  padding: 2px var(--sp-2);
  border-radius: var(--rd-sm);
  transition: opacity var(--dur-fast) var(--ease),
              background var(--dur-fast) var(--ease);
}

.allure-legend__item:hover {
  background: var(--bg-hover);
}

.allure-legend__item.is-muted {
  opacity: 0.35;
}

.allure-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.dot--passed { background: var(--c-success); }
.dot--failed { background: var(--c-error); }
.dot--broken { background: var(--c-warning); }
.dot--skipped { background: var(--tx-4); }

.allure-legend__label {
  color: var(--tx-2);
}

.allure-legend__value {
  margin-left: auto;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--tx-1);
}

.allure-legend__pct {
  width: 48px;
  text-align: right;
  color: var(--tx-4);
  font-variant-numeric: tabular-nums;
}
</style>
