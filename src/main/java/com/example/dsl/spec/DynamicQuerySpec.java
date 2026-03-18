package com.example.dsl.spec;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 동적 쿼리 명세를 정의하는 DSL 클래스.
 *
 * <p>Fluent API 패턴으로 SELECT 쿼리를 선언적으로 구성한다.
 * 내부적으로 WMap 기반 파라미터를 생성하여 {@link DynamicQueryBuilder}에 전달한다.</p>
 *
 * <h3>지원 기능</h3>
 * <ul>
 *   <li>SELECT / SELECT AS / COUNT / SUM / AVG / MIN / MAX / DISTINCT</li>
 *   <li>CASE WHEN THEN ELSE END</li>
 *   <li>selectRaw — 원시 SQL 표현식</li>
 *   <li>INNER JOIN / LEFT OUTER JOIN</li>
 *   <li>ON 컬럼=컬럼 / ON 컬럼 연산자 컬럼 / onValue / onRaw</li>
 *   <li>WHERE = / != / &gt; / &gt;= / &lt; / &lt;= / BETWEEN / IN / NOT IN / LIKE / IS NULL / IS NOT NULL</li>
 *   <li>whereIf — null 자동 제외 (null-safe)</li>
 *   <li>whereRaw — {} 플레이스홀더 기반 원시 조건</li>
 *   <li>GROUP BY / HAVING / havingRaw</li>
 *   <li>ORDER BY ASC/DESC</li>
 *   <li>LIMIT / OFFSET</li>
 *   <li>debug() — 파라미터 치환된 SQL 즉시 출력</li>
 * </ul>
 *
 * <h3>기본 사용법</h3>
 * <pre>{@code
 * new DynamicQuerySpec()
 *     .mainTable("ORDERS", "o")
 *     .innerJoin("PAYMENTS", "p")
 *         .on("o", "ORDER_ID", "p", "ORDER_ID")
 *     .select("o", "ORDER_ID")
 *     .select("p", "APPROVAL_NO")
 *     .where("o", "STORE_ID", storeId)
 *     .whereIf("p", "APPROVAL_NO", approvalNo)
 *     .whereBetween("o", "ORDER_DT", frDt, toDt)
 *     .orderBy("o", "ORDER_DT", true)
 *     .limit(100)
 *     .debug();
 * }</pre>
 *
 * <h3>고급 사용법</h3>
 * <pre>{@code
 * new DynamicQuerySpec()
 *     .mainTable("ORDERS", "o")
 *     .leftJoin("ORDER_HISTORY", "h")
 *         .on("o", "ORDER_ID", "h", "ORDER_ID")
 *         .onValue("h", "STATUS", "0")
 *         .onRaw("DATE_FORMAT(h.SETTLE_DT, '%Y%m') = DATE_FORMAT(o.ORDER_DT, '%Y%m')")
 *     .select("o", "STORE_ID")
 *     .selectCount("o", "ORDER_ID", "orderCnt")
 *     .selectSum("o", "AMOUNT", "totalAmount")
 *     .selectCaseElse("statusLabel", "알 수 없음",
 *         CaseWhen.of("o", "STATUS", "0", "정상"),
 *         CaseWhen.of("o", "STATUS", "1", "취소"))
 *     .where("o", "STORE_ID", storeId)
 *     .whereGte("o", "AMOUNT", 10000)
 *     .whereNotIn("o", "STATUS", List.of("9"))
 *     .whereRaw("DATE_FORMAT(o.ORDER_DT, '%Y%m') = {}", yearMonth)
 *     .groupBy("o", "STORE_ID")
 *     .having("totalAmount", ">", 100000)
 *     .orderBy("o", "ORDER_DT", true)
 *     .limit(100)
 *     .offset(0);
 * }</pre>
 */
@Slf4j
public class DynamicQuerySpec {

    // ──────────────────────────────────────────────
    // 내부 상태
    // ──────────────────────────────────────────────

    public MainTable mainTable;
    public JoinTable currentJoin;  // 마지막으로 추가된 JOIN (on/onValue/onRaw 연결용)

    public final List<JoinTable> joinTables = new ArrayList<>();
    public final List<SelectColumn> selectColumns = new ArrayList<>();
    public final List<WhereCondition> whereConditions = new ArrayList<>();
    public final List<HavingCondition> havingConditions = new ArrayList<>();
    public final List<GroupByColumn> groupByColumns = new ArrayList<>();
    public final List<OrderByColumn> orderByColumns = new ArrayList<>();

    public boolean selectAll = true;
    /** Builder가 build() 시점에 채우는 임시 파라미터 저장소 (MyBatis 바인딩용) */
    public java.util.Map<String, Object> paramStore = new java.util.HashMap<>();
    public boolean selectDistinct = false;
    public boolean debugMode = false;
    public Integer limitCount;
    public Integer offsetCount;

    // ──────────────────────────────────────────────
    // FROM
    // ──────────────────────────────────────────────

    /**
     * 메인 테이블과 별칭을 설정한다.
     *
     * @param tableName 테이블 물리명 (예: "ORDERS")
     * @param alias     SQL 별칭 (예: "o")
     * @return this
     */
    public DynamicQuerySpec mainTable(String tableName, String alias) {
        this.mainTable = new MainTable(tableName, alias);
        return this;
    }

    // ──────────────────────────────────────────────
    // JOIN
    // ──────────────────────────────────────────────

    /**
     * INNER JOIN 테이블을 추가한다.
     *
     * @param tableName 조인할 테이블 물리명
     * @param alias     별칭
     * @return this
     */
    public DynamicQuerySpec innerJoin(String tableName, String alias) {
        currentJoin = new JoinTable(tableName, alias, JoinType.INNER);
        joinTables.add(currentJoin);
        return this;
    }

    /**
     * LEFT OUTER JOIN 테이블을 추가한다.
     *
     * @param tableName 조인할 테이블 물리명
     * @param alias     별칭
     * @return this
     */
    public DynamicQuerySpec leftJoin(String tableName, String alias) {
        currentJoin = new JoinTable(tableName, alias, JoinType.LEFT_OUTER);
        joinTables.add(currentJoin);
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN에 컬럼 = 컬럼 조건을 추가한다.
     *
     * <p>예: {@code .on("o", "ORDER_ID", "p", "ORDER_ID")}
     * → {@code ON o.ORDER_ID = p.ORDER_ID}</p>
     *
     * @param leftAlias   왼쪽 테이블 별칭
     * @param leftColumn  왼쪽 컬럼명
     * @param rightAlias  오른쪽 테이블 별칭
     * @param rightColumn 오른쪽 컬럼명
     * @return this
     */
    public DynamicQuerySpec on(String leftAlias, String leftColumn,
                               String rightAlias, String rightColumn) {
        requireJoin();
        currentJoin.conditions.add(new JoinCondition(leftAlias, leftColumn, "=",
                rightAlias, rightColumn, null, null));
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN에 컬럼 연산자 컬럼 조건을 추가한다.
     *
     * <p>예: {@code .on("o", "ORDER_DT", ">=", "h", "SETTLE_DT")}
     * → {@code AND o.ORDER_DT >= h.SETTLE_DT}</p>
     *
     * @param leftAlias   왼쪽 테이블 별칭
     * @param leftColumn  왼쪽 컬럼명
     * @param operator    비교 연산자 (=, !=, &gt;, &gt;=, &lt;, &lt;=)
     * @param rightAlias  오른쪽 테이블 별칭
     * @param rightColumn 오른쪽 컬럼명
     * @return this
     */
    public DynamicQuerySpec on(String leftAlias, String leftColumn, String operator,
                               String rightAlias, String rightColumn) {
        requireJoin();
        validateOperator(operator);
        currentJoin.conditions.add(new JoinCondition(leftAlias, leftColumn, operator,
                rightAlias, rightColumn, null, null));
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN에 컬럼 = 고정값 조건을 추가한다.
     *
     * <p>예: {@code .onValue("p", "PAY_TYPE", "CARD")}
     * → {@code AND p.PAY_TYPE = 'CARD'}</p>
     *
     * @param alias      테이블 별칭
     * @param column     컬럼명
     * @param fixedValue 고정값 (SQL Injection 방지를 위해 외부 입력 직접 전달 금지)
     * @return this
     */
    public DynamicQuerySpec onValue(String alias, String column, String fixedValue) {
        requireJoin();
        validateFixedValue(fixedValue);
        currentJoin.conditions.add(new JoinCondition(alias, column, "=",
                null, null, fixedValue, null));
        return this;
    }

    /**
     * 마지막으로 추가된 JOIN에 원시 SQL 조건을 추가한다.
     *
     * <p>예: {@code .onRaw("DATE_FORMAT(h.SETTLE_DT, '%Y%m') = DATE_FORMAT(o.ORDER_DT, '%Y%m')")}
     *
     * <p><b>주의</b>: 외부 입력을 직접 포함하면 SQL Injection 위험이 있다.
     * 외부값이 필요하면 {@code onValue()}를 사용한다.</p>
     *
     * @param rawCondition 원시 SQL 조건 문자열
     * @return this
     */
    public DynamicQuerySpec onRaw(String rawCondition) {
        requireJoin();
        validateRaw(rawCondition);
        currentJoin.conditions.add(new JoinCondition(null, null, null,
                null, null, null, rawCondition));
        return this;
    }

    // ──────────────────────────────────────────────
    // SELECT
    // ──────────────────────────────────────────────

    /**
     * SELECT 컬럼을 추가한다. 한 번이라도 호출하면 SELECT * 모드가 해제된다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @return this
     */
    public DynamicQuerySpec select(String alias, String column) {
        selectAll = false;
        selectColumns.add(SelectColumn.plain(alias, column, null));
        return this;
    }

    /**
     * SELECT 컬럼에 AS 별칭을 지정한다.
     *
     * <p>예: {@code .selectAs("o", "AMOUNT", "orderAmount")}
     * → {@code o.AMOUNT AS orderAmount}</p>
     *
     * @param alias       테이블 별칭
     * @param column      컬럼명
     * @param columnAlias AS 별칭
     * @return this
     */
    public DynamicQuerySpec selectAs(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.plain(alias, column, columnAlias));
        return this;
    }

    /**
     * COUNT 집계함수를 추가한다.
     *
     * <p>예: {@code .selectCount("o", "ORDER_ID", "orderCnt")}
     * → {@code COUNT(o.ORDER_ID) AS orderCnt}</p>
     *
     * @param alias       테이블 별칭
     * @param column      컬럼명
     * @param columnAlias AS 별칭
     * @return this
     */
    public DynamicQuerySpec selectCount(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.aggregate("COUNT", alias, column, columnAlias));
        return this;
    }

    /**
     * SUM 집계함수를 추가한다.
     *
     * <p>예: {@code .selectSum("o", "AMOUNT", "totalAmount")}
     * → {@code SUM(o.AMOUNT) AS totalAmount}</p>
     */
    public DynamicQuerySpec selectSum(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.aggregate("SUM", alias, column, columnAlias));
        return this;
    }

    /**
     * AVG 집계함수를 추가한다.
     *
     * <p>예: {@code .selectAvg("o", "AMOUNT", "avgAmount")}
     * → {@code AVG(o.AMOUNT) AS avgAmount}</p>
     */
    public DynamicQuerySpec selectAvg(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.aggregate("AVG", alias, column, columnAlias));
        return this;
    }

    /**
     * MIN 집계함수를 추가한다.
     *
     * <p>예: {@code .selectMin("o", "AMOUNT", "minAmount")}
     * → {@code MIN(o.AMOUNT) AS minAmount}</p>
     */
    public DynamicQuerySpec selectMin(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.aggregate("MIN", alias, column, columnAlias));
        return this;
    }

    /**
     * MAX 집계함수를 추가한다.
     *
     * <p>예: {@code .selectMax("o", "AMOUNT", "maxAmount")}
     * → {@code MAX(o.AMOUNT) AS maxAmount}</p>
     */
    public DynamicQuerySpec selectMax(String alias, String column, String columnAlias) {
        selectAll = false;
        selectColumns.add(SelectColumn.aggregate("MAX", alias, column, columnAlias));
        return this;
    }

    /**
     * DISTINCT 모드를 활성화한다.
     *
     * <p>SELECT DISTINCT 형태로 생성된다.</p>
     *
     * @return this
     */
    public DynamicQuerySpec distinct() {
        this.selectDistinct = true;
        return this;
    }

    /**
     * 원시 SQL SELECT 표현식을 추가한다.
     *
     * <p>예: {@code .selectRaw("DATE_FORMAT(o.ORDER_DT, '%Y-%m') AS orderYm")}
     *
     * <p><b>주의</b>: 외부 입력을 직접 포함하면 SQL Injection 위험이 있다.
     * 고정 표현식에만 사용한다.</p>
     *
     * @param rawExpression 원시 SQL 표현식
     * @return this
     */
    public DynamicQuerySpec selectRaw(String rawExpression) {
        selectAll = false;
        validateRaw(rawExpression);
        selectColumns.add(SelectColumn.raw(rawExpression));
        return this;
    }

    /**
     * CASE WHEN THEN ELSE END 표현식을 SELECT에 추가한다 (ELSE = 빈 문자열).
     *
     * <pre>{@code
     * .selectCase("statusLabel",
     *     CaseWhen.of("o", "STATUS", "0", "정상"),
     *     CaseWhen.of("o", "STATUS", "1", "취소"),
     *     CaseWhen.of("o", "STATUS", "2", "부분취소"))
     * // → CASE WHEN o.STATUS = '0' THEN '정상'
     * //        WHEN o.STATUS = '1' THEN '취소'
     * //        WHEN o.STATUS = '2' THEN '부분취소'
     * //   ELSE '' END AS statusLabel
     * }</pre>
     *
     * @param columnAlias AS 별칭
     * @param cases       WHEN 조건 목록
     * @return this
     */
    public DynamicQuerySpec selectCase(String columnAlias, CaseWhen... cases) {
        selectAll = false;
        selectColumns.add(SelectColumn.caseExpr(columnAlias, "", Arrays.asList(cases)));
        return this;
    }

    /**
     * CASE WHEN THEN ELSE END 표현식에 ELSE 기본값을 지정한다.
     *
     * <pre>{@code
     * .selectCaseElse("statusLabel", "알 수 없음",
     *     CaseWhen.of("o", "STATUS", "0", "정상"),
     *     CaseWhen.of("o", "STATUS", "1", "취소"))
     * // → CASE ... ELSE '알 수 없음' END AS statusLabel
     * }</pre>
     *
     * @param columnAlias AS 별칭
     * @param elseValue   ELSE 절 값
     * @param cases       WHEN 조건 목록
     * @return this
     */
    public DynamicQuerySpec selectCaseElse(String columnAlias, String elseValue, CaseWhen... cases) {
        selectAll = false;
        selectColumns.add(SelectColumn.caseExpr(columnAlias, elseValue, Arrays.asList(cases)));
        return this;
    }

    // ──────────────────────────────────────────────
    // WHERE
    // ──────────────────────────────────────────────

    /**
     * WHERE = 조건을 추가한다. value가 null 또는 빈 문자열이면 제외된다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec where(String alias, String column, Object value) {
        if (isNotEmpty(value)) {
            whereConditions.add(WhereCondition.eq(alias, column, value));
        }
        return this;
    }

    /**
     * WHERE = 조건을 추가한다. value가 null이면 조건을 건너뛴다 (null-safe).
     *
     * <p>{@link #where}와 동일하게 동작한다. 의도를 명확히 표현할 때 사용한다.</p>
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값 (null이면 조건 제외)
     * @return this
     */
    public DynamicQuerySpec whereIf(String alias, String column, Object value) {
        if (isNotEmpty(value)) {
            whereConditions.add(WhereCondition.eq(alias, column, value));
        }
        return this;
    }

    /**
     * WHERE != 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec whereNot(String alias, String column, Object value) {
        if (isNotEmpty(value)) {
            whereConditions.add(WhereCondition.op(alias, column, "!=", value));
        }
        return this;
    }

    /**
     * WHERE &gt; 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec whereGt(String alias, String column, Object value) {
        if (value != null) whereConditions.add(WhereCondition.op(alias, column, ">", value));
        return this;
    }

    /**
     * WHERE &gt;= 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec whereGte(String alias, String column, Object value) {
        if (value != null) whereConditions.add(WhereCondition.op(alias, column, ">=", value));
        return this;
    }

    /**
     * WHERE &lt; 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec whereLt(String alias, String column, Object value) {
        if (value != null) whereConditions.add(WhereCondition.op(alias, column, "<", value));
        return this;
    }

    /**
     * WHERE &lt;= 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  조건 값
     * @return this
     */
    public DynamicQuerySpec whereLte(String alias, String column, Object value) {
        if (value != null) whereConditions.add(WhereCondition.op(alias, column, "<=", value));
        return this;
    }

    /**
     * WHERE BETWEEN 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param from   시작 값
     * @param to     종료 값
     * @return this
     */
    public DynamicQuerySpec whereBetween(String alias, String column, Object from, Object to) {
        whereConditions.add(WhereCondition.between(alias, column, from, to));
        return this;
    }

    /**
     * WHERE IN 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param values IN 절 값 목록
     * @return this
     */
    public DynamicQuerySpec whereIn(String alias, String column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            whereConditions.add(WhereCondition.in(alias, column, values));
        }
        return this;
    }

    /**
     * WHERE NOT IN 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param values NOT IN 절 값 목록
     * @return this
     */
    public DynamicQuerySpec whereNotIn(String alias, String column, List<?> values) {
        if (values != null && !values.isEmpty()) {
            whereConditions.add(WhereCondition.notIn(alias, column, values));
        }
        return this;
    }

    /**
     * WHERE LIKE 조건을 추가한다.
     *
     * <p>% 와일드카드는 직접 포함해서 전달한다. 예: {@code "%Alice%"}, {@code "Alice%"}</p>
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @param value  검색 값 (% 포함)
     * @return this
     */
    public DynamicQuerySpec whereLike(String alias, String column, String value) {
        if (isNotEmpty(value)) {
            whereConditions.add(WhereCondition.like(alias, column, value));
        }
        return this;
    }

    /**
     * WHERE IS NULL 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @return this
     */
    public DynamicQuerySpec whereNull(String alias, String column) {
        whereConditions.add(WhereCondition.nullCheck(alias, column, "IS NULL"));
        return this;
    }

    /**
     * WHERE IS NOT NULL 조건을 추가한다.
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @return this
     */
    public DynamicQuerySpec whereNotNull(String alias, String column) {
        whereConditions.add(WhereCondition.nullCheck(alias, column, "IS NOT NULL"));
        return this;
    }

    /**
     * 원시 SQL WHERE 조건을 추가한다. {@code {}} 플레이스홀더로 파라미터를 바인딩한다.
     *
     * <p>예시:</p>
     * <pre>{@code
     * .whereRaw("DATE_FORMAT(o.ORDER_DT, '%Y%m') = {}", yearMonth)
     * .whereRaw("o.AMOUNT BETWEEN {} AND {}", minAmount, maxAmount)
     * .whereRaw("o.STATUS IN ('0', '1')")  // 파라미터 없는 고정 조건
     * }</pre>
     *
     * <p><b>주의</b>: 외부 입력을 직접 문자열에 포함하면 SQL Injection 위험이 있다.
     * 외부값은 반드시 {@code {}} 플레이스홀더를 통해 전달한다.</p>
     *
     * @param template 조건 템플릿 ({} 를 파라미터 자리로 사용)
     * @param params   플레이스홀더에 바인딩될 값들
     * @return this
     */
    public DynamicQuerySpec whereRaw(String template, Object... params) {
        validateRaw(template);
        whereConditions.add(WhereCondition.raw(template, params));
        return this;
    }

    // ──────────────────────────────────────────────
    // GROUP BY
    // ──────────────────────────────────────────────

    /**
     * GROUP BY 컬럼을 추가한다.
     *
     * <p>예: {@code .groupBy("o", "STORE_ID").groupBy("o", "ORDER_DT")}</p>
     *
     * @param alias  테이블 별칭
     * @param column 컬럼명
     * @return this
     */
    public DynamicQuerySpec groupBy(String alias, String column) {
        groupByColumns.add(new GroupByColumn(alias, column));
        return this;
    }

    // ──────────────────────────────────────────────
    // HAVING
    // ──────────────────────────────────────────────

    /**
     * HAVING 조건을 추가한다. GROUP BY와 함께 사용한다.
     *
     * <p>집계함수 결과 별칭 또는 표현식을 직접 지정한다.</p>
     *
     * <p>예: {@code .having("totalAmount", ">", 100000)}
     * → {@code HAVING totalAmount > 100000}</p>
     *
     * @param expression 집계 표현식 또는 AS 별칭 (예: {@code "totalAmount"}, {@code "COUNT(*)"})
     * @param operator   비교 연산자 ({@code ">"}, {@code ">="}, {@code "<"}, {@code "="} 등)
     * @param value      비교 값
     * @return this
     */
    public DynamicQuerySpec having(String expression, String operator, Object value) {
        havingConditions.add(HavingCondition.expr(expression, operator, value));
        return this;
    }

    /**
     * 원시 SQL HAVING 조건을 추가한다.
     *
     * <p>예: {@code .havingRaw("COUNT(*) > 5")}</p>
     *
     * <p><b>주의</b>: 외부 입력을 직접 포함하면 SQL Injection 위험이 있다.</p>
     *
     * @param rawCondition 원시 HAVING 조건
     * @return this
     */
    public DynamicQuerySpec havingRaw(String rawCondition) {
        validateRaw(rawCondition);
        havingConditions.add(HavingCondition.raw(rawCondition));
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
    // DEBUG
    // ──────────────────────────────────────────────

    /**
     * 현재 spec을 기반으로 파라미터가 치환된 SQL을 INFO 로그로 즉시 출력한다.
     *
     * <p>메서드 체이닝 중간에 삽입하여 쿼리를 확인할 수 있다.</p>
     * <p>운영환경에서는 사용하지 않는다.</p>
     *
     * <pre>{@code
     * new DynamicQuerySpec()
     *     .mainTable("ORDERS", "o")
     *     .where("o", "STORE_ID", storeId)
     *     .limit(100)
     *     .debug();  // ← 이 시점의 SQL을 로그로 출력
     * }</pre>
     *
     * @return this
     */
    public DynamicQuerySpec debug() {
        log.info("[DynamicQuerySpec DEBUG]\n{}", buildPreview(true));
        return this;
    }

    /**
     * 파라미터가 치환된 SQL 미리보기 문자열을 반환한다.
     *
     * @param inlineBind true면 파라미터를 실제값으로 치환, false면 ? + Bindings 섹션
     * @return SQL 미리보기 문자열
     */
    String buildPreview(boolean inlineBind) {
        StringBuilder sb = new StringBuilder();
        List<Object> bindings = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // SELECT
        sb.append(selectDistinct ? "SELECT DISTINCT" : "SELECT");
        if (selectAll || selectColumns.isEmpty()) {
            sb.append("\n    ").append(mainTable != null ? mainTable.alias + ".*" : "*");
        } else {
            boolean first = true;
            for (SelectColumn sc : selectColumns) {
                sb.append(first ? "\n    " : ",\n    ");
                sb.append(sc.toSql());
                first = false;
            }
        }

        // FROM
        if (mainTable != null) {
            sb.append("\nFROM ").append(mainTable.tableName).append(" ").append(mainTable.alias);
        }

        // JOIN
        for (JoinTable jt : joinTables) {
            String joinKw = jt.joinType == JoinType.INNER ? "INNER JOIN" : "LEFT OUTER JOIN";
            sb.append("\n").append(joinKw).append(" ").append(jt.tableName)
                    .append(" ").append(jt.alias).append(" ON");
            boolean firstOn = true;
            for (JoinCondition jc : jt.conditions) {
                sb.append(firstOn ? " " : " AND ");
                if (jc.rawCondition != null) {
                    sb.append(jc.rawCondition);
                } else if (jc.fixedValue != null) {
                    sb.append(jc.leftAlias).append(".").append(jc.leftColumn)
                            .append(" ").append(jc.operator != null ? jc.operator : "=")
                            .append(" '").append(jc.fixedValue).append("'");
                } else {
                    sb.append(jc.leftAlias).append(".").append(jc.leftColumn)
                            .append(" ").append(jc.operator != null ? jc.operator : "=")
                            .append(" ").append(jc.rightAlias).append(".").append(jc.rightColumn);
                }
                firstOn = false;
            }
        }

        // WHERE
        List<String> whereParts = new ArrayList<>();
        for (WhereCondition wc : whereConditions) {
            if (wc.rawCondition != null) {
                whereParts.add(wc.rawCondition);
                continue;
            }
            if (wc.value == null && !wc.isNullCheck && !wc.isBetween && !wc.isIn) {
                skipped.add(wc.column + " (null 이라 조건 생략됨)");
                continue;
            }
            StringBuilder part = new StringBuilder();
            part.append(wc.alias).append(".").append(wc.column);
            if (wc.isNullCheck) {
                part.append(" ").append(wc.operator);
            } else if (wc.isBetween) {
                Object[] arr = (Object[]) wc.value;
                if (inlineBind) {
                    part.append(" BETWEEN ").append(fmt(arr[0])).append(" AND ").append(fmt(arr[1]));
                } else {
                    part.append(" BETWEEN ? AND ?");
                    bindings.add(arr[0]);
                    bindings.add(arr[1]);
                }
            } else if (wc.isIn) {
                List<?> list = (List<?>) wc.value;
                part.append(" ").append(wc.operator).append(" (");
                for (int i = 0; i < list.size(); i++) {
                    if (inlineBind) {
                        part.append(i > 0 ? ", " : "").append(fmt(list.get(i)));
                    } else {
                        part.append(i > 0 ? ", ?" : "?");
                        bindings.add(list.get(i));
                    }
                }
                part.append(")");
            } else {
                if (inlineBind) {
                    part.append(" ").append(wc.operator).append(" ").append(fmt(wc.value));
                } else {
                    part.append(" ").append(wc.operator).append(" ?");
                    bindings.add(wc.value);
                }
            }
            whereParts.add(part.toString());
        }
        if (!whereParts.isEmpty()) {
            sb.append("\nWHERE ").append(String.join("\n    AND ", whereParts));
        }

        // GROUP BY
        if (!groupByColumns.isEmpty()) {
            sb.append("\nGROUP BY ");
            boolean first = true;
            for (GroupByColumn gc : groupByColumns) {
                sb.append(first ? "" : ", ").append(gc.alias).append(".").append(gc.column);
                first = false;
            }
        }

        // HAVING
        if (!havingConditions.isEmpty()) {
            sb.append("\nHAVING ");
            boolean first = true;
            for (HavingCondition hc : havingConditions) {
                sb.append(first ? "" : " AND ");
                if (hc.rawCondition != null) {
                    sb.append(hc.rawCondition);
                } else {
                    if (inlineBind) {
                        sb.append(hc.expression).append(" ").append(hc.operator)
                                .append(" ").append(fmt(hc.value));
                    } else {
                        sb.append(hc.expression).append(" ").append(hc.operator).append(" ?");
                        bindings.add(hc.value);
                    }
                }
                first = false;
            }
        }

        // ORDER BY
        if (!orderByColumns.isEmpty()) {
            sb.append("\nORDER BY ");
            boolean first = true;
            for (OrderByColumn oc : orderByColumns) {
                sb.append(first ? "" : ", ")
                        .append(oc.alias).append(".").append(oc.column)
                        .append(oc.descending ? " DESC" : " ASC");
                first = false;
            }
        }

        if (limitCount != null) sb.append("\nLIMIT ").append(limitCount);
        if (offsetCount != null) sb.append("\nOFFSET ").append(offsetCount);

        if (!inlineBind && !bindings.isEmpty()) {
            sb.append("\n\nBindings:");
            for (int i = 0; i < bindings.size(); i++) {
                sb.append("\n  [").append(i + 1).append("] = ").append(fmt(bindings.get(i)));
            }
        }
        if (!skipped.isEmpty()) {
            sb.append("\n");
            for (String s : skipped) sb.append("\n// ").append(s);
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────

    private void requireJoin() {
        if (currentJoin == null) {
            throw new IllegalStateException("on/onValue/onRaw 호출 전에 innerJoin 또는 leftJoin을 먼저 호출해야 합니다.");
        }
    }

    private static void validateOperator(String op) {
        if (!List.of("=", "!=", "<>", ">", ">=", "<", "<=").contains(op)) {
            throw new IllegalArgumentException("허용되지 않는 연산자: " + op);
        }
    }

    private static void validateFixedValue(String v) {
        if (v != null && v.matches(".*['\";].*")) {
            throw new IllegalArgumentException("onValue 고정값에 특수문자(', \", ;)는 허용되지 않습니다.");
        }
    }

    private static void validateRaw(String raw) {
        if (raw == null) return;
        if (DynamicQueryKeys.RAW_INJECTION_PATTERN.matcher(raw).find()) {
            throw new IllegalArgumentException("Raw 조건에 잠재적 SQL Injection 패턴이 감지되었습니다: " + raw);
        }
    }

    private static boolean isNotEmpty(Object v) {
        return v != null && !v.toString().isEmpty();
    }

    private static String fmt(Object v) {
        if (v == null) return "NULL";
        if (v instanceof String) return "'" + v + "'";
        return String.valueOf(v);
    }

    // ──────────────────────────────────────────────
    // 내부 모델 클래스
    // ──────────────────────────────────────────────

    public enum JoinType { INNER, LEFT_OUTER }

    public static class MainTable {
        public final String tableName;
        public final String alias;
        public MainTable(String tableName, String alias) {
            this.tableName = tableName;
            this.alias = alias;
        }
    }

    public static class JoinTable {
        public final String tableName;
        public final String alias;
        public final JoinType joinType;
        public final List<JoinCondition> conditions = new ArrayList<>();
        public JoinTable(String tableName, String alias, JoinType joinType) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
        }
    }

    public static class JoinCondition {
        public final String leftAlias;
        public final String leftColumn;
        public final String operator;
        public final String rightAlias;
        public final String rightColumn;
        public final String fixedValue;
        public final String rawCondition;

        public JoinCondition(String leftAlias, String leftColumn, String operator,
                             String rightAlias, String rightColumn,
                             String fixedValue, String rawCondition) {
            this.leftAlias = leftAlias;
            this.leftColumn = leftColumn;
            this.operator = operator;
            this.rightAlias = rightAlias;
            this.rightColumn = rightColumn;
            this.fixedValue = fixedValue;
            this.rawCondition = rawCondition;
        }
    }

    public static class SelectColumn {
        public final String type;       // "PLAIN", "AGGREGATE", "CASE", "RAW"
        public final String aggFunc;    // "COUNT", "SUM" 등
        public final String alias;
        public final String column;
        public final String columnAlias;
        public final String rawExpression;
        public final List<CaseWhen> caseWhens;
        public final String caseElseValue;

        private SelectColumn(String type, String aggFunc, String alias, String column,
                             String columnAlias, String rawExpression,
                             List<CaseWhen> caseWhens, String caseElseValue) {
            this.type = type;
            this.aggFunc = aggFunc;
            this.alias = alias;
            this.column = column;
            this.columnAlias = columnAlias;
            this.rawExpression = rawExpression;
            this.caseWhens = caseWhens;
            this.caseElseValue = caseElseValue;
        }

        public static SelectColumn plain(String alias, String column, String columnAlias) {
            return new SelectColumn("PLAIN", null, alias, column, columnAlias, null, null, null);
        }

        public static SelectColumn aggregate(String func, String alias, String column, String columnAlias) {
            return new SelectColumn("AGGREGATE", func, alias, column, columnAlias, null, null, null);
        }

        public static SelectColumn raw(String rawExpression) {
            return new SelectColumn("RAW", null, null, null, null, rawExpression, null, null);
        }

        public static SelectColumn caseExpr(String columnAlias, String elseValue, List<CaseWhen> cases) {
            return new SelectColumn("CASE", null, null, null, columnAlias, null, cases, elseValue);
        }

        public String toSql() {
            return switch (type) {
                case "AGGREGATE" -> aggFunc + "(" + alias + "." + column + ")"
                        + (columnAlias != null ? " AS " + columnAlias : "");
                case "RAW" -> rawExpression;
                case "CASE" -> {
                    StringBuilder sb = new StringBuilder("CASE");
                    for (CaseWhen cw : caseWhens) {
                        sb.append(" WHEN ").append(cw.alias).append(".").append(cw.column)
                                .append(" = '").append(cw.whenValue).append("'")
                                .append(" THEN '").append(cw.thenValue).append("'");
                    }
                    sb.append(" ELSE '").append(caseElseValue != null ? caseElseValue : "")
                            .append("' END");
                    if (columnAlias != null) sb.append(" AS ").append(columnAlias);
                    yield sb.toString();
                }
                default -> alias + "." + column + (columnAlias != null ? " AS " + columnAlias : "");
            };
        }
    }

    public static class WhereCondition {
        public final String alias;
        public final String column;
        public final String operator;
        public final Object value;
        public final boolean isNullCheck;
        public final boolean isBetween;
        public final boolean isIn;
        public final String rawCondition;
        public final Object[] rawParams;

        private WhereCondition(String alias, String column, String operator, Object value,
                               boolean isNullCheck, boolean isBetween, boolean isIn,
                               String rawCondition, Object[] rawParams) {
            this.alias = alias;
            this.column = column;
            this.operator = operator;
            this.value = value;
            this.isNullCheck = isNullCheck;
            this.isBetween = isBetween;
            this.isIn = isIn;
            this.rawCondition = rawCondition;
            this.rawParams = rawParams;
        }

        public static WhereCondition eq(String alias, String column, Object value) {
            return new WhereCondition(alias, column, "=", value, false, false, false, null, null);
        }

        public static WhereCondition op(String alias, String column, String operator, Object value) {
            return new WhereCondition(alias, column, operator, value, false, false, false, null, null);
        }

        public static WhereCondition between(String alias, String column, Object from, Object to) {
            return new WhereCondition(alias, column, "BETWEEN", new Object[]{from, to},
                    false, true, false, null, null);
        }

        public static WhereCondition in(String alias, String column, List<?> values) {
            return new WhereCondition(alias, column, "IN", values, false, false, true, null, null);
        }

        public static WhereCondition notIn(String alias, String column, List<?> values) {
            return new WhereCondition(alias, column, "NOT IN", values, false, false, true, null, null);
        }

        public static WhereCondition like(String alias, String column, String value) {
            return new WhereCondition(alias, column, "LIKE", value, false, false, false, null, null);
        }

        public static WhereCondition nullCheck(String alias, String column, String operator) {
            return new WhereCondition(alias, column, operator, null, true, false, false, null, null);
        }

        public static WhereCondition raw(String template, Object[] params) {
            // {} 플레이스홀더를 실제 파라미터명으로 치환하는 작업은 Builder에서 처리
            return new WhereCondition(null, null, null, null, false, false, false, template, params);
        }
    }

    public static class HavingCondition {
        public final String expression;
        public final String operator;
        public final Object value;
        public final String rawCondition;

        private HavingCondition(String expression, String operator, Object value, String rawCondition) {
            this.expression = expression;
            this.operator = operator;
            this.value = value;
            this.rawCondition = rawCondition;
        }

        public static HavingCondition expr(String expression, String operator, Object value) {
            return new HavingCondition(expression, operator, value, null);
        }

        public static HavingCondition raw(String rawCondition) {
            return new HavingCondition(null, null, null, rawCondition);
        }
    }

    public static class GroupByColumn {
        public final String alias;
        public final String column;
        public GroupByColumn(String alias, String column) {
            this.alias = alias;
            this.column = column;
        }
    }

    public static class OrderByColumn {
        public final String alias;
        public final String column;
        public final boolean descending;
        public OrderByColumn(String alias, String column, boolean descending) {
            this.alias = alias;
            this.column = column;
            this.descending = descending;
        }
    }

    /**
     * CASE WHEN THEN 조건 단위.
     *
     * <h3>생성 방법</h3>
     * <pre>{@code
     * CaseWhen.of("o", "STATUS", "0", "정상")
     * // → WHEN o.STATUS = '0' THEN '정상'
     * }</pre>
     */
    public static class CaseWhen {
        public final String alias;
        public final String column;
        public final String whenValue;
        public final String thenValue;

        private CaseWhen(String alias, String column, String whenValue, String thenValue) {
            this.alias = alias;
            this.column = column;
            this.whenValue = whenValue;
            this.thenValue = thenValue;
        }

        /**
         * WHEN 조건을 생성한다.
         *
         * @param alias     테이블 별칭
         * @param column    컬럼명
         * @param whenValue WHEN 비교값
         * @param thenValue THEN 반환값
         * @return CaseWhen 인스턴스
         */
        public static CaseWhen of(String alias, String column, String whenValue, String thenValue) {
            return new CaseWhen(alias, column, whenValue, thenValue);
        }
    }
}