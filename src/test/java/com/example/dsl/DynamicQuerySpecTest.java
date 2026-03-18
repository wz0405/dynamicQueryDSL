package com.example.dsl;

import com.example.dsl.mapper.DynamicQueryMapper;
import com.example.dsl.spec.DynamicQueryBuilder;
import com.example.dsl.spec.DynamicQuerySpec;
import com.example.dsl.spec.DynamicQuerySpec.CaseWhen;
import com.example.dsl.validator.DynamicQueryValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DynamicQuerySpecTest {

    @Autowired
    DynamicQueryMapper mapper;

    @Autowired
    DynamicQueryValidator validator;

    @Test
    @DisplayName("기본 WHERE + LIMIT")
    void testBasic() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", "store01")
                .where("o", "MERCHANT_ID", "merchant01")
                .limit(100);

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("기본조회", result);
    }

    @Test
    @DisplayName("whereIf — null 조건 자동 제외")
    void testWhereIf_null() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .where("o", "STORE_ID", "store01")
                .whereIf("o", "BUYER_NAME_ENC", null)
                .limit(100);

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).hasSize(5);
        print("whereIf null", result);
    }

    @Test
    @DisplayName("whereNot — != 조건")
    void testWhereNot() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "STATUS")
                .where("o", "STORE_ID", "store01")
                .whereNot("o", "STATUS", "2") // 부분취소 제외
                .limit(100)
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("whereNot", result);
    }

    @Test
    @DisplayName("whereGte / whereLte — 범위 조건")
    void testWhereRange() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", "store01")
                .whereGte("o", "AMOUNT", 150000)
                .whereLte("o", "AMOUNT", 500000)
                .orderBy("o", "AMOUNT", false)
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("whereGte/Lte", result);
    }

    @Test
    @DisplayName("whereNotIn — NOT IN 조건")
    void testWhereNotIn() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .where("o", "STORE_ID", "store01")
                .whereNotIn("o", "ORDER_ID", Arrays.asList("ORD001", "ORD002"))
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).hasSize(3);
        print("whereNotIn", result);
    }

    @Test
    @DisplayName("whereNull / whereNotNull — IS NULL / IS NOT NULL")
    void testNullCheck() {
        DynamicQuerySpec specNull = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "BUYER_NAME")
                .where("o", "STORE_ID", "store01")
                .whereNull("o", "BUYER_NAME"); // BUYER_NAME IS NULL

        DynamicQuerySpec specNotNull = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .where("o", "STORE_ID", "store01")
                .whereNotNull("o", "BUYER_NAME"); // BUYER_NAME IS NOT NULL

        List<Map<String, Object>> nullResult = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(specNull));
        List<Map<String, Object>> notNullResult = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(specNotNull));
        System.out.println("[IS NULL] 결과: " + nullResult.size() + "건");
        System.out.println("[IS NOT NULL] 결과: " + notNullResult.size() + "건");
    }

    @Test
    @DisplayName("BETWEEN 날짜 범위 조회")
    void testWhereBetween() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .where("o", "STORE_ID", "store01")
                .whereBetween("o", "ORDER_DT", "20260110", "20260120")
                .orderBy("o", "ORDER_DT", false)
                .limit(100);

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("BETWEEN", result);
    }

    @Test
    @DisplayName("INNER JOIN — 승인번호 필터")
    void testInnerJoin() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .innerJoin("PAYMENTS", "p")
                .on("o", "ORDER_ID", "p", "ORDER_ID")
                .on("o", "ORDER_DT", "p", "ORDER_DT")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("p", "APPROVAL_NO")
                .where("o", "STORE_ID", "store01")
                .whereIf("p", "APPROVAL_NO", "APV10001")
                .limit(100);

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("INNER JOIN", result);
    }

    @Test
    @DisplayName("LEFT JOIN + AS 별칭")
    void testLeftJoinWithAlias() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .leftJoin("ORDER_HISTORY", "h")
                .on("o", "ORDER_ID", "h", "ORDER_ID")
                .selectAs("o", "ORDER_ID", "orderId")
                .selectAs("o", "AMOUNT", "amount")
                .selectAs("h", "SETTLE_DT", "settleDt")
                .where("o", "STORE_ID", "store01")
                .whereBetween("o", "ORDER_DT", "20260101", "20260131")
                .orderBy("o", "ORDER_DT", true)
                .limit(50);

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("LEFT JOIN + AS", result);
    }

    @Test
    @DisplayName("WHERE IN — ORDER_ID 목록 조회")
    void testWhereIn() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .whereIn("o", "ORDER_ID", Arrays.asList("ORD001", "ORD002", "ORD003"));

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).hasSize(3);
        print("WHERE IN", result);
    }

    @Test
    @DisplayName("COUNT / SUM / AVG / MIN / MAX 집계함수")
    void testAggregates() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .selectCount("o", "ORDER_ID", "orderCnt")
                .selectSum("o", "AMOUNT", "totalAmount")
                .selectAvg("o", "AMOUNT", "avgAmount")
                .selectMin("o", "AMOUNT", "minAmount")
                .selectMax("o", "AMOUNT", "maxAmount")
                .where("o", "STORE_ID", "store01")
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).hasSize(1);
        print("집계함수", result);
    }

    @Test
    @DisplayName("GROUP BY + HAVING")
    void testGroupByHaving() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "STORE_ID")
                .selectCount("o", "ORDER_ID", "orderCnt")
                .selectSum("o", "AMOUNT", "totalAmount")
                .where("o", "STORE_ID", "store01")
                .groupBy("o", "STORE_ID")
                .having("totalAmount", ">", 0)
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("GROUP BY + HAVING", result);
    }

    @Test
    @DisplayName("CASE WHEN THEN ELSE END")
    void testCaseWhen() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "STATUS")
                .selectCaseElse("statusLabel", "알 수 없음",
                        CaseWhen.of("o", "STATUS", "0", "정상"),
                        CaseWhen.of("o", "STATUS", "1", "취소"),
                        CaseWhen.of("o", "STATUS", "2", "부분취소"))
                .where("o", "STORE_ID", "store01")
                .debug();

        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("CASE WHEN", result);
    }

    @Test
    @DisplayName("EXPLAIN 검증 포함 실행")
    void testWithValidation() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .where("o", "STORE_ID", "store01")
                .whereBetween("o", "ORDER_DT", "20260101", "20260131")
                .limit(100);

        validator.validate(spec);
        List<Map<String, Object>> result = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));
        assertThat(result).isNotEmpty();
        print("EXPLAIN 검증", result);
    }

    @Test
    @DisplayName("LIMIT + OFFSET 페이징")
    void testPaging() {
        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", "store01")
                .orderBy("o", "ORDER_DT", false)
                .limit(2)
                .offset(0)
                .debug();

        List<Map<String, Object>> page1 = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(spec));

        DynamicQuerySpec page2Spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .select("o", "ORDER_ID")
                .select("o", "AMOUNT")
                .where("o", "STORE_ID", "store01")
                .orderBy("o", "ORDER_DT", false)
                .limit(2)
                .offset(2);

        List<Map<String, Object>> page2 = mapper.selectDynamic(DynamicQueryBuilder.buildParamMap(page2Spec));

        System.out.println("[페이지1] " + page1.size() + "건: " + page1);
        System.out.println("[페이지2] " + page2.size() + "건: " + page2);
        assertThat(page1).hasSize(2);
    }

    private void print(String label, List<Map<String, Object>> result) {
        System.out.println("[" + label + "] 결과: " + result.size() + "건");
        result.forEach(r -> System.out.println("  " + r));
    }
}