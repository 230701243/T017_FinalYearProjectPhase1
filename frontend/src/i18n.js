import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import enTranslations from './locales/en.json'
import { LANGUAGES, LANGUAGE_MAP, DEFAULT_LANGUAGE } from './config/languages'
import { applyLanguageEnvironment } from './utils/i18nFontHelper'

// Dynamic glob loader for all locale files in ./locales/
const localeFiles = import.meta.glob('./locales/*.json')

/**
 * Detect initial language on first visit:
 * Check localStorage -> navigator.languages -> fallback to English
 */
export function detectInitialLanguage() {
  if (typeof window !== 'undefined') {
    const stored = localStorage.getItem('finance_language')
    if (stored && LANGUAGE_MAP[stored]) {
      return stored
    }

    const browserLangs = navigator.languages || [navigator.language || '']
    for (const bLang of browserLangs) {
      if (!bLang) continue
      const lower = bLang.toLowerCase()
      const matched = LANGUAGES.find(
        l => l.code.toLowerCase() === lower || l.code.toLowerCase() === lower.split('-')[0]
      )
      if (matched) return matched.code
    }
  }
  return DEFAULT_LANGUAGE
}

const initialLang = detectInitialLanguage()

// Initialize i18next
i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: enTranslations },
    },
    lng: initialLang,
    fallbackLng: DEFAULT_LANGUAGE,
    interpolation: {
      escapeValue: false, // React already protects against XSS
    },
    saveMissing: true,
    missingKeyHandler: (lng, ns, key, fallbackValue) => {
      if (import.meta.env?.DEV) {
        console.warn(`[i18n missing key]: lng="${lng}", key="${key}" -> using fallback: "${fallbackValue}"`)
      }
    },
    react: {
      useSuspense: false,
    },
  })

/**
 * Lazy loads a language bundle on-demand and updates language, direction, and fonts.
 * If a language file does not exist, cleanly catches and falls back to English without crashing.
 */
export async function loadLanguage(langCode) {
  const targetCode = (langCode && LANGUAGE_MAP[langCode]) ? langCode : DEFAULT_LANGUAGE

  if (targetCode !== 'en' && !i18n.hasResourceBundle(targetCode, 'translation')) {
    const filePath = `./locales/${targetCode}.json`
    const loader = localeFiles[filePath]
    if (loader) {
      try {
        const module = await loader()
        const data = module.default || module
        i18n.addResourceBundle(targetCode, 'translation', data, true, true)
      } catch (err) {
        if (import.meta.env?.DEV) {
          console.warn(`[i18n] Failed to load language file for "${targetCode}". Falling back to English.`, err)
        }
      }
    } else {
      if (import.meta.env?.DEV) {
        console.info(`[i18n] No dedicated bundle for "${targetCode}". Falling back cleanly to English without crashing.`)
      }
    }
  }

  await i18n.changeLanguage(targetCode)
  applyLanguageEnvironment(targetCode)
  if (typeof window !== 'undefined') {
    localStorage.setItem('finance_language', targetCode)
  }
}

// Initial environment setup
if (initialLang !== 'en') {
  loadLanguage(initialLang).catch(() => {})
} else {
  applyLanguageEnvironment('en')
}

export default i18n
