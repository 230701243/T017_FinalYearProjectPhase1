import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  Receipt,
  TrendingUp,
  Target,
  Settings,
  LogOut,
  Download,
  Plus,
  ArrowUpRight,
  ArrowDownRight,
  Wallet,
  PiggyBank,
  CheckCircle2,
  X,
  Repeat,
  Edit2,
  RefreshCw,
  MoreHorizontal,
  ChevronDown,
  Search,
  Shield,
  Globe,
  User,
  AlertTriangle,
  AlertCircle,
  Sparkles
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useTranslation } from 'react-i18next'
import api from '../api/axios'
import LanguagePicker from '../components/LanguagePicker'
import AddExpenseModal from '../components/AddExpenseModal'
import AddIncomeModal from '../components/AddIncomeModal'
import SubscriptionsModal from '../components/SubscriptionsModal'
import SavingsGoalModal from '../components/SavingsGoalModal'
import CategoryChart from '../components/CategoryChart'
import HistoricalTrendChart from '../components/HistoricalTrendChart'
import ForecastPanel from '../components/ForecastPanel'
import CashFlow3MonthChart from '../components/CashFlow3MonthChart'
import RiskScorePanel from '../components/RiskScorePanel'
import SavingsGoalPanel from '../components/SavingsGoalPanel'
import WhatIfSimulatorPanel from '../components/WhatIfSimulatorPanel'
import RecommendationPanel from '../components/RecommendationPanel'
import AiAssistantView from '../components/AiAssistantView'
import AccountSettingsModal from '../components/AccountSettingsModal'
import EditTransactionModal from '../components/EditTransactionModal'
import { formatCurrency, getCategoryDisplayName } from '../utils/currency'

const CURRENCY_FLAG_MAP = {
  USD: '🇺🇸',
  INR: '🇮🇳',
  EUR: '🇪🇺',
  GBP: '🇬🇧',
  JPY: '🇯🇵',
  CAD: '🇨🇦',
  AUD: '🇦🇺',
  SGD: '🇸🇬',
  AED: '🇦🇪',
  CHF: '🇨🇭',
  US: '🇺🇸',
  IN: '🇮🇳',
  EU: '🇪🇺',
  GB: '🇬🇧',
  JP: '🇯🇵',
  CA: '🇨🇦',
  AU: '🇦🇺',
  SG: '🇸🇬',
  AE: '🇦🇪',
  CH: '🇨🇭'
}

const DEFAULT_CURRENCIES = [
  { countryCode: 'US', currencyCode: 'USD', currencySymbol: '$', exchangeRateToUsd: 1.0, currencyName: 'US Dollar' },
  { countryCode: 'IN', currencyCode: 'INR', currencySymbol: '₹', exchangeRateToUsd: 83.5, currencyName: 'Indian Rupee' },
  { countryCode: 'EU', currencyCode: 'EUR', currencySymbol: '€', exchangeRateToUsd: 0.92, currencyName: 'Euro' },
  { countryCode: 'GB', currencyCode: 'GBP', currencySymbol: '£', exchangeRateToUsd: 0.79, currencyName: 'British Pound' },
  { countryCode: 'JP', currencyCode: 'JPY', currencySymbol: '¥', exchangeRateToUsd: 155.0, currencyName: 'Japanese Yen' },
  { countryCode: 'CA', currencyCode: 'CAD', currencySymbol: 'CA$', exchangeRateToUsd: 1.36, currencyName: 'Canadian Dollar' },
  { countryCode: 'AU', currencyCode: 'AUD', currencySymbol: 'A$', exchangeRateToUsd: 1.52, currencyName: 'Australian Dollar' },
  { countryCode: 'SG', currencyCode: 'SGD', currencySymbol: 'S$', exchangeRateToUsd: 1.35, currencyName: 'Singapore Dollar' },
  { countryCode: 'AE', currencyCode: 'AED', currencySymbol: 'د.إ', exchangeRateToUsd: 3.6725, currencyName: 'UAE Dirham' },
  { countryCode: 'CH', currencyCode: 'CHF', currencySymbol: 'CHF', exchangeRateToUsd: 0.90, currencyName: 'Swiss Franc' },
]

const NAV_GROUPS = [
  {
    title: 'Overview',
    items: [
      { id: 'dashboard',    icon: LayoutDashboard, label: 'Dashboard'    },
      { id: 'transactions', icon: Receipt,         label: 'Transactions' },
      { id: 'analytics',    icon: TrendingUp,      label: 'Analytics'    },
      { id: 'assistant',    icon: Sparkles,        label: 'AI Assistant' },
      { id: 'budget',       icon: Target,          label: 'Budget & Goals'},
    ]
  },
  {
    title: 'System',
    items: [
      { id: 'settings',     icon: Settings,        label: 'Settings'     },
    ]
  }
]

function Dashboard({ initialNav = 'dashboard' }) {
  const { t, i18n } = useTranslation()
  const { user, updateUser, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const getResolvedNav = () => {
    if (location.pathname === '/ai-assistant' || location.pathname === '/assistant') return 'assistant'
    return initialNav
  }
  const [activeNav,          setActiveNav]          = useState(getResolvedNav)

  useEffect(() => {
    if (location.pathname === '/ai-assistant' || location.pathname === '/assistant') {
      setActiveNav('assistant')
    } else if (location.pathname === '/dashboard') {
      setActiveNav('dashboard')
    }
  }, [location.pathname])
  const [showMoreMenu,       setShowMoreMenu]       = useState(false)
  const [apiMsg,             setApiMsg]             = useState('')
  const [apiOk,              setApiOk]              = useState(null)
  const [expenses,           setExpenses]           = useState([])
  const [incomes,            setIncomes]            = useState([])
  const [stats,              setStats]              = useState(null)
  const [historyData,        setHistoryData]        = useState(null)
  const [forecastData,       setForecastData]       = useState(null)
  const [cashFlowData,       setCashFlowData]       = useState(null)
  const [riskData,           setRiskData]           = useState(null)
  const [goalsData,          setGoalsData]          = useState([])
  const [recommendationData, setRecommendationData] = useState(null)
  const [isExpenseModal,     setIsExpenseModal]     = useState(false)
  const [isIncomeModal,      setIsIncomeModal]      = useState(false)
  const [isSubsModal,        setIsSubsModal]        = useState(false)
  const [isGoalsModal,       setIsGoalsModal]       = useState(false)
  const [isSettingsModal,    setIsSettingsModal]    = useState(false)
  const [downloadingReport,  setDownloadingReport]  = useState(false)
  const [activeTab,          setActiveTab]          = useState('expenses')
  const [loadingTx,          setLoadingTx]          = useState(true)
  const [successNotification,setSuccessNotification]= useState(null)
  const [editingTransaction, setEditingTransaction] = useState(null)
  const [editingType,        setEditingType]        = useState('expense')

  // Search & Category filter states for Transactions ledger
  const [txSearchQuery,      setTxSearchQuery]      = useState('')
  const [txCategoryFilter,   setTxCategoryFilter]   = useState('ALL')

  // Dedicated Settings Page states
  const [currenciesList,     setCurrenciesList]     = useState(DEFAULT_CURRENCIES)
  const [pageCountry,        setPageCountry]        = useState(user?.country || 'US')
  const [pageCurrency,       setPageCurrency]       = useState(user?.preferredCurrency || 'USD')
  const [pageHealthInsurance,setPageHealthInsurance]= useState(Boolean(user?.hasHealthInsurance))
  const [pageLifeInsurance,  setPageLifeInsurance]  = useState(Boolean(user?.hasLifeInsurance))
  const [pageSettingsSaving, setPageSettingsSaving] = useState(false)
  const [pageSettingsMsg,    setPageSettingsMsg]    = useState({ text: '', type: '' })
  const [pageFieldErrors,    setPageFieldErrors]    = useState({})

  const fetchData = () => {
    setLoadingTx(true)
    Promise.all([
      api.get('/expenses'),
      api.get('/incomes'),
      api.get('/dashboard/stats'),
      api.get('/dashboard/history'),
      api.get('/dashboard/forecast'),
      api.get('/dashboard/cashflow-3month'),
      api.get('/dashboard/risk-score'),
      api.get('/goals'),
      api.get('/dashboard/recommendations')
    ])
      .then(([expRes, incRes, statsRes, historyRes, forecastRes, cfRes, riskRes, goalsRes, recRes]) => {
        setExpenses(expRes.data)
        setIncomes(incRes.data)
        setStats(statsRes.data)
        setHistoryData(historyRes.data)
        setForecastData(forecastRes.data)
        setCashFlowData(cfRes.data)
        setRiskData(riskRes.data)
        setGoalsData(goalsRes.data)
        setRecommendationData(recRes.data)
      })
      .catch(err => {
        console.error('Failed to fetch transaction data', err)
      })
      .finally(() => {
        setLoadingTx(false)
      })
  }

  useEffect(() => {
    api.get('/dashboard')
      .then(res => { setApiMsg(res.data.message); setApiOk(true) })
      .catch(() => { setApiMsg('Could not reach backend'); setApiOk(false) })

    fetchData()

    api.get('/currencies')
      .then(res => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          setCurrenciesList(res.data)
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (user) {
      setPageCountry(user.country || 'US')
      setPageCurrency(user.preferredCurrency || 'USD')
      setPageHealthInsurance(Boolean(user.hasHealthInsurance))
      setPageLifeInsurance(Boolean(user.hasLifeInsurance))
    }
  }, [user])

  const handleSavePageSettings = async (e) => {
    e.preventDefault()
    const errors = {}
    if (!pageCountry || !pageCountry.trim()) {
      errors.country = 'Country code is required (e.g. US, IN).'
    }
    if (Object.keys(errors).length > 0) {
      setPageFieldErrors(errors)
      return
    }

    setPageSettingsSaving(true)
    setPageSettingsMsg({ text: '', type: '' })
    try {
      const res = await api.put('/user/profile', {
        country: pageCountry.trim().toUpperCase(),
        preferredCurrency: pageCurrency.trim().toUpperCase(),
        hasHealthInsurance: pageHealthInsurance,
        hasLifeInsurance: pageLifeInsurance
      })
      updateUser({
        country: res.data.country,
        preferredCurrency: res.data.preferredCurrency,
        hasHealthInsurance: Boolean(res.data.hasHealthInsurance),
        hasLifeInsurance: Boolean(res.data.hasLifeInsurance)
      })
      fetchData()
      setPageSettingsMsg({ text: 'Preferences updated successfully.', type: 'success' })
      setTimeout(() => setPageSettingsMsg({ text: '', type: '' }), 4000)
    } catch (err) {
      setPageSettingsMsg({
        text: err.response?.data?.message || 'Failed to update preferences. Please try again.',
        type: 'error'
      })
    } finally {
      setPageSettingsSaving(false)
    }
  }

  const handleDownloadReport = async () => {
    setDownloadingReport(true)
    try {
      const currentLang = i18n.language || 'en'
      const response = await api.get('/reports/monthly', {
        params: { lang: currentLang },
        responseType: 'blob'
      })
      const blob = new Blob([response.data], { type: 'text/html;charset=utf-8' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `FinanceApp_Monthly_Report_${currentLang}.html`)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      alert('Failed to download report: ' + (err.response?.data?.message || err.message))
    } finally {
      setDownloadingReport(false)
    }
  }

  const handleExpenseAdded = (newExpense) => {
    fetchData()
    if (newExpense) {
      setSuccessNotification({
        type: 'expense',
        amount: newExpense.amount,
        description: newExpense.description,
        paymentMethod: newExpense.paymentMethod
      })
      setActiveTab('expenses')
    }
  }

  const handleIncomeAdded = (newIncome) => {
    fetchData()
    if (newIncome) {
      setSuccessNotification({
        type: 'income',
        amount: newIncome.amount,
        description: newIncome.description,
        source: newIncome.source
      })
      setActiveTab('incomes')
    }
  }
  const handleSubscriptionsUpdated = () => fetchData()
  const handleGoalsUpdated = () => fetchData()

  const handleOpenEditModal = (item, type) => {
    setEditingTransaction(item)
    setEditingType(type)
  }

  const handleCloseEditModal = () => {
    setEditingTransaction(null)
  }

  const handleTransactionSaved = (savedItem) => {
    fetchData()
    if (savedItem) {
      setSuccessNotification({
        type: editingType,
        amount: savedItem.amount,
        description: savedItem.description,
        source: savedItem.source,
        paymentMethod: savedItem.paymentMethod,
        isEdit: true
      })
    }
  }

  const EXPENSE_CATEGORIES = [
    'Food & Dining',
    'Utilities & Bills',
    'Shopping & Tech',
    'Transportation',
    'Entertainment',
    'Subscriptions',
    'Healthcare',
    'Insurance',
    'Investments',
    'Uncategorized'
  ]

  const INCOME_CATEGORIES = [
    'Income & Salary',
    'Freelance & Business',
    'Uncategorized'
  ]

  const handleRecategorizeExpense = async (id, currentCat) => {
    const options = EXPENSE_CATEGORIES.map((c, i) => `${i + 1}. ${c}`).join('\n')
    const promptInput = window.prompt(
      `Select a standard category for this expense:\n${options}\n\n(Enter number 1-${EXPENSE_CATEGORIES.length} or category name):`,
      currentCat || '1'
    )
    if (!promptInput || !promptInput.trim()) return

    const trimmed = promptInput.trim()
    let chosenCat = trimmed
    const num = parseInt(trimmed, 10)
    if (!isNaN(num) && num >= 1 && num <= EXPENSE_CATEGORIES.length) {
      chosenCat = EXPENSE_CATEGORIES[num - 1]
    } else {
      const match = EXPENSE_CATEGORIES.find(c => c.toLowerCase() === trimmed.toLowerCase())
      if (match) {
        chosenCat = match
      } else {
        alert(`Invalid category: "${trimmed}". Please select a valid standard category:\n${EXPENSE_CATEGORIES.join(', ')}`)
        return
      }
    }

    try {
      await api.put(`/expenses/${id}/category`, { category: chosenCat })
      fetchData()
    } catch (err) {
      alert('Failed to recategorize: ' + (err.response?.data?.message || err.message))
    }
  }

  const handleRecategorizeIncome = async (id, currentCat) => {
    const options = INCOME_CATEGORIES.map((c, i) => `${i + 1}. ${c}`).join('\n')
    const promptInput = window.prompt(
      `Select a standard category for this income:\n${options}\n\n(Enter number 1-${INCOME_CATEGORIES.length} or category name):`,
      currentCat || '1'
    )
    if (!promptInput || !promptInput.trim()) return

    const trimmed = promptInput.trim()
    let chosenCat = trimmed
    const num = parseInt(trimmed, 10)
    if (!isNaN(num) && num >= 1 && num <= INCOME_CATEGORIES.length) {
      chosenCat = INCOME_CATEGORIES[num - 1]
    } else {
      const match = INCOME_CATEGORIES.find(c => c.toLowerCase() === trimmed.toLowerCase())
      if (match) {
        chosenCat = match
      } else {
        alert(`Invalid category: "${trimmed}". Please select a valid standard category:\n${INCOME_CATEGORIES.join(', ')}`)
        return
      }
    }

    try {
      await api.put(`/incomes/${id}/category`, { category: chosenCat })
      fetchData()
    } catch (err) {
      alert('Failed to recategorize: ' + (err.response?.data?.message || err.message))
    }
  }

  const handleRetroactiveCategorize = async () => {
    try {
      const res = await api.post('/expenses/recategorize-uncategorized')
      const { totalUncategorizedProcessed, matchedCount, unmatchedCount } = res.data
      alert(`Retroactive Categorization Complete:\n- Processed: ${totalUncategorizedProcessed}\n- Newly Matched: ${matchedCount}\n- Remaining Uncategorized: ${unmatchedCount}`)
      fetchData()
    } catch (err) {
      alert('Failed to run retroactive categorization: ' + (err.response?.data?.message || err.message))
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const initial = user?.username?.[0]?.toUpperCase() ?? 'U'

  const totalBalance    = stats?.totalBalance != null ? Number(stats.totalBalance) : (incomes.reduce((a,c)=>a+Number(c.amount||0),0) - expenses.reduce((a,c)=>a+Number(c.amount||0),0))
  const monthlyIncome   = stats?.monthlyIncome != null ? Number(stats.monthlyIncome) : incomes.reduce((a,c)=>a+Number(c.amount||0),0)
  const monthlyExpenses = stats?.monthlyExpenses != null ? Number(stats.monthlyExpenses) : expenses.reduce((a,c)=>a+Number(c.amount||0),0)
  const savings         = stats?.savings != null ? Number(stats.savings) : (monthlyIncome - monthlyExpenses)
  const savingsRatio    = stats?.savingsRatio != null ? stats.savingsRatio : (monthlyIncome > 0 ? ((savings / monthlyIncome)*100).toFixed(1) : '0.0')
  const momTrendPct     = historyData?.momTrendPercentage != null ? Number(historyData.momTrendPercentage) : 0

  const hasTransactions = expenses.length > 0 || incomes.length > 0

  const formatDate = (isoString) => {
    if (!isoString) return 'N/A'
    const d = new Date(isoString)
    return d.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  return (
    <div className="dashboard-layout">
      {/* ── 240px Sidebar ── */}
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="logo-badge icon-tile">
            <Wallet size={20} strokeWidth={2} />
          </div>
          <span className="logo-title">{t('common.brand')}</span>
        </div>

        <nav className="sidebar-nav" aria-label="Main navigation">
          {NAV_GROUPS.map((group) => (
            <div key={group.title} className="nav-group">
              <span className="nav-group-title">
                {group.title === 'Overview' ? t('nav.overview') : t('nav.system')}
              </span>
              {group.items.map((item) => {
                const Icon = item.icon
                const isActive = activeNav === item.id
                const itemLabel = item.id === 'dashboard' ? t('nav.dashboard') :
                                  item.id === 'transactions' ? t('nav.transactions') :
                                  item.id === 'analytics' ? t('nav.analytics') :
                                  item.id === 'assistant' ? t('nav.assistant') :
                                  item.id === 'budget' ? t('nav.budget') : t('nav.settings')
                return (
                  <button
                    type="button"
                    key={item.id}
                    id={`nav-item-${item.id}`}
                    data-nav={item.id}
                    className={`nav-item${isActive ? ' active' : ''}`}
                    onClick={() => {
                      setActiveNav(item.id)
                      if (item.id === 'assistant') {
                        navigate('/ai-assistant')
                      } else if (item.id === 'dashboard') {
                        navigate('/dashboard')
                      }
                    }}
                  >
                    <span className="nav-icon">
                      <Icon size={20} strokeWidth={isActive ? 2.2 : 2} />
                    </span>
                    <span className="nav-label">{itemLabel}</span>
                  </button>
                )
              })}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div
            className="sidebar-user-chip"
            id="user-chip"
            onClick={() => setIsSettingsModal(true)}
            role="button"
            tabIndex={0}
            title={t('modals.accountSettingsTitle')}
          >
            <div className="user-avatar-tile icon-tile">
              {initial}
            </div>
            <div className="user-info">
              <span className="user-name">{user?.username}</span>
              <div className="user-currency-row">
                <span className="chip chip-info" style={{ fontSize: '12px', padding: '2px 6px' }}>
                  {user?.preferredCurrency || 'USD'}
                </span>
              </div>
            </div>
          </div>

          <button
            id="logout-btn"
            className="btn-logout"
            onClick={handleLogout}
            aria-label={t('common.signOut')}
          >
            <LogOut size={20} strokeWidth={2} />
            <span className="logout-text">{t('common.signOut')}</span>
          </button>
        </div>
      </aside>

      {/* ── Main Area ── */}
      <main className="main-area">
        {/* Topbar */}
        <div className="topbar">
          <div>
            <h1 className="page-title">
              {activeNav === 'dashboard' && t('nav.dashboard')}
              {activeNav === 'transactions' && t('transactions.ledgerTitle')}
              {activeNav === 'analytics' && t('analytics.title')}
              {activeNav === 'assistant' && t('nav.assistant')}
              {activeNav === 'budget' && t('nav.budget')}
              {activeNav === 'settings' && t('settings.title')}
            </h1>
            <p className="page-subtitle">
              {activeNav === 'assistant'
                ? t('assistant.subtitle', 'Chat-based financial guidance and purchase affordability assessments based on your raw financial snapshot')
                : `${t('topbar.welcomeUser', { name: user?.username })} · ${apiMsg || t('topbar.systemOperational')}`
              }
            </p>
          </div>

          <div className="topbar-actions">
            {/* Language Picker Dropdown */}
            <LanguagePicker variant="topbar" id="topbar-language-picker" />

            {/* Secondary Button: Add Income */}
            <button
              id="open-add-income-btn"
              className="btn-secondary"
              onClick={() => setIsIncomeModal(true)}
            >
              <ArrowUpRight size={20} strokeWidth={2} style={{ color: 'var(--color-positive)' }} />
              <span>{t('topbar.addIncome')}</span>
            </button>

            {/* Primary Button: Add Expense */}
            <button
              id="open-add-expense-btn"
              className="btn-primary"
              onClick={() => setIsExpenseModal(true)}
            >
              <Plus size={20} strokeWidth={2} />
              <span>{t('topbar.addExpense')}</span>
            </button>

            {/* More Menu Dropdown: Export Report, Goals, Subscriptions */}
            <div className="dropdown-wrapper">
              <button
                id="more-menu-btn"
                className="btn-secondary"
                onClick={() => setShowMoreMenu(prev => !prev)}
                aria-expanded={showMoreMenu}
                aria-label={t('topbar.moreOptions')}
                style={{ padding: '10px 14px' }}
              >
                <MoreHorizontal size={20} strokeWidth={2} />
                <span>{t('topbar.moreOptions')}</span>
                <ChevronDown size={14} strokeWidth={2} />
              </button>

              {showMoreMenu && (
                <>
                  <div
                    style={{ position: 'fixed', inset: 0, zIndex: 40 }}
                    onClick={() => setShowMoreMenu(false)}
                  />
                  <div className="more-dropdown-menu">
                    <button
                      id="download-report-btn"
                      className="more-dropdown-item"
                      onClick={() => {
                        setShowMoreMenu(false)
                        handleDownloadReport()
                      }}
                      disabled={downloadingReport}
                    >
                      <div className="icon-tile icon-tile-neutral" style={{ width: '28px', height: '28px', minWidth: '28px', minHeight: '28px' }}>
                        <Download size={16} strokeWidth={2} />
                      </div>
                      <div className="dropdown-item-text">
                        <span className="dropdown-item-title">{downloadingReport ? t('topbar.generatingReport') : t('topbar.exportMonthlyReport')}</span>
                        <span className="dropdown-item-subtitle">{t('topbar.downloadReportSubtitle')}</span>
                      </div>
                    </button>

                    <button
                      id="manage-goals-btn"
                      className="more-dropdown-item"
                      onClick={() => {
                        setShowMoreMenu(false)
                        setIsGoalsModal(true)
                      }}
                    >
                      <div className="icon-tile icon-tile-neutral" style={{ width: '28px', height: '28px', minWidth: '28px', minHeight: '28px' }}>
                        <Target size={16} strokeWidth={2} />
                      </div>
                      <div className="dropdown-item-text">
                        <span className="dropdown-item-title">{t('topbar.savingsGoals')}</span>
                        <span className="dropdown-item-subtitle">{t('topbar.savingsGoalsSubtitle')}</span>
                      </div>
                    </button>

                    <button
                      id="manage-subscriptions-btn"
                      className="more-dropdown-item"
                      onClick={() => {
                        setShowMoreMenu(false)
                        setIsSubsModal(true)
                      }}
                    >
                      <div className="icon-tile icon-tile-neutral" style={{ width: '28px', height: '28px', minWidth: '28px', minHeight: '28px' }}>
                        <Repeat size={16} strokeWidth={2} />
                      </div>
                      <div className="dropdown-item-text">
                        <span className="dropdown-item-title">{t('topbar.subscriptionsAndEmis')}</span>
                        <span className="dropdown-item-subtitle">{t('topbar.subscriptionsSubtitle')}</span>
                      </div>
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Success Confirmation Notification */}
        {successNotification && (
          <div
            id="transaction-success-alert"
            className={successNotification.type === 'income' ? 'alert alert-success' : 'alert alert-error'}
            style={{ justifyContent: 'space-between' }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <CheckCircle2 size={18} strokeWidth={2} />
              <div>
                <strong>
                  {successNotification.isEdit
                    ? (successNotification.type === 'income' ? t('dashboard.incomeUpdated') : t('dashboard.expenseUpdated'))
                    : (successNotification.type === 'income' ? t('dashboard.incomeRecorded') : t('dashboard.expenseRecorded'))}
                </strong> —{' '}
                {successNotification.type === 'income' ? (
                  <span>+{formatCurrency(successNotification.amount, user?.preferredCurrency)} for "{successNotification.description}" ({successNotification.source})</span>
                ) : (
                  <span>-{formatCurrency(successNotification.amount, user?.preferredCurrency)} for "{successNotification.description}" ({successNotification.paymentMethod})</span>
                )}
              </div>
            </div>
            <button
              id="dismiss-success-btn"
              onClick={() => setSuccessNotification(null)}
              style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', padding: '2px 6px' }}
              aria-label="Dismiss message"
            >
              <X size={16} />
            </button>
          </div>
        )}

        {/* ── VIEW 1: EXECUTIVE HERO & STATS (Dashboard & Transactions) ── */}
        {(activeNav === 'dashboard' || activeNav === 'transactions') && (
          <>
            {/* 1. Hero Card: Net Balance as the largest figure on the page, with the MoM change shown as a chip */}
            {loadingTx ? (
              <div className="hero-balance-card skeleton-card">
                <div className="skeleton skeleton-title" />
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div className="skeleton skeleton-figure" />
                  <div className="skeleton" style={{ width: '130px', height: '28px', borderRadius: '6px' }} />
                </div>
                <div className="skeleton skeleton-text" style={{ width: '240px' }} />
              </div>
            ) : (
              <div className="hero-balance-card">
                <div className="hero-balance-header">
                  <span className="hero-label">{t('dashboard.netBalance')}</span>
                  {apiOk !== null && (
                    <div className={`api-badge ${apiOk ? 'ok' : 'error'}`}>
                      <span className="dot" />
                      <span>{apiOk ? t('dashboard.operationalStatus') : t('dashboard.offline')}</span>
                    </div>
                  )}
                </div>

                {hasTransactions ? (
                  <>
                    <div className="hero-balance-row">
                      <div
                        className="hero-balance-value"
                        style={{ color: totalBalance >= 0 ? 'var(--text-primary)' : 'var(--color-negative)' }}
                      >
                        {formatCurrency(totalBalance, user?.preferredCurrency)}
                      </div>

                      {historyData && (
                        <span
                          className={momTrendPct > 0 ? 'chip chip-pos' : momTrendPct < 0 ? 'chip chip-neg' : 'chip chip-neutral'}
                          style={{ fontSize: '12.5px', padding: '5px 12px', fontVariantNumeric: 'tabular-nums' }}
                        >
                          {momTrendPct > 0 ? (
                            <ArrowUpRight size={14} strokeWidth={2.5} />
                          ) : momTrendPct < 0 ? (
                            <ArrowDownRight size={14} strokeWidth={2.5} />
                          ) : null}
                          {momTrendPct > 0
                            ? t('dashboard.momChangePositive', { percent: momTrendPct })
                            : momTrendPct < 0
                            ? t('dashboard.momChangeNegative', { percent: momTrendPct })
                            : t('dashboard.momChangeNeutral')}
                        </span>
                      )}
                    </div>

                    <div className="hero-balance-hint">
                      {user?.preferredCurrency || 'USD'}
                    </div>
                  </>
                ) : (
                  <div className="empty-state" style={{ padding: '16px 0', alignItems: 'flex-start', textAlign: 'left' }}>
                    <div className="empty-state-title">{t('dashboard.noTransactionsFound')}</div>
                    <p className="empty-state-desc" style={{ maxWidth: '600px' }}>
                      {t('dashboard.addFirstTransaction')}
                    </p>
                    <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap', marginTop: '6px' }}>
                      <button
                        id="hero-empty-add-expense-btn"
                        className="btn-primary"
                        onClick={() => setIsExpenseModal(true)}
                      >
                        <Plus size={18} strokeWidth={2} /> {t('topbar.addExpense')}
                      </button>
                      <button
                        id="hero-empty-add-income-btn"
                        className="btn-secondary"
                        onClick={() => setIsIncomeModal(true)}
                      >
                        <ArrowUpRight size={18} strokeWidth={2} style={{ color: 'var(--color-positive)' }} /> {t('topbar.addIncome')}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 2. Row of Three Smaller Stat Cards: Income, Expenses, Savings Ratio */}
            {loadingTx ? (
              <div className="stats-grid">
                {[1, 2, 3].map(i => (
                  <div key={i} className="stat-card skeleton-card" style={{ padding: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <div className="skeleton" style={{ width: '90px', height: '14px' }} />
                      <div className="skeleton" style={{ width: '32px', height: '32px', borderRadius: '8px' }} />
                    </div>
                    <div className="skeleton" style={{ width: '130px', height: '28px' }} />
                    <div className="skeleton" style={{ width: '160px', height: '12px' }} />
                  </div>
                ))}
              </div>
            ) : (
              <div className="stats-grid">
                <div className="stat-card">
                  <div className="stat-card-header">
                    <span className="stat-card-label">{t('transactions.income')}</span>
                    <div className="icon-tile icon-tile-pos">
                      <ArrowUpRight size={20} strokeWidth={2} />
                    </div>
                  </div>
                  <div className="stat-card-value" style={{ color: hasTransactions ? 'var(--color-positive)' : 'var(--text-muted)' }}>
                    {hasTransactions ? `+${formatCurrency(monthlyIncome, user?.preferredCurrency)}` : '—'}
                  </div>
                  <div className="stat-card-hint">
                    {t('dashboard.monthlyIncome')}
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-card-header">
                    <span className="stat-card-label">{t('transactions.expenses')}</span>
                    <div className="icon-tile icon-tile-neg">
                      <ArrowDownRight size={20} strokeWidth={2} />
                    </div>
                  </div>
                  <div className="stat-card-value" style={{ color: hasTransactions ? 'var(--color-negative)' : 'var(--text-muted)' }}>
                    {hasTransactions ? `-${formatCurrency(monthlyExpenses, user?.preferredCurrency)}` : '—'}
                  </div>
                  <div className="stat-card-hint">
                    {t('dashboard.monthlyOutflow')}
                  </div>
                </div>

                <div className="stat-card">
                  <div className="stat-card-header">
                    <span className="stat-card-label">{t('dashboard.savingsRatio')}</span>
                    <div className="icon-tile icon-tile-neutral">
                      <PiggyBank size={20} strokeWidth={2} />
                    </div>
                  </div>
                  <div className="stat-card-value" style={{ color: hasTransactions ? (Number(savingsRatio) >= 20 ? 'var(--color-positive)' : 'var(--text-primary)') : 'var(--text-muted)' }}>
                    {hasTransactions ? `${savingsRatio}%` : '—'}
                  </div>
                  <div className="stat-card-hint">
                    {hasTransactions
                      ? t('dashboard.netSavings', { amount: `${savings >= 0 ? '+' : ''}${formatCurrency(savings, user?.preferredCurrency)}` })
                      : t('dashboard.computedFromMonthly')}
                  </div>
                </div>
              </div>
            )}
          </>
        )}

        {/* ── DASHBOARD ITEMS 3 - 7 ── */}
        {activeNav === 'dashboard' && (
          <>
            {/* 3. Cash-Flow Projection */}
            <CashFlow3MonthChart
              cashFlowData={cashFlowData}
              loading={loadingTx}
              onOpenAddTransaction={() => setIsIncomeModal(true)}
            />

            {/* 4. Risk Score */}
            <RiskScorePanel
              riskData={riskData}
              loading={loadingTx}
            />

            {/* 5. What-If Simulator */}
            <WhatIfSimulatorPanel />

            {/* 6. Recommendations */}
            <RecommendationPanel
              recommendationData={recommendationData}
              loading={loadingTx}
            />

            {/* 7. AI Decision Assistant Teaser */}
            <div className="chart-card assistant-teaser-card" id="assistant-teaser-card">
              <div className="chart-header" style={{ alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div className="icon-tile" style={{ width: '38px', height: '38px', borderRadius: '10px', background: 'var(--accent-subtle)', color: 'var(--accent-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Sparkles size={20} strokeWidth={2} />
                  </div>
                  <div>
                    <h3 className="chart-title" style={{ margin: 0, fontSize: '15px' }}>
                      {t('assistant.title', 'AI Decision Assistant')}
                    </h3>
                    <span className="chart-subtitle" style={{ fontSize: '12.5px' }}>
                      {t('assistant.subtitle', 'Chat-based financial guidance and purchase affordability assessments based on your raw financial snapshot')}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn-secondary"
                  id="btn-goto-ai-assistant"
                  onClick={() => {
                    setActiveNav('assistant')
                    navigate('/ai-assistant')
                  }}
                  style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12.5px', padding: '7px 14px' }}
                >
                  <span>{t('assistant.goToAssistant')}</span>
                  <ArrowUpRight size={15} />
                </button>
              </div>
            </div>
          </>
        )}

        {/* ── VIEW 2: TRANSACTIONS FEED (Dedicated transactions tab) ── */}
        {/* ── VIEW 2: TRANSACTIONS FEED (Dedicated transactions tab) ── */}
        {activeNav === 'transactions' && (() => {
          const expenseCategories = ['ALL', ...Array.from(new Set(expenses.map(e => e.category).filter(Boolean)))]
          const incomeCategories = ['ALL', ...Array.from(new Set(incomes.map(i => i.category).filter(Boolean)))]

          const filteredExpenses = expenses.filter(tx => {
            const q = txSearchQuery.toLowerCase().trim()
            const matchesSearch = !q ||
              (tx.description && tx.description.toLowerCase().includes(q)) ||
              (tx.id && tx.id.toString().includes(q)) ||
              (tx.amount && tx.amount.toString().includes(q)) ||
              (tx.category && tx.category.toLowerCase().includes(q)) ||
              (tx.paymentMethod && tx.paymentMethod.toLowerCase().includes(q)) ||
              (tx.matchedRule && tx.matchedRule.toLowerCase().includes(q))

            const matchesCategory = txCategoryFilter === 'ALL' || tx.category === txCategoryFilter
            return matchesSearch && matchesCategory
          })

          const filteredIncomes = incomes.filter(inc => {
            const q = txSearchQuery.toLowerCase().trim()
            const matchesSearch = !q ||
              (inc.description && inc.description.toLowerCase().includes(q)) ||
              (inc.id && inc.id.toString().includes(q)) ||
              (inc.amount && inc.amount.toString().includes(q)) ||
              (inc.category && inc.category.toLowerCase().includes(q)) ||
              (inc.source && inc.source.toLowerCase().includes(q)) ||
              (inc.matchedRule && inc.matchedRule.toLowerCase().includes(q))

            const matchesCategory = txCategoryFilter === 'ALL' || inc.category === txCategoryFilter
            return matchesSearch && matchesCategory
          })

          return (
            <div className="transactions-section page-transition">
              <div className="section-header">
                <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <h2 className="section-title">{t('transactions.ledgerTitle')}</h2>

                  {/* Segmented Control for Expenses / Income Switch */}
                  <div className="segmented-control" role="tablist" aria-label="Transactions view selection">
                    <button
                      className={`segmented-control-btn ${activeTab === 'expenses' ? 'active' : ''}`}
                      onClick={() => { setActiveTab('expenses'); setTxCategoryFilter('ALL'); }}
                      role="tab"
                      aria-selected={activeTab === 'expenses'}
                    >
                      <span>{t('transactions.expenses')}</span>
                      <span className="segmented-count-badge">{expenses.length}</span>
                    </button>
                    <button
                      className={`segmented-control-btn ${activeTab === 'incomes' ? 'active' : ''}`}
                      onClick={() => { setActiveTab('incomes'); setTxCategoryFilter('ALL'); }}
                      role="tab"
                      aria-selected={activeTab === 'incomes'}
                    >
                      <span>{t('transactions.income')}</span>
                      <span className="segmented-count-badge">{incomes.length}</span>
                    </button>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '8px' }}>
                  {activeTab === 'expenses' && (
                    <button
                      className="btn-secondary"
                      onClick={handleRetroactiveCategorize}
                      title="Re-run Categorization against all Uncategorized expenses"
                      style={{ padding: '6px 12px', fontSize: '12px' }}
                    >
                      <RefreshCw size={13} /> {t('transactions.reCategorize')}
                    </button>
                  )}
                  {activeTab === 'expenses' ? (
                    <button
                      className="btn-secondary"
                      onClick={() => setIsExpenseModal(true)}
                      style={{ padding: '6px 14px', fontSize: '12px' }}
                    >
                      <Plus size={14} /> {t('transactions.newExpense')}
                    </button>
                  ) : (
                    <button
                      className="btn-secondary"
                      onClick={() => setIsIncomeModal(true)}
                      style={{ padding: '6px 14px', fontSize: '12px', color: 'var(--color-positive)', borderColor: 'var(--color-positive-border)' }}
                    >
                      <Plus size={14} /> {t('transactions.newIncome')}
                    </button>
                  )}
                </div>
              </div>

              {/* Search Box and Category Filter Toolbar Above the Table */}
              <div className="ledger-filter-toolbar">
                <div className="ledger-search-box">
                  <Search size={15} style={{ color: 'var(--text-muted)' }} />
                  <input
                    type="text"
                    placeholder={activeTab === 'expenses' ? t('transactions.searchPlaceholderExpenses') : t('transactions.searchPlaceholderIncome')}
                    value={txSearchQuery}
                    onChange={(e) => setTxSearchQuery(e.target.value)}
                    className="ledger-search-input"
                    aria-label="Search ledger transactions"
                  />
                  {txSearchQuery && (
                    <button
                      onClick={() => setTxSearchQuery('')}
                      style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '2px', display: 'flex' }}
                      title="Clear search"
                      aria-label="Clear search query"
                    >
                      <X size={14} />
                    </button>
                  )}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <select
                    value={txCategoryFilter}
                    onChange={(e) => setTxCategoryFilter(e.target.value)}
                    className="ledger-filter-select"
                    aria-label="Filter by category"
                  >
                    <option value="ALL">{t('transactions.allCategories')}</option>
                    {(activeTab === 'expenses' ? expenseCategories : incomeCategories)
                      .filter(c => c !== 'ALL')
                      .map(cat => (
                        <option key={cat} value={cat}>{getCategoryDisplayName(cat, t)}</option>
                      ))}
                  </select>

                  {(txSearchQuery || txCategoryFilter !== 'ALL') && (
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                      {t('transactions.showingFiltered', {
                        current: activeTab === 'expenses' ? filteredExpenses.length : filteredIncomes.length,
                        total: activeTab === 'expenses' ? expenses.length : incomes.length
                      })}
                    </span>
                  )}
                </div>
              </div>

              <div className="table-container">
                {loadingTx ? (
                  <div style={{ padding: '28px', textAlign: 'center', color: 'var(--text-muted)' }}>
                    <span className="spinner" style={{ marginRight: '8px' }} /> {t('common.loading')}
                  </div>
                ) : activeTab === 'expenses' ? (
                  filteredExpenses.length === 0 ? (
                    <div style={{ padding: '36px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                      {t('transactions.noExpensesMatched')}
                    </div>
                  ) : (
                    <table className="tx-table" id="expenses-table">
                      <thead>
                        <tr>
                          <th style={{ width: '60px' }}>{t('transactions.id')}</th>
                          <th>{t('transactions.description')}</th>
                          <th>{t('transactions.category')}</th>
                          <th>{t('transactions.matchedRule')}</th>
                          <th>{t('transactions.confidence')}</th>
                          <th className="th-amount" style={{ textAlign: 'right' }}>{t('transactions.amount')}</th>
                          <th>{t('transactions.method')}</th>
                          <th>{t('transactions.timestamp')}</th>
                          <th style={{ width: '60px', textAlign: 'center' }}>{t('transactions.actions')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredExpenses.map((tx) => (
                          <tr key={tx.id}>
                            <td style={{ color: 'var(--text-muted)', fontFamily: 'monospace' }}>#{tx.id}</td>
                            <td style={{ fontWeight: '600' }}>{tx.description}</td>
                            <td>
                              <button
                                onClick={() => handleOpenEditModal(tx, 'expense')}
                                title="Click to edit category, amount, or description"
                                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                              >
                                <span className="badge-cat">
                                  <Edit2 size={11} strokeWidth={2} style={{ color: 'var(--text-muted)' }} />
                                  {getCategoryDisplayName(tx.category, t)}
                                </span>
                              </button>
                            </td>
                            <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                              {tx.matchedRule ? <code>{tx.matchedRule}</code> : <span>—</span>}
                            </td>
                            <td>
                              <span className="badge-confidence">
                                {tx.confidenceSource || 'UNASSIGNED'}
                              </span>
                            </td>
                            <td className="td-amount" style={{ color: 'var(--color-negative)', fontWeight: '700', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                              -{formatCurrency(tx.amount, user?.preferredCurrency)}
                            </td>
                            <td>
                              <span className="badge-payment">
                                {tx.paymentMethod}
                              </span>
                            </td>
                            <td className="tx-timestamp">
                              {formatDate(tx.createdAt)}
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <button
                                id={`edit-expense-${tx.id}-btn`}
                                className="btn-icon-action"
                                onClick={() => handleOpenEditModal(tx, 'expense')}
                                title="Edit expense"
                                aria-label="Edit expense"
                              >
                                <Edit2 size={14} strokeWidth={2} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )
                ) : (
                  filteredIncomes.length === 0 ? (
                    <div style={{ padding: '36px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                      {t('transactions.noIncomeMatched')}
                    </div>
                  ) : (
                    <table className="tx-table" id="incomes-table">
                      <thead>
                        <tr>
                          <th style={{ width: '60px' }}>{t('transactions.id')}</th>
                          <th>{t('transactions.description')}</th>
                          <th>{t('transactions.category')}</th>
                          <th>{t('transactions.matchedRule')}</th>
                          <th>{t('transactions.confidence')}</th>
                          <th className="th-amount" style={{ textAlign: 'right' }}>{t('transactions.amount')}</th>
                          <th>{t('transactions.source')}</th>
                          <th>{t('transactions.timestamp')}</th>
                          <th style={{ width: '60px', textAlign: 'center' }}>{t('transactions.actions')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredIncomes.map((inc) => (
                          <tr key={inc.id}>
                            <td style={{ color: 'var(--text-muted)', fontFamily: 'monospace' }}>#{inc.id}</td>
                            <td style={{ fontWeight: '600' }}>{inc.description}</td>
                            <td>
                              <button
                                onClick={() => handleOpenEditModal(inc, 'income')}
                                title="Click to edit category, amount, or description"
                                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}
                              >
                                <span className="badge-cat">
                                  <Edit2 size={11} strokeWidth={2} style={{ color: 'var(--text-muted)' }} />
                                  {getCategoryDisplayName(inc.category, t)}
                                </span>
                              </button>
                            </td>
                            <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                              {inc.matchedRule ? <code>{inc.matchedRule}</code> : <span>—</span>}
                            </td>
                            <td>
                              <span className="badge-confidence">
                                {inc.confidenceSource || 'UNASSIGNED'}
                              </span>
                            </td>
                            <td className="td-amount" style={{ color: 'var(--color-positive)', fontWeight: '700', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                              +{formatCurrency(inc.amount, user?.preferredCurrency)}
                            </td>
                            <td>
                              <span className="badge-source">
                                {inc.source}
                              </span>
                            </td>
                            <td className="tx-timestamp">
                              {formatDate(inc.createdAt)}
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <button
                                id={`edit-income-${inc.id}-btn`}
                                className="btn-icon-action"
                                onClick={() => handleOpenEditModal(inc, 'income')}
                                title="Edit income"
                                aria-label="Edit income"
                              >
                                <Edit2 size={14} strokeWidth={2} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )
                )}
              </div>
            </div>
          )
        })()}

        {/* ── VIEW 3: ANALYTICS ── */}
        {activeNav === 'analytics' && (
          <div className="page-transition" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Executive Analytics Metrics Row */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-card-header">
                  <span className="stat-card-label">{t('analytics.monthlyOutflow')}</span>
                  <div className="icon-tile icon-tile-neg">
                    <ArrowDownRight size={20} strokeWidth={2} />
                  </div>
                </div>
                <div className="stat-card-value" style={{ color: 'var(--color-negative)' }}>
                  -{formatCurrency(monthlyExpenses, user?.preferredCurrency)}
                </div>
                <div className="stat-card-hint">
                  {t('analytics.activeCategoriesCount', { count: stats?.categoryBreakdown?.length || 0 })}
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-card-header">
                  <span className="stat-card-label">{t('analytics.sixMonthTrend')}</span>
                  <div className="icon-tile icon-tile-neutral">
                    <TrendingUp size={20} strokeWidth={2} />
                  </div>
                </div>
                <div className="stat-card-value" style={{ color: 'var(--text-primary)' }}>
                  {historyData?.momTrendPercentage >= 0 ? `+${historyData.momTrendPercentage}%` : `${historyData?.momTrendPercentage || 0}%`}
                </div>
                <div className="stat-card-hint">
                  {t('analytics.trendDirection', { direction: historyData?.trendDirection || 'STABLE' })}
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-card-header">
                  <span className="stat-card-label">{t('analytics.endingLiquidity')}</span>
                  <div className="icon-tile icon-tile-pos">
                    <PiggyBank size={20} strokeWidth={2} />
                  </div>
                </div>
                <div className="stat-card-value" style={{ color: 'var(--color-positive)' }}>
                  {formatCurrency(cashFlowData?.projections?.[cashFlowData.projections.length - 1]?.endBalance || 0, user?.preferredCurrency)}
                </div>
                <div className="stat-card-hint">
                  {t('analytics.threeMonthTarget')}
                </div>
              </div>
            </div>

            <CategoryChart
              breakdown={stats?.categoryBreakdown || []}
              totalExpenses={monthlyExpenses}
            />

            <HistoricalTrendChart
              history={historyData?.history || []}
              momTrendPercentage={historyData?.momTrendPercentage || 0}
              trendDirection={historyData?.trendDirection || 'STABLE'}
            />

            <CashFlow3MonthChart cashFlowData={cashFlowData} loading={loadingTx} />
          </div>
        )}

        {/* ── VIEW 4: SETTINGS PAGE ── */}
        {activeNav === 'settings' && (
          <div className="page-transition">
            <div className="settings-page-grid">
              <div className="section-header" style={{ marginBottom: '4px' }}>
                <div>
                  <h2 className="section-title">{t('settings.title')}</h2>
                  <p style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
                    {t('settings.subtitle')}
                  </p>
                </div>
                <button
                  className="btn-secondary"
                  onClick={() => setIsSettingsModal(true)}
                  style={{ fontSize: '13px', padding: '8px 14px' }}
                >
                  <Settings size={14} /> {t('modals.accountSettingsTitle')}
                </button>
              </div>

              {pageSettingsMsg.text && (
                <div className={pageSettingsMsg.type === 'success' ? 'alert alert-success' : 'alert alert-error'}>
                  {pageSettingsMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
                  {pageSettingsMsg.text}
                </div>
              )}

              {/* GROUP 1: User Profile & Account */}
              <div className="settings-section-card">
                <div className="settings-section-header">
                  <div>
                    <div className="settings-section-title">
                      <User size={16} style={{ color: 'var(--accent-light)' }} />
                      {t('settings.profileGroupTitle')}
                    </div>
                    <div className="settings-section-desc">
                      {t('settings.profileGroupDesc')}
                    </div>
                  </div>
                  <span className="chip chip-info">{t('settings.activeAccount')}</span>
                </div>

                <div className="two-col-grid" style={{ gap: '16px' }}>
                  <div className="settings-row">
                    <div className="settings-row-info">
                      <span className="settings-row-title">{t('settings.usernameLabel')}</span>
                      <span className="settings-row-desc">{user?.username}</span>
                    </div>
                  </div>
                  <div className="settings-row">
                    <div className="settings-row-info">
                      <span className="settings-row-title">{t('settings.emailLabel')}</span>
                      <span className="settings-row-desc">{user?.email || 'Active User'}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* GROUP 2: Display & Localization Preferences */}
              <div className="settings-section-card">
                <div className="settings-section-header">
                  <div>
                    <div className="settings-section-title">
                      <Globe size={16} style={{ color: 'var(--accent-light)' }} />
                      {t('settings.displayGroupTitle')}
                    </div>
                    <div className="settings-section-desc">
                      {t('settings.displayGroupDesc')}
                    </div>
                  </div>
                  <div className="chip chip-neutral" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {t('settings.multiCurrencyEngine')}
                  </div>
                </div>

                <form onSubmit={handleSavePageSettings} noValidate>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {/* Interface Language */}
                    <div className="form-group">
                      <label className="field-label">
                        <span>{t('settings.languageLabel')}</span>
                      </label>
                      <LanguagePicker variant="topbar" id="page-settings-language-picker" />
                    </div>

                    <div className="form-group">
                      <label className="field-label" htmlFor="page-settings-currency">
                        <span>{t('settings.currencyLabel')}</span>
                        <span style={{ fontSize: '11.5px', color: 'var(--accent-light)', fontWeight: 500 }}>
                          {t('settings.currencyActive', { code: pageCurrency })}
                        </span>
                      </label>
                      <select
                        id="page-settings-currency"
                        value={pageCurrency}
                        onChange={(e) => {
                          const code = e.target.value
                          setPageCurrency(code)
                          const match = (currenciesList.length > 0 ? currenciesList : DEFAULT_CURRENCIES).find(c => c.currencyCode === code)
                          if (match) setPageCountry(match.countryCode)
                        }}
                        className="currency-select-clean"
                      >
                        {(currenciesList.length > 0 ? currenciesList : DEFAULT_CURRENCIES).map(c => {
                          const flag = CURRENCY_FLAG_MAP[c.currencyCode] || CURRENCY_FLAG_MAP[c.countryCode] || '🌐'
                          return (
                            <option key={c.currencyCode} value={c.currencyCode}>
                              {flag}  {c.currencySymbol}  {c.currencyCode} — {c.currencyName || c.countryCode}
                            </option>
                          )
                        })}
                      </select>
                    </div>

                    {/* Active Live Conversion Rate chip */}
                    {(() => {
                      const activeCur = (currenciesList.length > 0 ? currenciesList : DEFAULT_CURRENCIES).find(c => c.currencyCode === pageCurrency) || {
                        currencyCode: 'USD',
                        currencySymbol: '$',
                        exchangeRateToUsd: 1.0
                      }
                      return (
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '12px 16px',
                          background: 'var(--surface-raised)',
                          borderRadius: '10px',
                          border: '1px solid var(--border-subtle)',
                          fontSize: '13px'
                        }}>
                          <span style={{ color: 'var(--text-secondary)' }}>{t('settings.liveConversionNotice')}</span>
                          <span style={{ fontWeight: '600', color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums' }}>
                            {t('settings.liveConversionRate', { rate: `${activeCur.currencySymbol}${activeCur.exchangeRateToUsd ?? 1.0}`, code: activeCur.currencyCode })}
                          </span>
                        </div>
                      )
                    })()}

                    <div className="form-group">
                      <label className="field-label" htmlFor="page-settings-country">{t('settings.countryLabel')}</label>
                      <input
                        id="page-settings-country"
                        type="text"
                        maxLength={4}
                        value={pageCountry}
                        onChange={e => {
                          setPageCountry(e.target.value.toUpperCase())
                          if (pageFieldErrors.country) setPageFieldErrors(prev => ({ ...prev, country: null }))
                        }}
                        placeholder="US"
                        className={`input-field ${pageFieldErrors.country ? 'input-has-error' : ''}`}
                      />
                      {pageFieldErrors.country && (
                        <div className="field-error">
                          <AlertCircle size={13} />
                          <span>{pageFieldErrors.country}</span>
                        </div>
                      )}
                    </div>

                    {/* GROUP 3: Financial Protection & Risk Profile */}
                    <div style={{
                      background: 'var(--surface-raised)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: '12px',
                      padding: '16px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '12px',
                      marginTop: '4px'
                    }}>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Shield size={13} />
                        {t('settings.insuranceGroupTitle')}
                      </div>
                      <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          id="page-health-insurance"
                          checked={pageHealthInsurance}
                          onChange={e => setPageHealthInsurance(e.target.checked)}
                          style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                        />
                        <span>{t('settings.healthInsuranceLabel')}</span>
                      </label>
                      <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          id="page-life-insurance"
                          checked={pageLifeInsurance}
                          onChange={e => setPageLifeInsurance(e.target.checked)}
                          style={{ width: '16px', height: '16px', accentColor: 'var(--accent-primary)', cursor: 'pointer' }}
                        />
                        <span>{t('settings.lifeInsuranceLabel')}</span>
                      </label>
                      <div style={{ fontSize: '11.5px', color: 'var(--text-muted)', lineHeight: '1.4' }}>
                        {t('settings.insuranceNotice')}
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '10px' }}>
                      <button
                        id="page-save-settings-btn"
                        type="submit"
                        className="btn-primary"
                        disabled={pageSettingsSaving}
                        style={{ minWidth: '130px' }}
                      >
                        {pageSettingsSaving ? <span className="spinner" /> : t('common.save')}
                      </button>
                    </div>
                  </div>
                </form>
              </div>

              {/* GROUP 4: System Reports & Export */}
              <div className="settings-section-card">
                <div className="settings-section-header">
                  <div>
                    <div className="settings-section-title">
                      <Download size={16} style={{ color: 'var(--accent-light)' }} />
                      {t('settings.auditReportsTitle')}
                    </div>
                    <div className="settings-section-desc">
                      {t('settings.auditReportsDesc')}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                  <div>
                    <div style={{ fontSize: '14px', fontWeight: '600', color: 'var(--text-primary)' }}>{t('settings.monthlyAuditReport')}</div>
                    <div style={{ fontSize: '12.5px', color: 'var(--text-secondary)' }}>{t('settings.monthlyAuditReportDesc')}</div>
                  </div>
                  <button
                    id="page-download-report-btn"
                    className="btn-secondary"
                    onClick={handleDownloadReport}
                    disabled={downloadingReport}
                  >
                    <Download size={15} />
                    <span>{downloadingReport ? t('topbar.generatingReport') : t('settings.downloadReport')}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── VIEW 4: BUDGET & GOALS (Items 8 - 9) ── */}
        {activeNav === 'budget' && (
          <>
            <SavingsGoalPanel
              goals={goalsData}
              loading={loadingTx}
              onOpenGoalsModal={() => setIsGoalsModal(true)}
            />

            <ForecastPanel
              forecastData={forecastData}
              loading={loadingTx}
              onOpenSubscriptions={() => setIsSubsModal(true)}
            />
          </>
        )}

        {/* ── VIEW 6: DEDICATED AI ASSISTANT PAGE ── */}
        {activeNav === 'assistant' && (
          <AiAssistantView />
        )}

        {/* Modals */}
        <AddExpenseModal
          isOpen={isExpenseModal}
          onClose={() => setIsExpenseModal(false)}
          onExpenseAdded={handleExpenseAdded}
        />

        <AddIncomeModal
          isOpen={isIncomeModal}
          onClose={() => setIsIncomeModal(false)}
          onIncomeAdded={handleIncomeAdded}
        />

        <SubscriptionsModal
          isOpen={isSubsModal}
          onClose={() => setIsSubsModal(false)}
          onSubscriptionsUpdated={handleSubscriptionsUpdated}
        />

        <SavingsGoalModal
          isOpen={isGoalsModal}
          onClose={() => setIsGoalsModal(false)}
          onGoalsUpdated={handleGoalsUpdated}
        />

        <AccountSettingsModal
          isOpen={isSettingsModal}
          onClose={() => setIsSettingsModal(false)}
          onSettingsUpdated={() => {
            setSuccessNotification(null)
            fetchData()
          }}
        />

        <EditTransactionModal
          isOpen={!!editingTransaction}
          transaction={editingTransaction}
          type={editingType}
          onClose={handleCloseEditModal}
          onSaved={handleTransactionSaved}
        />
      </main>
    </div>
  )
}

export default Dashboard
