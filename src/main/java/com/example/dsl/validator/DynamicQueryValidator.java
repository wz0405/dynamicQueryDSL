package com.example.dsl.validator;

import com.example.dsl.spec.DynamicQueryBuilder;
import com.example.dsl.spec.DynamicQuerySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link DynamicQuerySpec}으로 생성된 SQL에 대해 EXPLAIN을 실행하여
 * 성능 이슈(Full Scan, 인덱스 미사용, filesort 등)를 사전에 감지한다.
 *
 * <h3>동작 방식</h3>
 * <ol>
 *   <li>SQL 생성 (DynamicQueryBuilder)</li>
 *   <li>EXPLAIN 실행</li>
 *   <li>결과 분석 — Full Scan / 인덱스 미사용 / filesort / temporary 감지</li>
 *   <li>옵션에 따라 WARNING 로그 또는 Exception throw</li>
 * </ol>
 *
 * <h3>사용 예시</h3>
 * <pre>{@code
 * DynamicQueryValidator validator = new DynamicQueryValidator(jdbcTemplate);
 * validator.setMode(DynamicQueryValidator.Mode.WARN); // 개발환경 권장
 *
 * DynamicQuerySpec spec = new DynamicQuerySpec()
 *     .mainTable("TBTR_C_MSTR", "mstr")
 *     .where("mstr", "CID", cid)
 *     .limit(100);
 *
 * validator.validate(spec); // EXPLAIN 분석 후 이슈 로그 출력
 * }</pre>
 */
@Slf4j
public class DynamicQueryValidator {

    private final JdbcTemplate jdbcTemplate;
    private Mode mode = Mode.WARN;
    private int maxFullScanRows = 10_000;

    public enum Mode {
        /** 이슈 발생 시 WARNING 로그만 출력 (개발환경 권장) */
        WARN,
        /** 이슈 발생 시 Exception throw (운영환경 엄격 모드) */
        ERROR,
        /** 검증 비활성화 */
        OFF
    }

    public DynamicQueryValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 검증 모드를 설정한다.
     *
     * @param mode WARN / ERROR / OFF
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Full Scan 경고 기준 행 수를 설정한다. 기본값은 10,000.
     *
     * @param maxRows 경고 임계값
     */
    public void setMaxFullScanRows(int maxRows) {
        this.maxFullScanRows = maxRows;
    }
    private boolean isMySql() {
        try {
            String url = jdbcTemplate.getDataSource()
                    .getConnection().getMetaData().getURL();
            return url.startsWith("jdbc:mariadb");
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * 주어진 {@link DynamicQuerySpec}에 대해 EXPLAIN을 실행하고 성능 이슈를 분석한다.
     *
     * @param spec 분석할 쿼리 명세
     * @throws QueryPerformanceException mode가 ERROR이고 심각한 이슈가 발견된 경우
     */
    public void validate(DynamicQuerySpec spec) {
        if (mode == Mode.OFF) return;
        if (!isMySql()) {
            log.info("[DynamicQuery] H2 환경 — EXPLAIN 검증 스킵 (MySQL에서만 동작)");
            return;
        }

        DynamicQueryBuilder builder = new DynamicQueryBuilder();
        String sql = builder.build(spec).replaceAll("#\\{[^}]+\\}", "?");

        log.info("[DynamicQuery] EXPLAIN 분석 시작");
        log.debug("[DynamicQuery] SQL: {}", builder.buildPreview(spec));

        try {
            List<Map<String, Object>> explainRows =
                    jdbcTemplate.queryForList("EXPLAIN " + sql, collectParams(spec));

            List<String> issues = new ArrayList<>();

            for (Map<String, Object> row : explainRows) {
                String table = String.valueOf(row.get("table"));
                String type = String.valueOf(row.get("type"));
                String key = String.valueOf(row.get("key"));
                String extra = row.get("Extra") != null ? String.valueOf(row.get("Extra")) : "";
                Long rows = row.get("rows") instanceof Number n ? n.longValue() : null;

                // Full Table Scan
                if ("ALL".equalsIgnoreCase(type)) {
                    String msg = String.format(
                            "[주의] '%s' 테이블 Full Scan 발생 (예상 %,d rows). WHERE 조건 컬럼에 인덱스 추가를 권장합니다.",
                            table, rows != null ? rows : 0);
                    issues.add(msg);
                    if (rows != null && rows > maxFullScanRows) {
                        issues.add(String.format("  → 권장 DDL: CREATE INDEX IDX_%s_01 ON %s(조건컬럼);",
                                table.toUpperCase(), table));
                    }
                }

                // 인덱스 미사용
                if ("null".equals(key) || key == null || key.isEmpty()) {
                    issues.add(String.format("[주의] '%s' 테이블 인덱스 미사용. possible_keys: %s",
                            table, row.get("possible_keys")));
                }

                // Using filesort
                if (extra.contains("Using filesort")) {
                    issues.add(String.format(
                            "[정보] '%s' 테이블 filesort 발생. ORDER BY 컬럼에 인덱스 추가를 고려하세요.", table));
                }

                // Using temporary
                if (extra.contains("Using temporary")) {
                    issues.add(String.format(
                            "[주의] '%s' 테이블 임시 테이블 생성. GROUP BY / ORDER BY 최적화를 고려하세요.", table));
                }

                // 정상 케이스 로그
                if ("eq_ref".equalsIgnoreCase(type) || "ref".equalsIgnoreCase(type)) {
                    log.info("[DynamicQuery] [최적] '{}' 테이블 인덱스 사용 (type={})", table, type);
                }
            }

            if (!issues.isEmpty()) {
                String report = String.join("\n", issues);
                if (mode == Mode.ERROR) {
                    throw new QueryPerformanceException("쿼리 성능 이슈 감지:\n" + report);
                } else {
                    log.warn("[DynamicQuery] 쿼리 성능 이슈:\n{}", report);
                }
            } else {
                log.info("[DynamicQuery] EXPLAIN 분석 완료 — 이슈 없음");
            }

        } catch (QueryPerformanceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[DynamicQuery] EXPLAIN 실행 실패 (무시하고 계속 진행): {}", e.getMessage());
        }
    }

    // EXPLAIN 실행 시 파라미터 수집 (값 치환용)
    private Object[] collectParams(DynamicQuerySpec spec) {
        List<Object> params = new ArrayList<>();
        for (DynamicQuerySpec.WhereCondition wc : spec.whereConditions) {
            if (wc.type == DynamicQuerySpec.ConditionType.BETWEEN) {
                List<?> vals = (List<?>) wc.value;
                params.add(vals.get(0));
                params.add(vals.get(1));
            } else if (wc.type == DynamicQuerySpec.ConditionType.IN) {
                params.addAll((List<?>) wc.value);
            } else {
                params.add(wc.value);
            }
        }
        return params.toArray();
    }

    /**
     * 쿼리 성능 검증 실패 예외.
     * mode가 {@link Mode#ERROR}일 때 심각한 이슈 발견 시 throw된다.
     */
    public static class QueryPerformanceException extends RuntimeException {
        public QueryPerformanceException(String message) {
            super(message);
        }
    }
}
