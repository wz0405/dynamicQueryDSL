package com.example.dsl;

import com.example.dsl.sample.DynamicQuerySample;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * DynamicQuerySpec 샘플 API 엔드포인트.
 *
 * 서버 실행 후 브라우저 또는 curl로 확인 가능하다.
 */
@RestController
@RequiredArgsConstructor
public class SampleController {

    private final DynamicQuerySample sample;

    /**
     * 기본 단순 조회.
     * GET /sample/basic?storeId=store01&merchantId=merchant01
     */
    @GetMapping("/sample/basic")
    public List<Map<String, Object>> basic(
            @RequestParam(defaultValue = "store01") String storeId,
            @RequestParam(defaultValue = "merchant01") String merchantId) {
        return sample.sampleBasic(storeId, merchantId);
    }

    /**
     * 날짜 범위 + 선택적 필터 (buyerNameEnc 없으면 전체 조회).
     * GET /sample/optional?storeId=store01&frDt=20260101&toDt=20260131
     * GET /sample/optional?storeId=store01&frDt=20260101&toDt=20260131&buyerNameEnc=enc_alice
     */
    @GetMapping("/sample/optional")
    public List<Map<String, Object>> optional(
            @RequestParam(defaultValue = "store01") String storeId,
            @RequestParam(defaultValue = "20260101") String frDt,
            @RequestParam(defaultValue = "20260131") String toDt,
            @RequestParam(required = false) String buyerNameEnc) {
        return sample.sampleWithOptionalFilter(storeId, frDt, toDt, buyerNameEnc);
    }

    /**
     * INNER JOIN + 승인번호 필터.
     * GET /sample/join?storeId=store01&frDt=20260101&toDt=20260131&approvalNo=APV10001
     */
    @GetMapping("/sample/join")
    public List<Map<String, Object>> join(
            @RequestParam(defaultValue = "store01") String storeId,
            @RequestParam(required = false) String approvalNo,
            @RequestParam(defaultValue = "20260101") String frDt,
            @RequestParam(defaultValue = "20260131") String toDt) {
        return sample.sampleWithJoin(storeId, approvalNo, frDt, toDt);
    }

    /**
     * WHERE IN 조회.
     * GET /sample/in?orderIds=ORD001,ORD002,ORD003
     */
    @GetMapping("/sample/in")
    public List<Map<String, Object>> in(
            @RequestParam(defaultValue = "ORD001,ORD002") String orderIds) {
        return sample.sampleWithIn(Arrays.asList(orderIds.split(",")));
    }

    /**
     * EXPLAIN 검증 포함 조회 — 서버 로그에서 EXPLAIN 결과 확인.
     * GET /sample/validate?storeId=store01&frDt=20260101&toDt=20260131
     */
    @GetMapping("/sample/validate")
    public List<Map<String, Object>> validate(
            @RequestParam(defaultValue = "store01") String storeId,
            @RequestParam(defaultValue = "20260101") String frDt,
            @RequestParam(defaultValue = "20260131") String toDt) {
        return sample.sampleWithValidation(storeId, frDt, toDt);
    }

    /**
     * LEFT JOIN + AS 별칭 조회.
     * GET /sample/leftjoin?storeId=store01&frDt=20260101&toDt=20260131
     */
    @GetMapping("/sample/leftjoin")
    public List<Map<String, Object>> leftJoin(
            @RequestParam(defaultValue = "store01") String storeId,
            @RequestParam(defaultValue = "20260101") String frDt,
            @RequestParam(defaultValue = "20260131") String toDt) {
        return sample.sampleWithLeftJoinAndAlias(storeId, frDt, toDt);
    }
}