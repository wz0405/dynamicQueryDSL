package com.example.dsl.sample;

import com.example.dsl.mapper.DynamicQueryMapper;
import com.example.dsl.spec.DynamicQuerySpec;
import com.example.dsl.validator.DynamicQueryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * {@link DynamicQuerySpec} 사용 예시 모음.
 *
 * <p>실제 서비스에서 활용할 수 있는 다양한 패턴을 제공한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQuerySample {

    private final DynamicQueryMapper mapper;
    private final DynamicQueryValidator validator;

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 1. 기본 단순 조회
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 기본 단순 조회 — WHERE + LIMIT.
     *
     * <pre>
     * SELECT o.ORDER_ID, o.ORDER_DT, o.AMOUNT
     * FROM ORDERS o
     * WHERE o.STORE_ID = 'store01'
     *   AND o.MERCHANT_ID = 'merchant01'
     * LIMIT 100
     * </pre>
     */
    public List<Map<String, Object>> sampleBasic(String storeId, String merchantId) {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", storeId)
                .where("o", "MERCHANT_ID", merchantId)
                .limit(100);

        return mapper.selectDynamic(spec);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 2. BETWEEN + 선택적 조건 (whereIf)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 날짜 범위 조회 + 선택적 필터.
     *
     * <p>{@code buyerNameEnc}가 null이면 조건에서 제외된다 (null-safe).</p>
     *
     * <pre>
     * SELECT o.ORDER_ID, o.ORDER_DT, o.BUYER_NAME
     * FROM ORDERS o
     * WHERE o.STORE_ID = 'store01'
     *   AND o.ORDER_DT BETWEEN '20260101' AND '20260131'
     *   [AND o.BUYER_NAME_ENC = 'enc_alice']  -- buyerNameEnc가 있을 때만
     * ORDER BY o.ORDER_DT DESC
     * LIMIT 100
     * </pre>
     */
    public List<Map<String, Object>> sampleWithOptionalFilter(
            String storeId, String frDt, String toDt, String buyerNameEnc) {

        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("o", "BUYER_NAME")
                .where("o", "STORE_ID", storeId)
                .whereBetween("o", "ORDER_DT", frDt, toDt)
                .whereIf("o", "BUYER_NAME_ENC", buyerNameEnc) // null이면 조건 제외
                .orderBy("o", "ORDER_DT", true)
                .limit(100);

        return mapper.selectDynamic(spec);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 3. INNER JOIN + 복합 조건
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * INNER JOIN을 통한 승인번호 기반 필터 조회.
     *
     * <p>기존에는 이 용도로 별도 Bean을 만들어야 했지만
     * DynamicQuerySpec으로 선언형으로 표현 가능하다.</p>
     *
     * <pre>
     * SELECT o.ORDER_ID, o.ORDER_DT, o.STATUS
     * FROM ORDERS o
     * INNER JOIN PAYMENTS p
     *     ON o.ORDER_ID = p.ORDER_ID AND o.ORDER_DT = p.ORDER_DT
     * WHERE o.STORE_ID = 'store01'
     *   [AND p.APPROVAL_NO = 'APV10001']
     *   AND o.ORDER_DT BETWEEN '20260101' AND '20260131'
     * LIMIT 100
     * </pre>
     */
    public List<Map<String, Object>> sampleWithJoin(
            String storeId, String approvalNo, String frDt, String toDt) {

        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .innerJoin("PAYMENTS", "p")
                .on("o", "ORDER_ID", "p", "ORDER_ID")
                .on("o", "ORDER_DT", "p", "ORDER_DT")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("o", "STATUS")
                .where("o", "STORE_ID", storeId)
                .whereIf("p", "APPROVAL_NO", approvalNo)
                .whereBetween("o", "ORDER_DT", frDt, toDt)
                .limit(100);

        return mapper.selectDynamic(spec);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 4. IN 조건
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * ORDER_ID 목록으로 IN 조회.
     *
     * <p>이전 단계에서 수집한 ID 목록을 조건으로 넘길 때 활용한다.</p>
     *
     * <pre>
     * SELECT o.ORDER_ID, o.AMOUNT, o.STATUS
     * FROM ORDERS o
     * WHERE o.ORDER_ID IN ('ORD001', 'ORD002', 'ORD003')
     * </pre>
     */
    public List<Map<String, Object>> sampleWithIn(List<String> orderIdList) {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .select("o", "STATUS")
                .whereIn("o", "ORDER_ID", orderIdList);

        return mapper.selectDynamic(spec);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 5. EXPLAIN 검증 포함
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * EXPLAIN 검증을 포함한 쿼리 실행.
     *
     * <p>validator가 EXPLAIN을 실행하여 Full Scan 등 성능 이슈를 사전에 감지한다.
     * 개발환경에서는 WARN 모드로, 운영환경 대용량 테이블은 ERROR 모드로 설정한다.</p>
     */
    public List<Map<String, Object>> sampleWithValidation(
            String storeId, String frDt, String toDt) {

        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", storeId)
                .whereBetween("o", "ORDER_DT", frDt, toDt)
                .orderBy("o", "ORDER_DT", true)
                .limit(100);

        // EXPLAIN 실행 — 이슈 발견 시 WARN 로그 출력
        validator.validate(spec);

        return mapper.selectDynamic(spec);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 예시 6. LEFT JOIN + AS 별칭
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * LEFT JOIN + 컬럼 AS 별칭 조회.
     *
     * <pre>
     * SELECT o.ORDER_ID AS orderId, o.AMOUNT AS amount, h.SETTLE_DT AS settleDt
     * FROM ORDERS o
     * LEFT JOIN ORDER_HISTORY h ON o.ORDER_ID = h.ORDER_ID
     * WHERE o.STORE_ID = 'store01'
     *   AND o.ORDER_DT BETWEEN '20260101' AND '20260131'
     * ORDER BY o.ORDER_DT DESC
     * LIMIT 50
     * </pre>
     */
    public List<Map<String, Object>> sampleWithLeftJoinAndAlias(
            String storeId, String frDt, String toDt) {

        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .leftJoin("ORDER_HISTORY", "h")
                .on("o", "ORDER_ID", "h", "ORDER_ID")
                .selectAs("o", "ORDER_ID", "orderId")
                .selectAs("o", "AMOUNT", "amount")
                .selectAs("h", "SETTLE_DT", "settleDt")
                .where("o", "STORE_ID", storeId)
                .whereBetween("o", "ORDER_DT", frDt, toDt)
                .orderBy("o", "ORDER_DT", true)
                .limit(50);

        return mapper.selectDynamic(spec);
    }
}