package com.example.dsl.spec;

import java.util.regex.Pattern;

/**
 * 동적 쿼리 관련 상수 정의.
 *
 * <p>파라미터 키 네이밍 규칙 및 SQL Injection 방어 패턴을 관리한다.</p>
 *
 * <h3>파라미터 키 규칙</h3>
 * <p>언더스코어를 포함하지 않는 camelCase를 사용한다.
 * MyBatis 파라미터 바인딩 시 키 이름이 그대로 사용되므로
 * WMap의 자동 변환 대상이 되지 않아야 한다.</p>
 *
 * <pre>
 * 올바른 예: "xdyWhPrm0"   → 그대로 바인딩
 * 잘못된 예: "xdy_wh_prm_0" → WMap이 "xdyWhPrm0"로 변환 → SQL #{xdy_wh_prm_0} 미매칭
 * </pre>
 */
public final class DynamicQueryKeys {

    private DynamicQueryKeys() {}

    /**
     * whereRaw / havingRaw / onRaw / selectRaw 위험 패턴 감지 정규식.
     *
     * <p>다음 패턴을 차단한다:</p>
     * <ul>
     *   <li>{@code ${...}} — EL 표현식 삽입</li>
     *   <li>{@code ' +} 또는 {@code + '} — 문자열 연결 흔적</li>
     *   <li>{@code --}, {@code /*} — SQL 주석</li>
     *   <li>{@code ;} — 다중 구문</li>
     *   <li>{@code UNION} — UNION 기반 Injection</li>
     *   <li>{@code OR 1=1}, {@code AND 1=1} 류 — 항상 참 조건</li>
     *   <li>{@code DROP}, {@code DELETE}, {@code INSERT} 등 — DDL/DML</li>
     * </ul>
     */
    public static final Pattern RAW_INJECTION_PATTERN = Pattern.compile(
            "\\$\\{[^}]*}" +
                    "|'\\s*\\+" +
                    "|\\+\\s*'" +
                    "|--" +
                    "|/\\*" +
                    "|;" +
                    "|(?i)\\bUNION\\b" +
                    "|(?i)\\b(OR|AND)\\s+['\"]?\\s*\\d+\\s*=\\s*\\d+" +
                    "|(?i)\\b(DROP|DELETE|INSERT|UPDATE|EXEC|EXECUTE|TRUNCATE)\\b"
    );
}