#!/usr/bin/env node
/**
 * Locale Validation Script (scripts/check-locales.js)
 * Validates all locale files against the baseline en.json:
 * 1. No missing keys that exist in en.json
 * 2. No extra keys that don't exist in en.json
 * 3. Placeholders (name and count) must match en.json exactly
 * 4. No leftover English for keys that should be translated (glossary/brand excluded)
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Locate locales directory: works from frontend root or project root
let localesDir = path.resolve(__dirname, '../src/locales');
if (!fs.existsSync(localesDir)) {
  localesDir = path.resolve(__dirname, '../frontend/src/locales');
}
if (!fs.existsSync(localesDir)) {
  localesDir = path.resolve(process.cwd(), 'src/locales');
}
if (!fs.existsSync(localesDir)) {
  localesDir = path.resolve(process.cwd(), 'frontend/src/locales');
}

if (!fs.existsSync(localesDir)) {
  console.error(`[check-locales ERROR] Could not find locales directory at: ${localesDir}`);
  process.exit(1);
}

const enPath = path.join(localesDir, 'en.json');
if (!fs.existsSync(enPath)) {
  console.error(`[check-locales ERROR] Baseline en.json not found at: ${enPath}`);
  process.exit(1);
}

const enData = JSON.parse(fs.readFileSync(enPath, 'utf8'));

// Flatten nested object to dot notation
function flatten(obj, prefix = '') {
  const result = {};
  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      Object.assign(result, flatten(value, fullKey));
    } else {
      result[fullKey] = value != null ? String(value) : '';
    }
  }
  return result;
}

// Extract all {{placeholder}} or {placeholder} tokens
function getPlaceholders(str) {
  if (typeof str !== 'string') return [];
  const matches = str.match(/\{\{([^{}]+)\}\}/g) || [];
  return matches.map(m => m.trim()).sort();
}

// Terms allowed to stay in English / Latin across all locales (glossary, schemes, universal acronyms)
const ALLOWED_LEFTOVER_ENGLISH = new Set([
  'FinanceApp',
  'UPI',
  'EMI',
  'PPF',
  'EPF',
  'NPS',
  'RD',
  'FD',
  'ELSS',
  'SGB',
  'Sovereign Gold Bond',
  'ID',
  'OK',
  'PDF',
  'CSV',
  'HTML',
  'N/A',
  'ALL',
  'Beta',
  'USD',
  'INR',
  'EUR',
  'GBP',
  'JPY',
  'AED',
  'CAD',
  'AUD',
  'SGD',
  'CHF',
  'CNY'
]);

function isAllowedEnglish(key, val, enVal) {
  const trimmed = val.trim();
  // Exact brand / glossary term
  if (ALLOWED_LEFTOVER_ENGLISH.has(trimmed)) return true;
  // Purely punctuation / numbers / currency symbols / percentages
  if (/^[0-9\s.,:%/+\-–—()$€£¥₹₽₩#@!?]+$/.test(trimmed)) return true;
  // Pure placeholder / formula strings like "1.00 USD = {{rate}} {{code}}" or "{{percent}}%"
  if (/^[0-9.,\sA-Z=–—]+(\{\{[^{}]+\}\}[0-9.,\sA-Z=–—%]*)+$/.test(trimmed)) return true;
  // Empty or whitespace
  if (!trimmed) return true;
  return false;
}

const enFlat = flatten(enData);
const enKeyCount = Object.keys(enFlat).length;

console.log(`\n======================================================`);
console.log(`[check-locales] Validating Locales against en.json (${enKeyCount} keys)`);
console.log(`Locales directory: ${localesDir}`);
console.log(`======================================================\n`);

const files = fs.readdirSync(localesDir)
  .filter(f => f.endsWith('.json') && f !== 'status.json')
  .sort();

let totalErrors = 0;
const results = [];

for (const file of files) {
  const langCode = file.replace('.json', '');
  const filePath = path.join(localesDir, file);
  let fileData;
  try {
    fileData = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (err) {
    console.error(`❌ [${file}] Failed to parse JSON: ${err.message}`);
    totalErrors++;
    continue;
  }

  const targetFlat = flatten(fileData);
  const targetKeyCount = Object.keys(targetFlat).length;

  const missingKeys = [];
  const extraKeys = [];
  const placeholderErrors = [];
  const leftoverEnglish = [];

  // 1. Missing keys
  for (const k of Object.keys(enFlat)) {
    if (!(k in targetFlat)) {
      missingKeys.push(k);
    }
  }

  // 2. Extra keys
  for (const k of Object.keys(targetFlat)) {
    if (!(k in enFlat)) {
      extraKeys.push(k);
    }
  }

  // 3. Placeholder checks & 4. Leftover English
  for (const [k, enVal] of Object.entries(enFlat)) {
    if (k in targetFlat) {
      const targetVal = targetFlat[k];

      // Check placeholders
      const enP = getPlaceholders(enVal);
      const targetP = getPlaceholders(targetVal);
      if (JSON.stringify(enP) !== JSON.stringify(targetP)) {
        placeholderErrors.push({
          key: k,
          expected: enP,
          actual: targetP,
          enText: enVal,
          targetText: targetVal
        });
      }

      // Check leftover English (only for non-English and non-pseudo-locale)
      if (langCode !== 'en' && langCode !== 'en-XA') {
        if (targetVal.trim() === enVal.trim() && !isAllowedEnglish(k, targetVal, enVal)) {
          leftoverEnglish.push({
            key: k,
            value: targetVal
          });
        }
      }
    }
  }

  const fileErrorCount = missingKeys.length + extraKeys.length + placeholderErrors.length + leftoverEnglish.length;
  totalErrors += fileErrorCount;

  results.push({
    file,
    langCode,
    keyCount: targetKeyCount,
    missing: missingKeys,
    extra: extraKeys,
    placeholders: placeholderErrors,
    leftover: leftoverEnglish,
    passed: fileErrorCount === 0
  });

  if (fileErrorCount === 0) {
    console.log(`  ✓ ${file.padEnd(12)} PASS  (${targetKeyCount}/${enKeyCount} keys, 0 errors)`);
  } else {
    console.log(`  ✗ ${file.padEnd(12)} FAIL  (${fileErrorCount} errors: missing=${missingKeys.length}, extra=${extraKeys.length}, placeholders=${placeholderErrors.length}, leftover=${leftoverEnglish.length})`);
    
    if (missingKeys.length > 0) {
      console.log(`    Missing keys (${missingKeys.length}): ${missingKeys.slice(0, 5).join(', ')}${missingKeys.length > 5 ? '…' : ''}`);
    }
    if (extraKeys.length > 0) {
      console.log(`    Extra keys (${extraKeys.length}): ${extraKeys.slice(0, 5).join(', ')}${extraKeys.length > 5 ? '…' : ''}`);
    }
    if (placeholderErrors.length > 0) {
      console.log(`    Placeholder mismatches (${placeholderErrors.length}):`);
      for (const p of placeholderErrors.slice(0, 3)) {
        console.log(`      - ${p.key}: expected [${p.expected.join(', ')}], found [${p.actual.join(', ')}]`);
      }
    }
    if (leftoverEnglish.length > 0) {
      console.log(`    Leftover English (${leftoverEnglish.length}):`);
      for (const le of leftoverEnglish.slice(0, 3)) {
        console.log(`      - ${le.key}: "${le.value}"`);
      }
    }
  }
}

console.log(`\n------------------------------------------------------`);
const passedFiles = results.filter(r => r.passed).length;
console.log(`Summary: ${passedFiles}/${files.length} locale files passed. Total errors: ${totalErrors}`);
console.log(`------------------------------------------------------\n`);

if (totalErrors > 0) {
  console.error(`[check-locales] FAILED: ${totalErrors} localization error(s) found.\n`);
  process.exit(1);
} else {
  console.log(`[check-locales] SUCCESS: All locale files are 100% compliant with en.json!\n`);
  process.exit(0);
}
