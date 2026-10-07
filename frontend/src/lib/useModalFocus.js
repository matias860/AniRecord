import { useEffect, useRef } from 'react'
const selector = 'button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'
export default function useModalFocus(active = true) {
  const ref = useRef(null)
  useEffect(() => {
    const dialog = ref.current
    if (!active || !dialog) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const previous = document.activeElement
    const inertNodes = new Map()
    const roots = () => [dialog, ...(dialog.classList.contains('top-nine-composer') ? document.querySelectorAll('.top-nine-picker') : [])]
    const candidates = () => [...new Set(roots().flatMap((root) => [...root.querySelectorAll(selector)]))].filter((node) => !node.closest('[inert]') && node.getClientRects().length)
    const syncInert = () => {
      for (const [node, value] of inertNodes) node.inert = value
      inertNodes.clear()
      let branch = dialog
      while (branch.parentElement && branch !== document.body) {
        for (const sibling of branch.parentElement.children) {
          if (sibling === branch || roots().some((root) => sibling.contains(root) || root.contains(sibling))) continue
          inertNodes.set(sibling, sibling.inert)
          sibling.inert = true
        }
        branch = branch.parentElement
      }
    }
    syncInert()
    const observer = new MutationObserver(syncInert)
    observer.observe(document.body, { childList: true, subtree: true })
    dialog.tabIndex = -1
    const frame = requestAnimationFrame(() => (candidates()[0] || dialog).focus({ preventScroll: true }))
    const contain = (event) => {
      if (!roots().some((root) => root.contains(event.target))) (candidates()[0] || dialog).focus({ preventScroll: true })
    }
    const tab = (event) => {
      if (event.key !== 'Tab') return
      const nodes = candidates()
      const index = nodes.indexOf(document.activeElement)
      if (!nodes.length) { event.preventDefault(); dialog.focus(); return }
      if (event.shiftKey && index <= 0) { event.preventDefault(); nodes.at(-1).focus() }
      else if (!event.shiftKey && (index < 0 || index === nodes.length - 1)) { event.preventDefault(); nodes[0].focus() }
    }
    document.addEventListener('focusin', contain)
    document.addEventListener('keydown', tab)
    return () => {
      document.body.style.overflow = previousOverflow
      cancelAnimationFrame(frame)
      observer.disconnect()
      document.removeEventListener('focusin', contain)
      document.removeEventListener('keydown', tab)
      for (const [node, value] of inertNodes) node.inert = value
      if (previous?.isConnected) previous.focus({ preventScroll: true })
    }
  }, [active])
  return ref
}
