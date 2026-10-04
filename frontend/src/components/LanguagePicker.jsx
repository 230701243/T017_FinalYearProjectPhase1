import React, { useState, useRef, useEffect, useMemo } from 'react'
import { Globe, Search, Check, ChevronDown, Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { LANGUAGES, LANGUAGE_MAP } from '../config/languages'
import { loadLanguage } from '../i18n'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'

function LanguagePicker({ variant = 'topbar', id = 'language-picker' }) {
  const { i18n } = useTranslation()
  const { user, updateUser } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const [search, setSearch] = useState('')
  const dropdownRef = useRef(null)
  const searchInputRef = useRef(null)

  const currentCode = i18n.language || 'en'
  const currentLang = LANGUAGE_MAP[currentCode] || LANGUAGE_MAP.en

  // Group languages and filter by search
  const { indianLangs, worldLangs } = useMemo(() => {
    const q = search.trim().toLowerCase()
    const filterFn = (l) => {
      if (!q) return true
      return (
        l.name.toLowerCase().includes(q) ||
        l.nativeName.toLowerCase().includes(q) ||
        l.code.toLowerCase().includes(q) ||
        l.script.toLowerCase().includes(q)
      )
    }

    return {
      indianLangs: LANGUAGES.filter(l => l.group === 'indian' && filterFn(l)),
      worldLangs: LANGUAGES.filter(l => l.group === 'world' && filterFn(l)),
    }
  }, [search])

  // Click outside listener
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setIsOpen(false)
        setSearch('')
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
      // Focus search input on open
      setTimeout(() => {
        if (searchInputRef.current) searchInputRef.current.focus()
      }, 50)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  // Escape key listener
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false)
        setSearch('')
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen])

  const handleSelectLanguage = async (code) => {
    setIsOpen(false)
    setSearch('')
    await loadLanguage(code)

    // If logged in, persist to backend via targeted endpoint
    if (user) {
      updateUser({ preferredLanguage: code })
      try {
        await api.put('/user/language', { preferredLanguage: code })
      } catch (err) {
        console.error('Failed to update preferredLanguage on server:', err)
      }
    }
  }

  return (
    <div
      ref={dropdownRef}
      className={`lang-picker-wrapper ${variant}`}
      id={`${id}-container`}
      style={{ position: 'relative' }}
    >
      <button
        type="button"
        id={id}
        className={`lang-picker-btn ${variant === 'auth' ? 'lang-picker-auth' : 'btn-secondary'}`}
        onClick={() => setIsOpen(prev => !prev)}
        aria-expanded={isOpen}
        aria-label="Select interface language"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: variant === 'auth' ? '8px 12px' : '8px 12px',
          fontSize: '13px',
          fontWeight: '500',
        }}
      >
        <Globe size={16} strokeWidth={2} style={{ color: 'var(--accent-light)' }} />
        <span className="lang-picker-current-label">
          {currentLang.nativeName} ({currentLang.name})
        </span>
        <ChevronDown size={14} strokeWidth={2} style={{ opacity: 0.7 }} />
      </button>

      {isOpen && (
        <div
          id={`${id}-dropdown`}
          className="lang-picker-dropdown"
          role="listbox"
          aria-label="Available languages"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            width: '320px',
            maxHeight: '440px',
            background: 'var(--surface-overlay, #131722)',
            border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
            borderRadius: 'var(--radius-lg, 12px)',
            boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          {/* Search Input */}
          <div
            style={{
              padding: '10px 12px',
              borderBottom: '1px solid var(--border-subtle, rgba(255,255,255,0.08))',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              background: 'var(--bg-primary, #0c0f17)',
            }}
          >
            <Search size={15} style={{ color: 'var(--text-muted)' }} />
            <input
              ref={searchInputRef}
              id={`${id}-search`}
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search language / भाषा खोजें…"
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--text-primary)',
                fontSize: '13px',
                width: '100%',
                outline: 'none',
              }}
            />
          </div>

          {/* Grouped Language List */}
          <div
            style={{
              overflowY: 'auto',
              flex: 1,
              padding: '6px 4px',
            }}
          >
            {/* 1. Indian Languages */}
            {indianLangs.length > 0 && (
              <div style={{ marginBottom: '8px' }}>
                <div
                  style={{
                    padding: '6px 12px 4px',
                    fontSize: '11px',
                    fontWeight: '700',
                    color: 'var(--text-muted)',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Indian Languages ({indianLangs.length})
                </div>
                {indianLangs.map(lang => {
                  const isSelected = lang.code === currentCode
                  return (
                    <button
                      type="button"
                      key={lang.code}
                      id={`lang-opt-${lang.code}`}
                      className={`lang-option-btn ${isSelected ? 'selected' : ''}`}
                      onClick={() => handleSelectLanguage(lang.code)}
                      role="option"
                      aria-selected={isSelected}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        width: '100%',
                        padding: '8px 12px',
                        background: isSelected ? 'rgba(79, 140, 255, 0.15)' : 'transparent',
                        border: 'none',
                        borderRadius: '8px',
                        cursor: 'pointer',
                        textAlign: 'left',
                        color: 'inherit',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <div>
                          <div style={{ fontSize: '13.5px', fontWeight: isSelected ? '700' : '500', color: 'var(--text-primary)' }}>
                            {lang.nativeName}
                          </div>
                          <div style={{ fontSize: '11.5px', color: 'var(--text-secondary)' }}>
                            {lang.name} · {lang.script}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        {lang.status === 'beta' && (
                          <span
                            className="chip-beta"
                            style={{
                              fontSize: '10px',
                              padding: '1px 5px',
                              borderRadius: '4px',
                              background: 'rgba(234, 179, 8, 0.15)',
                              color: 'var(--color-warning, #eab308)',
                              fontWeight: '600',
                            }}
                          >
                            Beta
                          </span>
                        )}
                        {isSelected && <Check size={16} style={{ color: 'var(--accent-primary)' }} />}
                      </div>
                    </button>
                  )
                })}
              </div>
            )}

            {/* 2. World Languages */}
            {worldLangs.length > 0 && (
              <div>
                <div
                  style={{
                    padding: '6px 12px 4px',
                    fontSize: '11px',
                    fontWeight: '700',
                    color: 'var(--text-muted)',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  World Languages ({worldLangs.length})
                </div>
                {worldLangs.map(lang => {
                  const isSelected = lang.code === currentCode
                  return (
                    <button
                      type="button"
                      key={lang.code}
                      id={`lang-opt-${lang.code}`}
                      className={`lang-option-btn ${isSelected ? 'selected' : ''}`}
                      onClick={() => handleSelectLanguage(lang.code)}
                      role="option"
                      aria-selected={isSelected}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        width: '100%',
                        padding: '8px 12px',
                        background: isSelected ? 'rgba(79, 140, 255, 0.15)' : 'transparent',
                        border: 'none',
                        borderRadius: '8px',
                        cursor: 'pointer',
                        textAlign: 'left',
                        color: 'inherit',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <div>
                          <div style={{ fontSize: '13.5px', fontWeight: isSelected ? '700' : '500', color: 'var(--text-primary)' }}>
                            {lang.nativeName}
                          </div>
                          <div style={{ fontSize: '11.5px', color: 'var(--text-secondary)' }}>
                            {lang.name}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        {lang.status === 'beta' && (
                          <span
                            className="chip-beta"
                            style={{
                              fontSize: '10px',
                              padding: '1px 5px',
                              borderRadius: '4px',
                              background: 'rgba(234, 179, 8, 0.15)',
                              color: 'var(--color-warning, #eab308)',
                              fontWeight: '600',
                            }}
                          >
                            Beta
                          </span>
                        )}
                        {isSelected && <Check size={16} style={{ color: 'var(--accent-primary)' }} />}
                      </div>
                    </button>
                  )
                })}
              </div>
            )}

            {indianLangs.length === 0 && worldLangs.length === 0 && (
              <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                No languages matching "{search}"
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default LanguagePicker
