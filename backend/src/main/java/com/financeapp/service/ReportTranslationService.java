package com.financeapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.LocalizedMessage;
import com.financeapp.dto.MoneyParam;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportTranslationService {

    private static final Logger log = LoggerFactory.getLogger(ReportTranslationService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> localeBundles = new ConcurrentHashMap<>();

    public record ScriptInfo(
            String script,
            String fontName,
            String fontCss,
            String googleFontUrl,
            boolean isRtl
    ) {}

    @PostConstruct
    public void init() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:locales/*.json");
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename != null && filename.endsWith(".json")) {
                    String langCode = filename.substring(0, filename.length() - 5);
                    try (InputStream is = r.getInputStream()) {
                        Map<String, Object> map = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                        localeBundles.put(langCode.toLowerCase(), map);
                        log.info("[REPORT-I18N] Loaded locale bundle '{}' with {} root keys", langCode, map.size());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[REPORT-I18N] Error loading locale files: {}", e.getMessage(), e);
        }
    }

    public ScriptInfo getScriptInfo(String rawLang) {
        String lang = (rawLang != null && !rawLang.isBlank()) ? rawLang.trim().toLowerCase() : "en";
        // Extract base language if hyphenated (e.g. "hi-IN" -> "hi", "zh-CN" -> special case)
        String base = lang.contains("-") && !lang.equals("zh-cn") ? lang.split("-")[0] : lang;

        switch (base) {
            case "hi":
            case "mr":
            case "ne":
            case "sa":
            case "kok":
            case "mai":
            case "doi":
            case "brx":
                return new ScriptInfo(
                        "Devanagari",
                        "Noto Sans Devanagari",
                        "'Noto Sans Devanagari', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Devanagari:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "ta":
                return new ScriptInfo(
                        "Tamil",
                        "Noto Sans Tamil",
                        "'Noto Sans Tamil', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Tamil:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "te":
                return new ScriptInfo(
                        "Telugu",
                        "Noto Sans Telugu",
                        "'Noto Sans Telugu', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Telugu:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "kn":
                return new ScriptInfo(
                        "Kannada",
                        "Noto Sans Kannada",
                        "'Noto Sans Kannada', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Kannada:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "ml":
                return new ScriptInfo(
                        "Malayalam",
                        "Noto Sans Malayalam",
                        "'Noto Sans Malayalam', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Malayalam:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "bn":
            case "as":
                return new ScriptInfo(
                        "Bengali",
                        "Noto Sans Bengali",
                        "'Noto Sans Bengali', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "gu":
                return new ScriptInfo(
                        "Gujarati",
                        "Noto Sans Gujarati",
                        "'Noto Sans Gujarati', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Gujarati:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "pa":
                return new ScriptInfo(
                        "Gurmukhi",
                        "Noto Sans Gurmukhi",
                        "'Noto Sans Gurmukhi', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Gurmukhi:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "or":
                return new ScriptInfo(
                        "Odia",
                        "Noto Sans Oriya",
                        "'Noto Sans Oriya', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Oriya:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "ar":
            case "ur":
            case "sd":
            case "ks":
                return new ScriptInfo(
                        "Arabic",
                        "Noto Sans Arabic",
                        "'Noto Sans Arabic', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+Arabic:wght@400;500;600;700;800&display=swap",
                        true
                );
            case "ja":
                return new ScriptInfo(
                        "Japanese",
                        "Noto Sans JP",
                        "'Noto Sans JP', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "zh":
            case "zh-cn":
                return new ScriptInfo(
                        "Chinese",
                        "Noto Sans SC",
                        "'Noto Sans SC', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700;800&display=swap",
                        false
                );
            case "ko":
                return new ScriptInfo(
                        "Korean",
                        "Noto Sans KR",
                        "'Noto Sans KR', system-ui, -apple-system, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700;800&display=swap",
                        false
                );
            default:
                return new ScriptInfo(
                        "Latin",
                        "Inter",
                        "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
                        "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
                        false
                );
        }
    }

    public String getMessage(String lang, String dotKey, String fallback) {
        return getMessage(lang, dotKey, Collections.emptyMap(), fallback);
    }

    public String getMessage(String rawLang, String dotKey, Map<String, Object> params, String fallback) {
        String lang = (rawLang != null && !rawLang.isBlank()) ? rawLang.trim().toLowerCase() : "en";
        String template = findKeyInBundle(lang, dotKey);
        if (template == null && !lang.equals("en")) {
            template = findKeyInBundle("en", dotKey);
        }
        if (template == null) {
            template = fallback != null ? fallback : dotKey;
        }

        if (params == null || params.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String valStr = formatParamValue(entry.getValue(), lang);
            result = result.replace("{{" + entry.getKey() + "}}", valStr)
                           .replace("{" + entry.getKey() + "}", valStr);
        }
        return result;
    }

    public String resolveMessage(String lang, LocalizedMessage message) {
        if (message == null || message.code() == null) {
            return "";
        }
        return getMessage(lang, message.code(), message.params(), message.code());
    }

    public String getCategoryDisplayName(String lang, String canonicalCategory) {
        if (canonicalCategory == null || canonicalCategory.isBlank()) {
            return getMessage(lang, "category.uncategorized", "Uncategorized");
        }
        String key = mapCategoryToKey(canonicalCategory);
        return getMessage(lang, key, canonicalCategory);
    }

    private String mapCategoryToKey(String cat) {
        String lower = cat.toLowerCase().trim();
        if (lower.contains("food") || lower.contains("dining")) return "category.food";
        if (lower.contains("transport")) return "category.transportation";
        if (lower.contains("util") || lower.contains("bill")) return "category.utilities";
        if (lower.contains("health") || lower.contains("medic")) return "category.healthcare";
        if (lower.contains("hous") || lower.contains("rent")) return "category.housing";
        if (lower.contains("entert") || lower.contains("leisure")) return "category.entertainment";
        if (lower.contains("personal")) return "category.personal";
        if (lower.contains("educat")) return "category.education";
        if (lower.contains("shop")) return "category.shopping";
        if (lower.contains("invest")) return "category.investments";
        if (lower.contains("saving")) return "category.savings";
        if (lower.contains("income")) return "category.income";
        return "category.uncategorized";
    }

    @SuppressWarnings("unchecked")
    private String findKeyInBundle(String lang, String dotKey) {
        Map<String, Object> bundle = localeBundles.get(lang);
        if (bundle == null && lang.contains("-")) {
            bundle = localeBundles.get(lang.split("-")[0]);
        }
        if (bundle == null) return null;

        String[] parts = dotKey.split("\\.");
        Object current = bundle;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current instanceof String ? (String) current : null;
    }

    private String formatParamValue(Object val, String lang) {
        if (val == null) return "";
        if (val instanceof MoneyParam mp) {
            return formatMoney(mp.amount(), mp.currency(), lang);
        }
        if (val instanceof Map<?, ?> m) {
            Object amt = m.get("amount");
            Object cur = m.get("currency");
            if (amt != null && cur != null) {
                try {
                    BigDecimal num = (amt instanceof BigDecimal b) ? b : new BigDecimal(amt.toString());
                    return formatMoney(num, cur.toString(), lang);
                } catch (Exception ignored) {}
            }
        }
        if (val instanceof BigDecimal b) {
            return NumberFormat.getNumberInstance(Locale.US).format(b);
        }
        if (val instanceof Number n) {
            return NumberFormat.getNumberInstance(Locale.US).format(n);
        }
        return val.toString();
    }

    private String formatMoney(BigDecimal amount, String currency, String lang) {
        if (amount == null) return "";
        String symbol = resolveSymbol(currency);
        String formattedNumber = String.format(Locale.US, "%,.2f", amount);
        return symbol + formattedNumber;
    }

    private String resolveSymbol(String currency) {
        if (currency == null) return "$";
        return switch (currency.toUpperCase()) {
            case "INR" -> "₹";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            case "AED" -> "د.إ";
            case "SAR" -> "ر.س";
            case "CAD" -> "CA$";
            case "AUD" -> "A$";
            default -> "$";
        };
    }
}
