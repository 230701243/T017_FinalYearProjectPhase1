/**
 * Pure functions for Decision Scenario Evaluation.
 */

/**
 * Evaluates the Buy Now option.
 *
 * @param {number} currentSavings Current liquid savings
 * @param {number} purchaseCost   Cost of the purchase
 * @param {number} reserveFloor   Emergency reserve floor
 * @returns {{ feasible: boolean, projectedSavingsOrBudget: number, warnings: string[], rawNumbers: object }}
 */
export function evaluateBuyNow(currentSavings, purchaseCost, reserveFloor) {
  const savings = Number(currentSavings) || 0
  const cost = Number(purchaseCost) || 0
  const floor = Number(reserveFloor) || 0

  const projectedSavings = Math.round((savings - cost) * 100) / 100
  const bufferAboveFloor = Math.round((projectedSavings - floor) * 100) / 100

  const warnings = []
  let feasible = true

  if (projectedSavings < 0) {
    feasible = false
    const deficit = Math.round((cost - savings) * 100) / 100
    warnings.push(`Insufficient funds: Purchase cost (₹${cost}) exceeds current savings (₹${savings}) by ₹${deficit}.`)
  } else if (projectedSavings < floor) {
    feasible = false
    const breachAmount = Math.round((floor - projectedSavings) * 100) / 100
    warnings.push(`Reserve floor breached: Projected savings of ₹${projectedSavings} falls below emergency reserve floor of ₹${floor} by ₹${breachAmount}.`)
  }

  return {
    feasible,
    projectedSavingsOrBudget: projectedSavings,
    warnings,
    rawNumbers: {
      currentSavings: savings,
      purchaseCost: cost,
      reserveFloor: floor,
      projectedSavings,
      bufferAboveFloor
    }
  }
}

/**
 * Evaluates the Wait Option.
 * Computes monthlyNetSavings from the user's actual last 6 months of income minus expenses.
 *
 * @param {number} currentSavings                     Current savings balance
 * @param {number} purchaseCost                       Cost of the purchase
 * @param {number} reserveFloor                       Emergency reserve floor
 * @param {number[]|object[]} monthlyNetSavingsHistoryLast6Months Actual monthly net savings numbers or [{income, expenses}]
 * @returns {{ feasible: boolean, projectedSavingsOrBudget: number, warnings: string[], rawNumbers: object }}
 */
export function evaluateWaitOption(currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistoryLast6Months = []) {
  const savings = Number(currentSavings) || 0
  const cost = Number(purchaseCost) || 0
  const floor = Number(reserveFloor) || 0

  const history = Array.isArray(monthlyNetSavingsHistoryLast6Months) ? monthlyNetSavingsHistoryLast6Months : []
  
  const netSavingsList = history.map(item => {
    if (typeof item === 'number') return item
    if (item && typeof item === 'object') {
      const inc = Number(item.income) || 0
      const exp = Number(item.expenses) || 0
      return inc - exp
    }
    return 0
  })

  const sum = netSavingsList.reduce((acc, v) => acc + v, 0)
  const count = netSavingsList.length > 0 ? netSavingsList.length : 1
  const avgMonthlyNetSavings = Math.round((sum / count) * 100) / 100

  const targetRequired = cost + floor
  const targetDeficit = Math.max(0, targetRequired - savings)

  const warnings = []
  let feasible = true
  let monthsToWait = 0
  let projectedSavingsBeforePurchase = savings
  let projectedSavingsAfterPurchase = Math.round((savings - cost) * 100) / 100

  if (targetDeficit === 0) {
    feasible = true
    monthsToWait = 0
    projectedSavingsBeforePurchase = savings
    projectedSavingsAfterPurchase = Math.round((savings - cost) * 100) / 100
  } else if (avgMonthlyNetSavings <= 0) {
    feasible = false
    monthsToWait = -1
    warnings.push(`Average actual monthly net savings is non-positive (₹${avgMonthlyNetSavings}/mo). Waiting will not accumulate required funds without expense reduction.`)
  } else {
    monthsToWait = Math.ceil(targetDeficit / avgMonthlyNetSavings)
    projectedSavingsBeforePurchase = Math.round((savings + (monthsToWait * avgMonthlyNetSavings)) * 100) / 100
    projectedSavingsAfterPurchase = Math.round((projectedSavingsBeforePurchase - cost) * 100) / 100

    if (monthsToWait > 12) {
      feasible = false
      warnings.push(`Estimated wait time of ${monthsToWait} months exceeds the 12-month short-term horizon.`)
    } else {
      feasible = true
    }
  }

  return {
    feasible,
    projectedSavingsOrBudget: projectedSavingsAfterPurchase,
    warnings,
    rawNumbers: {
      currentSavings: savings,
      purchaseCost: cost,
      reserveFloor: floor,
      targetRequired,
      targetDeficit,
      avgMonthlyNetSavings,
      monthsToWait,
      projectedSavingsBeforePurchase,
      projectedSavingsAfterPurchase,
      historyMonthsCount: history.length
    }
  }
}

/**
 * Evaluates the EMI Option.
 *
 * @param {number} purchaseCost                 Total cost of the purchase
 * @param {number} emiAmount                    Monthly EMI installment
 * @param {number} salary                       Monthly salary / net income
 * @param {number} fixedExpenses                Monthly fixed obligations
 * @param {number} minSavingsGoal               Monthly minimum savings goal
 * @param {number} [emiAffordabilityRatioLimit=0.30] Affordability ratio limit (default 0.30)
 * @returns {{ feasible: boolean, projectedSavingsOrBudget: number, warnings: string[], rawNumbers: object }}
 */
export function evaluateEmiOption(
  purchaseCost,
  emiAmount,
  salary,
  fixedExpenses,
  minSavingsGoal,
  emiAffordabilityRatioLimit = 0.30
) {
  const cost = Number(purchaseCost) || 0
  const emi = Number(emiAmount) || 0
  const sal = Number(salary) || 0
  const fixed = Number(fixedExpenses) || 0
  const goal = Number(minSavingsGoal) || 0
  const limit = Number(emiAffordabilityRatioLimit) || 0.30

  const disposableIncome = Math.round((sal - fixed) * 100) / 100
  const remainingBudget = Math.round((sal - fixed - goal - emi) * 100) / 100

  const emiToSalaryRatio = sal > 0 ? Math.round((emi / sal) * 10000) / 10000 : 1.0

  const warnings = []
  let feasible = true

  if (emiToSalaryRatio > limit) {
    feasible = false
    const actualPct = (emiToSalaryRatio * 100).toFixed(1)
    const limitPct = (limit * 100).toFixed(1)
    warnings.push(`EMI affordability breached: Monthly EMI of ₹${emi} is ${actualPct}% of salary, exceeding the maximum limit of ${limitPct}%.`)
  }

  if (remainingBudget < 0) {
    feasible = false
    warnings.push(`Budget deficit: Monthly uncommitted budget is negative (₹${remainingBudget}) after EMI (₹${emi}), fixed expenses (₹${fixed}), and savings goal (₹${goal}).`)
  }

  if (disposableIncome < emi) {
    feasible = false
    warnings.push(`Disposable income deficit: EMI (₹${emi}) exceeds total disposable income (₹${disposableIncome}).`)
  }

  return {
    feasible,
    projectedSavingsOrBudget: remainingBudget,
    warnings,
    rawNumbers: {
      purchaseCost: cost,
      emiAmount: emi,
      salary: sal,
      fixedExpenses: fixed,
      minSavingsGoal: goal,
      disposableIncome,
      remainingBudget,
      emiToSalaryRatio,
      emiAffordabilityRatioLimit: limit
    }
  }
}
