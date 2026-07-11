import { useEffect, useRef, useState } from 'react'
import './ColorPicker.css'

type PaletteEntry = { hex: string; name?: string }

interface ColorPickerProps {
  /** Currently selected colour (hex), or null if none. */
  value: string | null
  /** Fired with the chosen hex. */
  onChange: (hex: string) => void
  /** Available swatches — plain hex strings or {hex, name}. */
  palette: readonly PaletteEntry[] | readonly string[]
  /** Swatches per row. Default 6. */
  columns?: number
  /** Swatch shape. Default 'circle'. */
  swatchShape?: 'square' | 'circle'
  /** Custom trigger node. Default: a swatch button showing the current value. */
  trigger?: React.ReactNode
  /** Close the popover after a selection. Default true. */
  closeOnSelect?: boolean
  /** Accessible label for the trigger. */
  ariaLabel?: string
}

function normalize(palette: readonly PaletteEntry[] | readonly string[]): PaletteEntry[] {
  return palette.map(c => (typeof c === 'string' ? { hex: c } : c))
}

/**
 * F91 — shared colour picker. Consolidates the three prior copies
 * (TeamsPage inline popup, WorkflowConfigPage ColorPicker + StatusColorPicker).
 *
 * The popover is fixed-positioned off the trigger's bounding rect so it escapes
 * clipping/overflow contexts (taken from the StatusColorPicker implementation).
 * Callers keep their own palettes.
 */
export function ColorPicker({
  value,
  onChange,
  palette,
  columns = 6,
  swatchShape = 'circle',
  trigger,
  closeOnSelect = true,
  ariaLabel = 'Choose color',
}: ColorPickerProps) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState({ top: 0, left: 0 })
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLDivElement>(null)

  const swatches = normalize(palette)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', handler)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', handler)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const toggle = () => {
    if (!open && triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      setPos({ top: rect.bottom + 4, left: rect.left })
    }
    setOpen(o => !o)
  }

  const select = (hex: string) => {
    onChange(hex)
    if (closeOnSelect) setOpen(false)
  }

  const radius = swatchShape === 'circle' ? '50%' : '4px'

  return (
    <div className="ds-color-picker" ref={rootRef}>
      <div
        ref={triggerRef}
        role="button"
        tabIndex={0}
        aria-label={ariaLabel}
        aria-haspopup="true"
        aria-expanded={open}
        title={ariaLabel}
        onClick={toggle}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle() } }}
        className="ds-color-picker-trigger-wrap"
      >
        {trigger ?? (
          <span
            className="ds-color-picker-trigger"
            style={{ backgroundColor: value || '#ccc', borderRadius: radius }}
          />
        )}
      </div>
      {open && (
        <div
          className="ds-color-picker-dropdown"
          style={{ top: pos.top, left: pos.left, gridTemplateColumns: `repeat(${columns}, 1fr)` }}
          role="listbox"
          aria-label={ariaLabel}
        >
          {swatches.map(c => {
            const selected = value != null && value.toUpperCase() === c.hex.toUpperCase()
            return (
              <span
                key={c.hex}
                role="button"
                tabIndex={0}
                aria-label={c.name ? `Select color ${c.name}` : `Select color ${c.hex}`}
                aria-pressed={selected}
                title={c.name || c.hex}
                onClick={() => select(c.hex)}
                onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(c.hex) } }}
                className={`ds-color-swatch ${selected ? 'selected' : ''}`}
                style={{ backgroundColor: c.hex, borderRadius: radius }}
              >
                {selected && (
                  <svg width="12" height="12" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                    <path d="M2.5 7L5.5 10L11.5 4" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </span>
            )
          })}
        </div>
      )}
    </div>
  )
}
