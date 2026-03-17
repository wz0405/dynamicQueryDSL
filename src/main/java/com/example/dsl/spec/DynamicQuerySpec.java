package com.example.dsl.spec;

import java.util.*;

/**
 * SQL 쿼리를 Java 코드로 선언적으로 구성하는 DSL 클래스.
 *
 * <p>MyBatis XML 없이 Java 메서드 체이닝으로 SELECT 쿼리를 구성한다.
 * 주 용도는 조회 조건이 동적으로 변하는 필터 쿼리다.</p>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * DynamicQuerySpec spec = new DynamicQuerySpec()
 *     .mainTable("TBTR_C_MSTR", "mstr")
 *     .innerJoin("TBTR_CARD", "card")
 *         .on("mstr", "TID", "card", "TID")
 *         .on("mstr", "TR_DT", "card", "TR_DT")
 *     .select("mstr", "TID")
 *     .select("mstr", "TR_DT")
 *     .select("card", "APP_NO")
 *     .where("mstr", "CID", "demotest0p")
 *     .whereIf("card", "APP_NO", appNo)
 *     .whereBetween("mstr", "TR_DT", "20260101", "20260131")
 *     .orderBy("mstr", "TR_DT", true)
 *     .limit(100);
 * }</pre>
 */
public class DynamicQuerySpec {

    public String mainTableName;
    public String mainAlias;

    public final List<JoinTable> joinTables = new ArrayList<>();
    public final List<WhereCondition> whereConditions = new ArrayList<>();
    public final List<SelectColumn> selectColumns = new ArrayList<>();
    public final List<OrderByColumn> orderByColumns = new ArrayList<>();

    public Integer limitCount;
    public Integer offsetCount;
    public boolean selectAll = false;

    // ──────────────────────────────────────────────
    // FROM
    // ──────────────────────────────────────────────

    /**
     * 메인 테이블과 별칭을 설정한다.
     *
     * @param tableName 테이블명 (예: "TBTR_C_MSTR")
     * @param alias     SQL 별칭 (예: "mstr")
     * @return this
     */
    public DynamicQuerySpec mainTable(String tableName, String alias) {
        this.mainTableName = tableName;
        this.mainAlias = alias;
        return this;
    }

    // ──────────────────────────────────────────────
    // JOIN
    // ──────────────────────────────────────────────

    /**
     * INNER JOIN 테이블을 추가한다.
     *
     * @param tableName 조인할 테이블명
     * @param alias     별칭
     * @return this
     */
    public DynamicQuerySpec innerJoin(String tableName, String alias) {
        joinTables.add(new JoinTable(tableName, alias, JoinType.INNER));
        return this;
    }

    /**
     * LEFT JOIN 테이블을 추가한다.
     *
     * @param tableName 조인할 테이블명
     * @param alias     별칭
     * @return this
     */
    public DynamicQuerySpec leftJoin(String tableName, String alias) {
        joinTables.add(new JoinTable(tableName, alias, JoinType.LEFT));
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN 테이블에 컬럼 = 컬럼 조건을 추가한다.
     *
     * @param leftAlias   왼쪽 테이블 별칭
     * @param leftColumn  왼쪽 컬럼명
     * @param rightAlias  오른쪽 테이블 별칭
     * @param rightColumn 오른쪽 컬럼명
     * @return this
     */
    public DynamicQuerySpec on(String leftAlias, String leftColumn, String rightAlias, String rightColumn) {
        if (!joinTables.isEmpty()) {
            joinTables.get(joinTables.size() - 1)
                    .conditions.add(new JoinCondition(leftAlias, leftColumn, rightAlias, rightColumn, null));
        }
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN 테이블에 컬럼 = 고정값 조건을 추가한다.
     *
     * @param alias      테이블 별칭
     * @param column     컬럼명
     * @param fixedValue 고정 값
     * @return this
     */
    public DynamicQuerySpec onValue(String alias, String column, String fixedValue) {
        if (!joinTables.isEmpty()) {
            joinTables.get(joinTables.size() - 1)
                    .conditions.add(new JoinCondition(alias, column, null, null, fixedValue));
        }
        return this;
    }

    // ──────────────────────────────────────────────
    // SELECT
    // ──────────────────────────────────────────────

    /**
     * SELECT 컬럼을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @return this
     */
    public DynamicQuerySpec select(String alias, String column) {
        selectColumns.add(new SelectColumn(alias, column, null));
        return this;
    }

    /**
     * SELECT 컬럼에 별칭(AS)을 지정한다.
     *
     * @param alias       테이블 별칭
     * @param column      컬럼명
     * @param columnAlias AS 별칭
     * @return this
     */
    public DynamicQuerySpec selectAs(String alias, String column, String columnAlias) {
        selectColumns.add(new SelectColumn(alias, column, columnAlias));
        return this;
    }

    // ──────────────────────────────────────────────
    // WHERE
    // ──────────────────────────────────────────────

    /**
     * WHERE 조건을 추가한다. value가 null이면 조건이 제외된다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec where(String alias, String column, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            whereConditions.add(new WhereCondition(alias, column, "=", value, ConditionType.NORMAL));
        }
        return this;
    }

    /**
     * value가 존재할 때만 WHERE 조건을 추가한다 (null-safe).
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값 (null이면 조건 제외)
     * @return this
     */
    public DynamicQuerySpec whereIf(String alias, String column, Object value) {
        if (value != null && !value.toString().isEmpty()) {
            whereConditions.add(new WhereCondition(alias, column, "=", value, ConditionType.NORMAL));
        }
        return this;
    }

    /**
     * BETWEEN 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param from   시작 값
     * @param to     종료 값
     * @return this
     */
    public DynamicQuerySpec whereBetween(String alias, String column, Object from, Object to) {
        whereConditions.add(new WhereCondition(alias, column, "BETWEEN", Arrays.asList(from, to), ConditionType.BETWEEN));
        return this;
    }

    /**
     * IN 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param values IN 절 값 목록
     * @return this
     */
    public DynamicQuerySpec whereIn(String alias, String column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            whereConditions.add(new WhereCondition(alias, column, "IN", values, ConditionType.IN));
        }
        return this;
    }

    /**
     * LIKE 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  검색 값 (% 포함)
     * @return this
     */
    public DynamicQuerySpec whereLike(String alias, String column, String value) {
        if (value != null && !value.isEmpty()) {
            whereConditions.add(new WhereCondition(alias, column, "LIKE", value, ConditionType.NORMAL));
        }
        return this;
    }

    // ──────────────────────────────────────────────
    // ORDER BY / LIMIT / OFFSET
    // ──────────────────────────────────────────────

    /**
     * ORDER BY 조건을 추가한다.
     *
     * @param alias      테이블 별칭
     * @param column     컬럼명
     * @param descending true면 DESC, false면 ASC
     * @return this
     */
    public DynamicQuerySpec orderBy(String alias, String column, boolean descending) {
        orderByColumns.add(new OrderByColumn(alias, column, descending));
        return this;
    }

    /**
     * LIMIT 절을 설정한다.
     *
     * @param count 최대 조회 건수
     * @return this
     */
    public DynamicQuerySpec limit(int count) {
        this.limitCount = count;
        return this;
    }

    /**
     * OFFSET 절을 설정한다.
     *
     * @param offset 시작 오프셋
     * @return this
     */
    public DynamicQuerySpec offset(int offset) {
        this.offsetCount = offset;
        return this;
    }

    // ──────────────────────────────────────────────
    // 내부 모델 클래스
    // ──────────────────────────────────────────────

    public enum JoinType { INNER, LEFT }

    public enum ConditionType { NORMAL, BETWEEN, IN }

    public static class JoinTable {
        public String tableName;
        public String alias;
        public JoinType joinType;
        public List<JoinCondition> conditions = new ArrayList<>();

        public JoinTable(String tableName, String alias, JoinType joinType) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
        }
    }

    public static class JoinCondition {
        public String leftAlias;
        public String leftColumn;
        public String rightAlias;
        public String rightColumn;
        public String fixedValue;

        public JoinCondition(String leftAlias, String leftColumn,
                             String rightAlias, String rightColumn, String fixedValue) {
            this.leftAlias = leftAlias;
            this.leftColumn = leftColumn;
            this.rightAlias = rightAlias;
            this.rightColumn = rightColumn;
            this.fixedValue = fixedValue;
        }
    }

    public static class WhereCondition {
        public String alias;
        public String column;
        public String operator;
        public Object value;
        public ConditionType type;

        public WhereCondition(String alias, String column, String operator,
                              Object value, ConditionType type) {
            this.alias = alias;
            this.column = column;
            this.operator = operator;
            this.value = value;
            this.type = type;
        }
    }

    public static class SelectColumn {
        public String alias;
        public String column;
        public String columnAlias;

        public SelectColumn(String alias, String column, String columnAlias) {
            this.alias = alias;
            this.column = column;
            this.columnAlias = columnAlias;
        }
    }

    public static class OrderByColumn {
        public String alias;
        public String column;
        public boolean descending;

        public OrderByColumn(String alias, String column, boolean descending) {
            this.alias = alias;
            this.column = column;
            this.descending = descending;
        }
    }
}
