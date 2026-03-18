package com.example.dsl.spec;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DynamicQuerySpec}을 MyBatis에서 실행 가능한 SQL 문자열로 변환한다.
 *
 * <h3>사용 패턴</h3>
 * <pre>{@code
 * // 1. spec 선언
 * DynamicQuerySpec spec = new DynamicQuerySpec()
 *     .mainTable("ORDERS", "o")
 *     .where("o", "STORE_ID", storeId)
 *     .limit(100);
 *
 * // 2. paramMap 생성 (SQL + 파라미터를 하나의 Map으로 묶음)
 * Map<String, Object> paramMap = DynamicQueryBuilder.buildParamMap(spec);
 *
 * // 3. Mapper 호출
 * List<Map<String, Object>> result = mapper.selectDynamic(paramMap);
 * }</pre>
 *
 * <h3>파라미터 키 규칙</h3>
 * <p>언더스코어를 포함하지 않는 camelCase를 사용한다.
 * WMap의 자동 변환 대상이 되지 않도록 하기 위함이다.</p>
 * <pre>
 * WHERE 파라미터  : xdyWhPrm0, xdyWhPrm1, ...
 * HAVING 파라미터 : xdyHvPrm0, xdyHvPrm1, ...
 * whereRaw 파라미터: xdyWhRawPrm1c0, xdyWhRawPrm1c1, ...
 * </pre>
 */
@Slf4j
public class DynamicQueryBuilder {

    private static final String SQL_KEY          = "__sql__";
    private static final String WHERE_PARAM_PRE  = "xdyWhPrm";
    private static final String HAVING_PARAM_PRE = "xdyHvPrm";
    private static final String RAW_PARAM_PRE    = "xdyWhRawPrm";

    // ──────────────────────────────────────────────
    // MyBatis SelectProvider 진입점
    // ──────────────────────────────────────────────

    /**
     * MyBatis SelectProvider 진입점.
     *
     * <p>paramMap 안에 {@code "__sql__"} 키로 저장된 SQL 문자열을 꺼내어 반환한다.
     * {@link #buildParamMap(DynamicQuerySpec)}이 미리 SQL을 생성해두기 때문에
     * 이 메서드는 단순 조회만 한다.</p>
     *
     * @param paramMap {@link #buildParamMap(DynamicQuerySpec)}으로 생성된 파라미터 맵
     * @return 실행할 SQL 문자열
     */
    public String buildSql(Map<String, Object> paramMap) {
        String sql = (String) paramMap.get(SQL_KEY);
        if (sql == null) {
            throw new IllegalArgumentException(
                    "paramMap에 SQL이 없습니다. DynamicQueryBuilder.buildParamMap(spec)으로 생성하세요.");
        }
        return sql;
    }

    // ──────────────────────────────────────────────
    // 외부 호출용 팩토리 메서드
    // ──────────────────────────────────────────────

    /**
     * {@link DynamicQuerySpec}으로부터 SQL과 파라미터가 담긴 맵을 생성한다.
     *
     * <p>이 메서드가 반환한 맵을 Mapper에 그대로 전달해야 한다.</p>
     *
     * <pre>{@code
     * Map<String, Object> paramMap = DynamicQueryBuilder.buildParamMap(spec);
     * List<Map<String, Object>> result = mapper.selectDynamic(paramMap);
     * }</pre>
     *
     * @param spec 쿼리 명세
     * @return SQL과 바인딩 파라미터가 담긴 맵
     * @throws IllegalArgumentException mainTable이 설정되지 않은 경우
     */
    public static Map<String, Object> buildParamMap(DynamicQuerySpec spec) {
        if (spec.mainTable == null) {
            throw new IllegalArgumentException("mainTable이 설정되지 않았습니다.");
        }

        Map<String, Object> paramMap = new HashMap<>();
        StringBuilder sql = new StringBuilder();

        buildSelect(sql, spec);
        sql.append("\nFROM ").append(spec.mainTable.tableName)
                .append(" ").append(spec.mainTable.alias);
        buildJoin(sql, spec);
        buildWhere(sql, spec, paramMap);
        buildGroupBy(sql, spec);
        buildHaving(sql, spec, paramMap);
        buildOrderBy(sql, spec);
        buildLimit(sql, spec);

        String finalSql = formatSql(sql.toString());
        log.debug("[DynamicQuery] SQL:\n{}", finalSql);

        // debug() 호출 시 로그 출력
        if (spec.debugMode) {
            log.info("[DynamicQuery DEBUG]\n{}", spec.buildPreview(true));
        }

        paramMap.put(SQL_KEY, finalSql);
        return paramMap;
    }

    // ──────────────────────────────────────────────
    // Validator용 — SQL만 반환 (파라미터 없이)
    // ──────────────────────────────────────────────

    /**
     * EXPLAIN 용도로 SQL 문자열만 반환한다.
     *
     * <p>파라미터 바인딩 없이 SQL만 필요할 때 사용한다.
     * {@link com.example.dsl.validator.DynamicQueryValidator}에서 내부적으로 사용한다.</p>
     *
     * @param spec 쿼리 명세
     * @return MyBatis #{} 바인딩 변수가 포함된 SQL
     */
    public static String buildSqlOnly(DynamicQuerySpec spec) {
        Map<String, Object> paramMap = buildParamMap(spec);
        return (String) paramMap.get(SQL_KEY);
    }

    /**
     * 디버깅용 SQL 미리보기를 반환한다 (파라미터를 실제 값으로 치환).
     *
     * @param spec 쿼리 명세
     * @return 파라미터가 치환된 SQL 문자열 (로그 전용)
     */
    public static String buildPreview(DynamicQuerySpec spec) {
        return spec.buildPreview(true);
    }

    // ──────────────────────────────────────────────
    // SQL 절 생성 (모두 static)
    // ──────────────────────────────────────────────

    private static void buildSelect(StringBuilder sql, DynamicQuerySpec spec) {
        sql.append(spec.selectDistinct ? "SELECT DISTINCT" : "SELECT");

        if (spec.selectAll || spec.selectColumns.isEmpty()) {
            sql.append(" ").append(spec.mainTable.alias).append(".*");
            return;
        }

        boolean first = true;
        for (DynamicQuerySpec.SelectColumn sc : spec.selectColumns) {
            sql.append(first ? " " : ", ");
            sql.append(sc.toSql());
            first = false;
        }
    }

    private static void buildJoin(StringBuilder sql, DynamicQuerySpec spec) {
        for (DynamicQuerySpec.JoinTable jt : spec.joinTables) {
            String joinKw = jt.joinType == DynamicQuerySpec.JoinType.INNER
                    ? "INNER JOIN" : "LEFT OUTER JOIN";
            sql.append(" ").append(joinKw).append(" ").append(jt.tableName)
                    .append(" ").append(jt.alias).append(" ON");

            boolean firstOn = true;
            for (DynamicQuerySpec.JoinCondition jc : jt.conditions) {
                sql.append(firstOn ? " " : " AND ");
                if (jc.rawCondition != null) {
                    sql.append(jc.rawCondition);
                } else if (jc.fixedValue != null) {
                    sql.append(jc.leftAlias).append(".").append(jc.leftColumn)
                            .append(" ").append(jc.operator != null ? jc.operator : "=")
                            .append(" '").append(jc.fixedValue).append("'");
                } else {
                    sql.append(jc.leftAlias).append(".").append(jc.leftColumn)
                            .append(" ").append(jc.operator != null ? jc.operator : "=")
                            .append(" ").append(jc.rightAlias).append(".").append(jc.rightColumn);
                }
                firstOn = false;
            }
        }
    }

    private static void buildWhere(StringBuilder sql, DynamicQuerySpec spec,
                                   Map<String, Object> paramMap) {
        List<String> parts = new ArrayList<>();
        int paramIdx = 0;
        int rawCallCount = 1;

        for (DynamicQuerySpec.WhereCondition wc : spec.whereConditions) {

            // whereRaw — {} 플레이스홀더를 파라미터명으로 치환
            if (wc.rawCondition != null) {
                String template = wc.rawCondition;
                if (wc.rawParams != null) {
                    for (int i = 0; i < wc.rawParams.length; i++) {
                        String paramName = RAW_PARAM_PRE + rawCallCount + "c" + i;
                        template = template.replaceFirst("\\{\\}", "#{" + paramName + "}");
                        paramMap.put(paramName, wc.rawParams[i]);
                    }
                }
                rawCallCount++;
                parts.add(template);
                continue;
            }

            // IS NULL / IS NOT NULL — 파라미터 없음
            if (wc.isNullCheck) {
                parts.add(wc.alias + "." + wc.column + " " + wc.operator);
                continue;
            }

            // null-safe: value가 없으면 조건 생략
            if (wc.value == null && !wc.isBetween && !wc.isIn) {
                continue;
            }

            String col = wc.alias + "." + wc.column;

            if (wc.isBetween) {
                Object[] arr = (Object[]) wc.value;
                String p1 = WHERE_PARAM_PRE + paramIdx++;
                String p2 = WHERE_PARAM_PRE + paramIdx++;
                paramMap.put(p1, arr[0]);
                paramMap.put(p2, arr[1]);
                parts.add(col + " BETWEEN #{" + p1 + "} AND #{" + p2 + "}");

            } else if (wc.isIn) {
                List<?> list = (List<?>) wc.value;
                StringBuilder inSql = new StringBuilder(col + " " + wc.operator + " (");
                for (int i = 0; i < list.size(); i++) {
                    String p = WHERE_PARAM_PRE + paramIdx++;
                    paramMap.put(p, list.get(i));
                    inSql.append(i > 0 ? ", " : "").append("#{").append(p).append("}");
                }
                inSql.append(")");
                parts.add(inSql.toString());

            } else {
                String p = WHERE_PARAM_PRE + paramIdx++;
                paramMap.put(p, wc.value);
                parts.add(col + " " + wc.operator + " #{" + p + "}");
            }
        }

        if (!parts.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", parts));
        }
    }

    private static void buildGroupBy(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.groupByColumns.isEmpty()) return;
        sql.append(" GROUP BY ");
        boolean first = true;
        for (DynamicQuerySpec.GroupByColumn gc : spec.groupByColumns) {
            sql.append(first ? "" : ", ").append(gc.alias).append(".").append(gc.column);
            first = false;
        }
    }

    private static void buildHaving(StringBuilder sql, DynamicQuerySpec spec,
                                    Map<String, Object> paramMap) {
        if (spec.havingConditions.isEmpty()) return;
        List<String> parts = new ArrayList<>();
        int paramIdx = 0;

        for (DynamicQuerySpec.HavingCondition hc : spec.havingConditions) {
            if (hc.rawCondition != null) {
                parts.add(hc.rawCondition);
            } else if (hc.expression != null && hc.value != null) {
                String p = HAVING_PARAM_PRE + paramIdx++;
                paramMap.put(p, hc.value);
                parts.add(hc.expression + " " + hc.operator + " #{" + p + "}");
            }
        }

        if (!parts.isEmpty()) {
            sql.append(" HAVING ").append(String.join(" AND ", parts));
        }
    }

    private static void buildOrderBy(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.orderByColumns.isEmpty()) return;
        sql.append(" ORDER BY ");
        boolean first = true;
        for (DynamicQuerySpec.OrderByColumn oc : spec.orderByColumns) {
            sql.append(first ? "" : ", ")
                    .append(oc.alias).append(".").append(oc.column)
                    .append(oc.descending ? " DESC" : " ASC");
            first = false;
        }
    }

    private static void buildLimit(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.limitCount != null) sql.append(" LIMIT ").append(spec.limitCount);
        if (spec.offsetCount != null) sql.append(" OFFSET ").append(spec.offsetCount);
    }

    private static String formatSql(String sql) {
        return sql
                .replaceAll("\\s+FROM ", "\nFROM ")
                .replaceAll("\\s+(INNER|LEFT OUTER) JOIN ", "\n$1 JOIN ")
                .replaceAll("\\s+WHERE ", "\nWHERE ")
                .replaceAll("\\s+GROUP BY ", "\nGROUP BY ")
                .replaceAll("\\s+HAVING ", "\nHAVING ")
                .replaceAll("\\s+ORDER BY ", "\nORDER BY ")
                .replaceAll("\\s+LIMIT ", "\nLIMIT ")
                .replaceAll("\\s+OFFSET ", "\nOFFSET ");
    }
}