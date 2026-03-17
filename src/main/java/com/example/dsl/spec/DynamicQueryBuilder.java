package com.example.dsl.spec;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * {@link DynamicQuerySpec}을 MyBatis에서 실행 가능한 SQL 문자열로 변환한다.
 *
 * <p>MyBatis Provider 방식으로 사용한다.
 * Mapper 메서드에 {@code @SelectProvider(type = DynamicQueryBuilder.class, method = "build")}를 선언하면
 * 런타임에 이 클래스가 SQL을 생성한다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * // Mapper
 * @SelectProvider(type = DynamicQueryBuilder.class, method = "build")
 * List<Map<String, Object>> selectDynamic(DynamicQuerySpec spec);
 *
 * // 호출
 * DynamicQuerySpec spec = new DynamicQuerySpec()
 *     .mainTable("TBTR_C_MSTR", "mstr")
 *     .select("mstr", "TID")
 *     .where("mstr", "CID", "demotest0p")
 *     .limit(100);
 *
 * List<Map<String, Object>> result = mapper.selectDynamic(spec);
 * }</pre>
 */
public class DynamicQueryBuilder {

    /**
     * {@link DynamicQuerySpec}을 SQL 문자열로 변환한다.
     *
     * @param spec 쿼리 명세
     * @return 실행 가능한 SQL 문자열
     * @throws IllegalArgumentException mainTable이 설정되지 않은 경우
     */
    public String build(DynamicQuerySpec spec) {
        if (spec.mainTableName == null || spec.mainTableName.isEmpty()) {
            throw new IllegalArgumentException("mainTable이 설정되지 않았습니다.");
        }

        StringBuilder sql = new StringBuilder();

        // SELECT
        buildSelect(sql, spec);

        // FROM
        sql.append(" FROM ").append(spec.mainTableName).append(" ").append(spec.mainAlias);

        // JOIN
        buildJoin(sql, spec);

        // WHERE
        buildWhere(sql, spec);

        // ORDER BY
        buildOrderBy(sql, spec);

        // LIMIT / OFFSET
        buildLimit(sql, spec);

        return sql.toString();
    }

    private void buildSelect(StringBuilder sql, DynamicQuerySpec spec) {
        sql.append("SELECT ");
        if (spec.selectColumns.isEmpty()) {
            sql.append(spec.mainAlias).append(".*");
            return;
        }
        for (int i = 0; i < spec.selectColumns.size(); i++) {
            DynamicQuerySpec.SelectColumn sc = spec.selectColumns.get(i);
            sql.append(sc.alias).append(".").append(sc.column);
            if (sc.columnAlias != null) {
                sql.append(" AS ").append(sc.columnAlias);
            }
            if (i < spec.selectColumns.size() - 1) {
                sql.append(", ");
            }
        }
    }

    private void buildJoin(StringBuilder sql, DynamicQuerySpec spec) {
        for (DynamicQuerySpec.JoinTable jt : spec.joinTables) {
            sql.append(" ").append(jt.joinType.name()).append(" JOIN ")
               .append(jt.tableName).append(" ").append(jt.alias).append(" ON ");
            for (int i = 0; i < jt.conditions.size(); i++) {
                DynamicQuerySpec.JoinCondition jc = jt.conditions.get(i);
                if (i > 0) sql.append(" AND ");
                if (jc.fixedValue != null) {
                    sql.append(jc.leftAlias).append(".").append(jc.leftColumn)
                       .append(" = '").append(jc.fixedValue).append("'");
                } else {
                    sql.append(jc.leftAlias).append(".").append(jc.leftColumn)
                       .append(" = ")
                       .append(jc.rightAlias).append(".").append(jc.rightColumn);
                }
            }
        }
    }

    private void buildWhere(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.whereConditions.isEmpty()) return;

        sql.append(" WHERE ");
        for (int i = 0; i < spec.whereConditions.size(); i++) {
            DynamicQuerySpec.WhereCondition wc = spec.whereConditions.get(i);
            if (i > 0) sql.append(" AND ");
            String col = wc.alias + "." + wc.column;

            switch (wc.type) {
                case BETWEEN -> {
                    List<?> vals = (List<?>) wc.value;
                    sql.append(col).append(" BETWEEN #{whereConditions[").append(i).append("].value[0]}")
                       .append(" AND #{whereConditions[").append(i).append("].value[1]}");
                }
                case IN -> {
                    sql.append(col).append(" IN (");
                    List<?> inVals = (List<?>) wc.value;
                    for (int j = 0; j < inVals.size(); j++) {
                        if (j > 0) sql.append(", ");
                        sql.append("#{whereConditions[").append(i).append("].value[").append(j).append("]}");
                    }
                    sql.append(")");
                }
                default -> {
                    if ("LIKE".equals(wc.operator)) {
                        sql.append(col).append(" LIKE #{whereConditions[").append(i).append("].value}");
                    } else {
                        sql.append(col).append(" = #{whereConditions[").append(i).append("].value}");
                    }
                }
            }
        }
    }

    private void buildOrderBy(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.orderByColumns.isEmpty()) return;
        sql.append(" ORDER BY ");
        for (int i = 0; i < spec.orderByColumns.size(); i++) {
            DynamicQuerySpec.OrderByColumn obc = spec.orderByColumns.get(i);
            if (i > 0) sql.append(", ");
            sql.append(obc.alias).append(".").append(obc.column)
               .append(obc.descending ? " DESC" : " ASC");
        }
    }

    private void buildLimit(StringBuilder sql, DynamicQuerySpec spec) {
        if (spec.limitCount != null) {
            sql.append(" LIMIT ");
            if (spec.offsetCount != null) {
                sql.append(spec.offsetCount).append(", ");
            }
            sql.append(spec.limitCount);
        }
    }

    /**
     * 디버깅용 SQL 미리보기를 반환한다. 파라미터를 실제 값으로 치환한 SQL을 출력한다.
     *
     * @param spec 쿼리 명세
     * @return 파라미터가 치환된 SQL 문자열 (로그 출력 전용, 실제 실행 금지)
     */
    public String buildPreview(DynamicQuerySpec spec) {
        String sql = build(spec);
        for (DynamicQuerySpec.WhereCondition wc : spec.whereConditions) {
            if (wc.type == DynamicQuerySpec.ConditionType.NORMAL) {
                sql = sql.replaceFirst("#\\{[^}]+\\}", "'" + wc.value + "'");
            }
        }
        return sql;
    }
}
