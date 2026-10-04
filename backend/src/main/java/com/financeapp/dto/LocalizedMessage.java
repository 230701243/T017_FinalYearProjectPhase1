package com.financeapp.dto;

import java.util.Collections;
import java.util.Map;

public record LocalizedMessage(
    String code,
    Map<String, Object> params
) {
    public LocalizedMessage(String code) {
        this(code, Collections.emptyMap());
    }

    public static LocalizedMessage of(String code) {
        return new LocalizedMessage(code, Collections.emptyMap());
    }

    public static LocalizedMessage of(String code, Map<String, Object> params) {
        return new LocalizedMessage(code, params != null ? params : Collections.emptyMap());
    }
}
