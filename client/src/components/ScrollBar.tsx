import { useEffect, useRef, useState } from 'react'

/**
 * 垂直滚动拖拽条：拖动缩略块上下滚动对应的容器内容。
 * 用于工作台三栏（用例库/编写用例/接口管理）的分界线侧。
 */
export default function ScrollBar({ containerRef }: { containerRef: React.RefObject<HTMLDivElement | null> }) {
  const barRef = useRef<HTMLDivElement>(null)
  const [thumbTop, setThumbTop] = useState(0)
  const [thumbH, setThumbH] = useState(30)
  const [show, setShow] = useState(false)
  const drag = useRef<{ startY: number; startTop: number } | null>(null)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const sync = () => {
      const barH = barRef.current?.clientHeight ?? 0
      const total = el.scrollHeight - el.clientHeight
      if (total <= 0 || barH <= 0) {
        setShow(false)
        return
      }
      setShow(true)
      const h = Math.max(30, (el.clientHeight / el.scrollHeight) * barH)
      setThumbH(h)
      setThumbTop((el.scrollTop / total) * (barH - h))
    }

    sync()
    el.addEventListener('scroll', sync)
    const ro = new ResizeObserver(sync)
    ro.observe(el)
    return () => {
      el.removeEventListener('scroll', sync)
      ro.disconnect()
    }
  }, [containerRef])

  const onMouseDown = (e: React.MouseEvent) => {
    e.preventDefault()
    const el = containerRef.current
    if (!el) return
    drag.current = { startY: e.clientY, startTop: el.scrollTop }

    const onMove = (ev: MouseEvent) => {
      const d = drag.current
      const el2 = containerRef.current
      const barH = barRef.current?.clientHeight ?? 0
      if (!d || !el2 || barH <= 0) return
      const total = el2.scrollHeight - el2.clientHeight
      if (total <= 0) return
      const ratio = total / (barH - thumbH)
      el2.scrollTop = d.startTop + (ev.clientY - d.startY) * ratio
    }
    const onUp = () => {
      drag.current = null
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  if (!show) return null

  return (
    <div
      ref={barRef}
      onMouseDown={onMouseDown}
      style={{ width: 8, background: '#f0f0f0', position: 'relative', flexShrink: 0, cursor: 'pointer', borderRadius: 4 }}
      title="拖动滚动内容"
    >
      <div
        style={{
          position: 'absolute',
          top: thumbTop,
          left: 1,
          width: 6,
          height: thumbH,
          background: '#b8b8b8',
          borderRadius: 3,
        }}
      />
    </div>
  )
}
