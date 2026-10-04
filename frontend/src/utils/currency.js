/**
 * Response/Display layer utility for currency conversion and formatting.
 * Converts USD values into the user's preferred currency using static configuration rates.
 * Formats numbers, currencies, and dates using Intl.NumberFormat and Intl.DateTimeFormat
 * with Latin numbering system by default and Indian digit grouping for INR.
 *
 * NOTE: Internal model calculations (Risk Scoring, ML Forecasting, Scenario Evaluation)
 * remain purely calculated in USD. This utility is applied strictly for presentation.
 */

import { getFormattingLocale } from '../config/languages.js'

export const STATIC_RATES = {
  USD: { symbol: '$', rate: 1.0, name: 'US Dollar' },
  INR: { symbol: '₹', rate: 83.50, name: 'Indian Rupee' },
  EUR: { symbol: '€', rate: 0.92, name: 'Euro' },
  GBP: { symbol: '£', rate: 0.79, name: 'British Pound' },
  JPY: { symbol: '¥', rate: 155.0, name: 'Japanese Yen' },
  CAD: { symbol: 'CA$', rate: 1.36, name: 'Canadian Dollar' },
  AUD: { symbol: 'A$', rate: 1.52, name: 'Australian Dollar' },
  SGD: { symbol: 'S$', rate: 1.35, name: 'Singapore Dollar' },
  AED: { symbol: 'د.إ', rate: 3.6725, name: 'UAE Dirham' },
  CHF: { symbol: 'CHF', rate: 0.90, name: 'Swiss Franc' },
}

/**
 * Returns the currency symbol for a currency code.
 */
export function getCurrencySymbol(currencyCode) {
  if (!currencyCode) return '$'
  const code = currencyCode.toUpperCase()
  return STATIC_RATES[code]?.symbol || '$'
}

/**
 * Converts a USD amount to target currency.
 */
export function convertUsd(usdAmount, currencyCode) {
  if (usdAmount === null || usdAmount === undefined || isNaN(usdAmount)) return 0
  const code = (currencyCode || 'USD').toUpperCase()
  const rate = STATIC_RATES[code]?.rate ?? 1.0
  return Number(usdAmount) * rate
}

/**
 * Converts an amount from user's preferred currency back to standard USD for backend persistence.
 */
export function toUsd(amountInPreferredCurrency, currencyCode) {
  if (amountInPreferredCurrency === null || amountInPreferredCurrency === undefined || isNaN(amountInPreferredCurrency)) return 0
  const code = (currencyCode || 'USD').toUpperCase()
  const rate = STATIC_RATES[code]?.rate ?? 1.0
  if (rate === 0) return Number(amountInPreferredCurrency)
  return Number(amountInPreferredCurrency) / rate
}

/**
 * Formats a USD amount into the preferred currency string with the correct symbol.
 * Uses Intl.NumberFormat with Latin numbering system.
 * Keeps Indian digit grouping (e.g. 1,00,000.00) for INR.
 * Example: formatCurrency(100, 'INR') -> "₹8,350.00"
 */
export function formatCurrency(usdAmount, currencyCode, decimals = 2, langCode = null) {
  if (usdAmount === null || usdAmount === undefined || isNaN(usdAmount)) {
    const sym = getCurrencySymbol(currencyCode)
    return `${sym}0.00`
  }
  const code = (currencyCode || 'USD').toUpperCase()
  const conf = STATIC_RATES[code] || { symbol: '$', rate: 1.0 }
  const converted = Number(usdAmount) * conf.rate

  // Locale determination:
  // For INR, always keep Indian digit grouping ('en-IN')
  // Otherwise use the user's active locale if provided, fallback to en-US
  let locale = 'en-US'
  if (code === 'INR') {
    locale = 'en-IN'
  } else if (langCode) {
    locale = getFormattingLocale(langCode)
  }

  try {
    const formatter = new Intl.NumberFormat(locale, {
      numberingSystem: 'latn',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })
    return `${conf.symbol}${formatter.format(converted)}`
  } catch (e) {
    return `${conf.symbol}${converted.toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })}`
  }
}

/**
 * Formats a numeric value with Latin numbering system and locale grouping.
 */
export function formatNumber(value, langCode = null, decimals = 2) {
  if (value === null || value === undefined || isNaN(value)) return '0'
  const locale = langCode ? getFormattingLocale(langCode) : 'en-US'
  try {
    return new Intl.NumberFormat(locale, {
      numberingSystem: 'latn',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(Number(value))
  } catch (e) {
    return Number(value).toFixed(decimals)
  }
}

/**
 * Formats an ISO date string using Intl.DateTimeFormat with Latin numbering system.
 */
export function formatDate(isoString, langCode = null, options = {}) {
  if (!isoString) return 'N/A'
  const locale = langCode ? getFormattingLocale(langCode) : 'en-US'
  try {
    const d = new Date(isoString)
    const defaultOpts = {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      numberingSystem: 'latn',
      ...options,
    }
    return new Intl.DateTimeFormat(locale, defaultOpts).format(d)
  } catch (e) {
    return new Date(isoString).toLocaleString('en-US')
  }
}

/**
 * Formats a typed MoneyParam object { amount: number, currency: string }
 * Note: The amount is ALREADY converted by the backend into the target currency.
 * The frontend only formats with the locale formatter and NEVER converts again!
 */
export function formatMoneyParam(moneyParam, langCode = null, decimals = 2) {
  if (!moneyParam) return ''
  if (typeof moneyParam === 'number' || typeof moneyParam === 'string') {
    return String(moneyParam)
  }
  const { amount, currency } = moneyParam
  if (amount === null || amount === undefined || isNaN(amount)) return ''
  const code = (currency || 'USD').toUpperCase()
  const sym = getCurrencySymbol(code)

  let locale = 'en-US'
  if (code === 'INR') {
    locale = 'en-IN'
  } else if (langCode) {
    locale = getFormattingLocale(langCode)
  }

  try {
    const formatter = new Intl.NumberFormat(locale, {
      numberingSystem: 'latn',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })
    return `${sym}${formatter.format(Number(amount))}`
  } catch (e) {
    return `${sym}${Number(amount).toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })}`
  }
}

/**
 * Resolves a LocalizedMessage { code: string, params: object } using i18next `t` function.
 * Handles typed MoneyParam values ({ amount, currency }), numbers, strings, etc.
 * Falls back to fallbackText if code is not resolved.
 */
export function renderLocalizedMessage(msg, t, langCode = null, fallbackText = '') {
  if (!msg) return fallbackText || ''
  if (typeof msg === 'string') return msg
  if (!msg.code) return fallbackText || ''

  // Format any MoneyParam inside params
  const interpolatedParams = {}
  if (msg.params && typeof msg.params === 'object') {
    for (const [key, val] of Object.entries(msg.params)) {
      if (val && typeof val === 'object' && 'amount' in val && 'currency' in val) {
        interpolatedParams[key] = formatMoneyParam(val, langCode)
      } else {
        interpolatedParams[key] = val
      }
    }
  }

  if (typeof t === 'function') {
    const translated = t(msg.code, interpolatedParams)
    if (translated === msg.code && fallbackText) {
      return fallbackText
    }
    return translated
  }
  return fallbackText || msg.code
}

/**
 * Returns translated display name for category while keeping stored/canonical category unchanged.
 */
export function getCategoryDisplayName(categoryName, t) {
  if (!categoryName) return t ? t('category.uncategorized', 'Uncategorized') : 'Uncategorized'
  const lower = categoryName.toLowerCase().trim()
  let key = 'uncategorized'
  if (lower.includes('food') || lower.includes('dining')) key = 'food'
  else if (lower.includes('transport')) key = 'transportation'
  else if (lower.includes('util') || lower.includes('bill')) key = 'utilities'
  else if (lower.includes('health') || lower.includes('medic')) key = 'healthcare'
  else if (lower.includes('hous') || lower.includes('rent')) key = 'housing'
  else if (lower.includes('entert') || lower.includes('leisure')) key = 'entertainment'
  else if (lower.includes('personal')) key = 'personal'
  else if (lower.includes('educat')) key = 'education'
  else if (lower.includes('shop')) key = 'shopping'
  else if (lower.includes('invest')) key = 'investments'
  else if (lower.includes('saving')) key = 'savings'
  else if (lower.includes('income')) key = 'income'

  if (typeof t === 'function') {
    return t(`category.${key}`, categoryName)
  }
  return categoryName
}
