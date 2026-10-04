import { LANGUAGE_MAP, DEFAULT_LANGUAGE, SCRIPT_FONTS, SCRIPT_FONT_URLS } from '../config/languages'

const loadedFonts = new Set()

/**
 * Loads font for the target script dynamically and updates html lang and dir.
 */
export function applyLanguageEnvironment(langCode) {
  const conf = LANGUAGE_MAP[langCode] || LANGUAGE_MAP[DEFAULT_LANGUAGE]
  if (!conf) return

  // 1. Set html attributes
  if (typeof document !== 'undefined') {
    document.documentElement.lang = conf.code
    document.documentElement.dir = conf.direction

    // 2. Set font-family on body
    const scriptFont = SCRIPT_FONTS[conf.script] || SCRIPT_FONTS.Latin
    document.body.style.fontFamily = scriptFont

    // 3. Load font stylesheet if not loaded yet
    const fontUrl = SCRIPT_FONT_URLS[conf.script]
    if (fontUrl && !loadedFonts.has(conf.script)) {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = fontUrl
      document.head.appendChild(link)
      loadedFonts.add(conf.script)
    }
  }
}
