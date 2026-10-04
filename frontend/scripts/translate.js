#!/usr/bin/env node
/**
 * Translation Generation Script (scripts/translate.js)
 * One-time offline translation tool using the Gemini API (or fallback batch engine).
 * 
 * Rules:
 * 1. Reads src/locales/en.json and generates one JSON file per language in languages.js
 * 2. Translates in batches and preserves all {{placeholders}} exactly
 * 3. Enforces glossary:
 *    - Untranslated: FinanceApp, UPI, EMI, PPF, EPF, NPS, RD, FD, ELSS, SGB
 *    - Consistent terms for: reserve, risk, forecast, savings goal, income, expense
 * 4. Writes locales/status.json: every generated language "beta", with "en", "hi", "ta" set to "reviewed"
 * 5. Must NOT be called at app runtime.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Locate frontend paths
let localesDir = path.resolve(__dirname, '../src/locales');
if (!fs.existsSync(localesDir)) localesDir = path.resolve(__dirname, '../frontend/src/locales');
if (!fs.existsSync(localesDir)) localesDir = path.resolve(process.cwd(), 'src/locales');
if (!fs.existsSync(localesDir)) localesDir = path.resolve(process.cwd(), 'frontend/src/locales');

let languagesConfigPath = path.resolve(__dirname, '../src/config/languages.js');
if (!fs.existsSync(languagesConfigPath)) languagesConfigPath = path.resolve(__dirname, '../frontend/src/config/languages.js');
if (!fs.existsSync(languagesConfigPath)) languagesConfigPath = path.resolve(process.cwd(), 'src/config/languages.js');

const backendLocalesDir = path.resolve(__dirname, '../../backend/src/main/resources/locales');

console.log(`\n======================================================`);
console.log(`[translate.js] FinanceApp Localization Engine`);
console.log(`Locales directory: ${localesDir}`);
console.log(`======================================================\n`);

const enPath = path.join(localesDir, 'en.json');
if (!fs.existsSync(enPath)) {
  console.error(`Baseline en.json not found at: ${enPath}`);
  process.exit(1);
}

const enData = JSON.parse(fs.readFileSync(enPath, 'utf8'));

// Helper: Flatten / Unflatten
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

function unflatten(flatDict) {
  const result = {};
  for (const [key, value] of Object.entries(flatDict)) {
    const parts = key.split('.');
    let curr = result;
    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      if (!curr[part]) curr[part] = {};
      curr = curr[part];
    }
    curr[parts[parts.length - 1]] = value;
  }
  return result;
}

const enFlat = flatten(enData);
const totalKeys = Object.keys(enFlat).length;

// Read languages from languages.js
const langContent = fs.readFileSync(languagesConfigPath, 'utf8');
const langMatches = [...langContent.matchAll(/code:\s*'([^']+)'/g)].map(m => m[1]);
const targetLanguages = [...new Set(langMatches)];

console.log(`Target languages in config (${targetLanguages.length}): ${targetLanguages.join(', ')}\n`);

// Pseudo-localization algorithm for en-XA
function generatePseudoLocale(flatEn) {
  const vmap = {
    'a': 'á', 'e': 'é', 'i': 'í', 'o': 'ó', 'u': 'ú', 'y': 'ý',
    'A': 'Á', 'E': 'É', 'I': 'Í', 'O': 'Ó', 'U': 'Ú', 'Y': 'Ý'
  };
  const result = {};
  for (const [key, text] of Object.entries(flatEn)) {
    const tokens = text.split(/(\{\{[^{}]+\}\}|\{[^{}]+\})/);
    const transformed = tokens.map(t => {
      if (t.startsWith('{') && t.endsWith('}')) return t;
      return t.split('').map(c => vmap[c] || c).join('');
    }).join('');
    const padLen = Math.max(2, Math.round(text.length * 0.4));
    result[key] = `[${transformed} ${'~'.repeat(padLen)}]`;
  }
  return unflatten(result);
}

// Allowed English / brand names
const ALLOWED_ENGLISH = new Set([
  'FinanceApp', 'UPI', 'EMI', 'PPF', 'EPF', 'NPS', 'RD', 'FD', 'ELSS', 'SGB',
  'Sovereign Gold Bond', 'ID', 'OK', 'PDF', 'CSV', 'HTML', 'N/A', 'ALL', 'Beta',
  'USD', 'INR', 'EUR', 'GBP', 'JPY', 'AED', 'CAD', 'AUD', 'SGD', 'CHF', 'CNY'
]);

// Gemini API Key from environment variable
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;

/**
 * Translates a batch of key-value pairs using the Gemini API
 */
async function translateWithGemini(batch, langCode, langName) {
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${GEMINI_API_KEY}`;
  
  const systemPrompt = `You are a financial translation engine translating a web application from English to ${langName} (${langCode}).
STRICT RULES:
1. Preserve all placeholders like {{amount}}, {{count}}, {{name}}, {{threshold}}, etc. EXACTLY as they appear. Do not translate placeholder names or remove braces.
2. Glossary rules:
   - Keep "FinanceApp", "UPI", "EMI", and scheme names (PPF, EPF, NPS, RD, FD, ELSS, SGB, Sovereign Gold Bond) UNTRANSLATED.
   - Use standard consistent financial terms for ${langName}:
     - reserve: reserve fund / emergency reserve
     - risk: financial risk
     - forecast: cash flow projection / forecast
     - savings goal: savings target
     - income: income
     - expense: expense / outflow
3. Return ONLY a single raw valid JSON object with the exact same keys as the input, containing the translated values. No markdown ticks, no commentary.`;

  const payload = {
    contents: [
      {
        role: 'user',
        parts: [
          { text: systemPrompt + "\n\nTranslate this JSON:\n" + JSON.stringify(batch) }
        ]
      }
    ],
    generationConfig: {
      temperature: 0.1,
      responseMimeType: "application/json"
    }
  };

  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    throw new Error(`Gemini API error ${res.status}: ${await res.text()}`);
  }

  const json = await res.json();
  const rawText = json.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!rawText) throw new Error("Empty candidate response from Gemini API");
  return JSON.parse(rawText);
}

/**
 * Fallback translator using batch translation endpoint when GEMINI_API_KEY is not set
 */
async function translateWithFallback(batchItems, langCode) {
  const prepared = [];
  const meta = [];

  for (const [k, text] of batchItems) {
    if (ALLOWED_ENGLISH.has(text.trim())) {
      prepared.append ? prepared.append(text) : prepared.push(text);
      meta.push({ phs: [], is_allowed: true });
      continue;
    }
    const phs = text.match(/\{\{[^{}]+\}\}/g) || [];
    let t = text;
    for (let i = 0; i < phs.length; i++) {
      t = t.replaceAll(phs[i], ` PH_${i}_PH `);
    }
    prepared.push(t);
    meta.push({ phs, is_allowed: false });
  }

  const delimiter = "\n\n=====\n\n";
  const combined = prepared.join(delimiter);
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${langCode}&dt=t&q=` + encodeURIComponent(combined);

  const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
  if (!res.ok) throw new Error(`Fallback HTTP ${res.status}`);
  const data = await res.json();
  const transFull = data[0].map(x => x[0] || '').join('');
  const parts = transFull.split(/\n*\s*=====\s*\n*/);

  const result = {};
  for (let idx = 0; idx < batchItems.length; idx++) {
    const [k, originalText] = batchItems[idx];
    const m = meta[idx];
    if (m.is_allowed) {
      result[k] = originalText.trim();
      continue;
    }
    let transText = (parts[idx] || originalText).trim();
    for (let i = 0; i < m.phs.length; i++) {
      const p = m.phs[i];
      const re = new RegExp(`PH\\s*_\\s*${i}\\s*_\\s*PH`, 'gi');
      transText = transText.replace(re, p);
    }
    for (const p of m.phs) {
      if (!transText.includes(p)) transText += ` ${p}`;
    }
    transText = transText.replace(/Finance\s*App/gi, 'FinanceApp');
    result[k] = transText;
  }
  return result;
}

async function main() {
  if (GEMINI_API_KEY) {
    console.log(`✓ GEMINI_API_KEY detected in environment. Using Gemini 2.5 Flash for translations.`);
  } else {
    console.log(`ℹ GEMINI_API_KEY not found in environment. Using fallback batch localization engine.`);
  }

  // 1. Generate en-XA
  console.log(`Generating pseudo-locale en-XA.json...`);
  const enXaData = generatePseudoLocale(enFlat);
  fs.writeFileSync(path.join(localesDir, 'en-XA.json'), JSON.stringify(enXaData, null, 2), 'utf8');
  console.log(`✓ en-XA.json written.`);

  // 2. Iterate languages
  for (const lang of targetLanguages) {
    if (lang === 'en' || lang === 'en-XA') continue;
    const targetFile = path.join(localesDir, `${lang}.json`);
    
    // If language file already exists and has full key count, preserve
    if (fs.existsSync(targetFile)) {
      try {
        const existing = JSON.parse(fs.readFileSync(targetFile, 'utf8'));
        const flatExisting = flatten(existing);
        if (Object.keys(flatExisting).length === totalKeys) {
          console.log(`  ✓ ${lang.padEnd(8)} already complete (${totalKeys} keys) -> skipping.`);
          continue;
        }
      } catch (e) {}
    }

    console.log(`  Translating language: ${lang} (${totalKeys} keys)...`);
    const allEntries = Object.entries(enFlat);
    const translatedFlat = {};
    const batchSize = 35;

    for (let i = 0; i < allEntries.length; i += batchSize) {
      const chunk = allEntries.slice(i, i + batchSize);
      try {
        if (GEMINI_API_KEY) {
          const batchObj = Object.fromEntries(chunk);
          const res = await translateWithGemini(batchObj, lang, lang);
          Object.assign(translatedFlat, res);
        } else {
          const res = await translateWithFallback(chunk, lang);
          Object.assign(translatedFlat, res);
        }
      } catch (err) {
        console.warn(`    Batch ${Math.floor(i / batchSize) + 1} retry for ${lang}: ${err.message}`);
        // Fallback to individual
        for (const [k, text] of chunk) {
          try {
            const res = await translateWithFallback([[k, text]], lang);
            Object.assign(translatedFlat, res);
          } catch (e) {
            translatedFlat[k] = text;
          }
        }
      }
      await new Promise(r => setTimeout(r, 150));
    }

    const finalJson = unflatten(translatedFlat);
    fs.writeFileSync(targetFile, JSON.stringify(finalJson, null, 2), 'utf8');
    console.log(`  ✓ ${lang.padEnd(8)} saved (${Object.keys(translatedFlat).length} keys).`);
  }

  // 3. Write locales/status.json
  console.log(`\nWriting locales/status.json...`);
  const statusMap = {};
  for (const lang of targetLanguages) {
    if (lang === 'en' || lang === 'hi' || lang === 'ta') {
      statusMap[lang] = 'reviewed';
    } else {
      statusMap[lang] = 'beta';
    }
  }
  const statusPath = path.join(localesDir, 'status.json');
  fs.writeFileSync(statusPath, JSON.stringify(statusMap, null, 2), 'utf8');
  console.log(`✓ locales/status.json generated with ${Object.keys(statusMap).length} entries:`);
  console.log(`  - Reviewed: en, hi, ta`);
  console.log(`  - Beta: ${Object.keys(statusMap).filter(k => statusMap[k] === 'beta').length} languages\n`);

  // 4. Synchronize to backend locales directory
  if (fs.existsSync(backendLocalesDir)) {
    console.log(`Synchronizing locale files to backend resources: ${backendLocalesDir}...`);
    const allFiles = fs.readdirSync(localesDir).filter(f => f.endsWith('.json'));
    for (const f of allFiles) {
      fs.copyFileSync(path.join(localesDir, f), path.join(backendLocalesDir, f));
    }
    console.log(`✓ Synchronized ${allFiles.length} files to backend.\n`);
  }

  console.log(`[translate.js] COMPLETED SUCCESSFULLY!\n`);
}

main().catch(err => {
  console.error(`[translate.js ERROR]:`, err);
  process.exit(1);
});
