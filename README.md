# DynamicQuerySpec

> Java DSL for type-safe, XML-free dynamic SQL — with built-in EXPLAIN validation

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0-red)](https://mybatis.org/)

---

## 왜 만들었나

조회 조건이 하나 추가될 때마다 MyBatis XML을 열고, `<if test="">` 절을 추가하고, 테스트하는 사이클을 반복해야 했다.
조건이 10개면 XML 한 파일이 100줄을 넘어가고, PAYMENTS 테이블을 JOIN하려면 또 별도 XML과 Bean을 만들어야 했다.

DynamicQuerySpec은 이 문제를 Java 메서드 체이닝으로 해결한다.

**Before — XML 방식**
```xml
<!-- 조건 하나 추가할 때마다 XML 수정 -->
<select id="selectOrders" parameterType="map">
    SELECT o.ORDER_ID, o.ORDER_DT
    FROM ORDERS o
    <if test="approvalNo != null">
    INNER JOIN PAYMENTS p ON o.ORDER_ID = p.ORDER_ID
    </if>
    WHERE 1=1
    <if test="storeId != null">AND o.STORE_ID = #{storeId}</if>
    <if test="merchantId != null">AND o.MERCHANT_ID = #{merchantId}</if>
    <if test="approvalNo != null">AND p.APPROVAL_NO = #{approvalNo}</if>
    <if test="frDt != null">AND o.ORDER_DT BETWEEN #{frDt} AND #{toDt}</if>
</select>
```

**After — DynamicQuerySpec**
```java
// 조건 추가 = 메서드 한 줄
DynamicQuerySpec spec = new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .innerJoin("PAYMENTS", "p")
        .on("o", "ORDER_ID", "p", "ORDER_ID")
    .select("o", "ORDER_ID")
    .select("o", "ORDER_DT")
    .where("o", "STORE_ID", storeId)
    .whereIf("p", "APPROVAL_NO", approvalNo)  // null이면 자동 제외
    .whereBetween("o", "ORDER_DT", frDt, toDt)
    .limit(100);

List<Map<String, Object>> result = mapper.selectDynamic(spec);
```

---

## 핵심 기능

### 1. 선언형 쿼리 구성

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")                    // FROM
    .innerJoin("PAYMENTS", "p")                  // INNER JOIN
        .on("o", "ORDER_ID", "p", "ORDER_ID")    //   ON 조건
        .onValue("p", "PAY_TYPE", "CARD")        //   고정값 조건
    .select("o", "ORDER_ID")                     // SELECT
    .selectAs("o", "AMOUNT", "orderAmount")      // SELECT AS
    .where("o", "STORE_ID", storeId)             // WHERE (필수)
    .whereIf("o", "MERCHANT_ID", merchantId)     // WHERE (옵션, null-safe)
    .whereBetween("o", "ORDER_DT", frDt, toDt)   // BETWEEN
    .whereIn("o", "ORDER_ID", orderIdList)        // IN
    .whereLike("o", "BUYER_NAME", "%Alice%")      // LIKE
    .orderBy("o", "ORDER_DT", true)               // ORDER BY DESC
    .limit(100)
    .offset(0);
```

### 2. EXPLAIN 자동 검증

> MySQL / MariaDB 환경에서만 동작한다. H2는 자동으로 스킵된다.

```java
validator.setMode(DynamicQueryValidator.Mode.WARN);  // 개발환경: 로그만 출력
validator.setMode(DynamicQueryValidator.Mode.ERROR); // 운영환경: Exception throw

validator.validate(spec);
List<Map<String, Object>> result = mapper.selectDynamic(spec);
```

| 검증 항목 | 설명 |
|-----------|------|
| Full Table Scan | `type = ALL` 감지 → 인덱스 DDL 권장 |
| 인덱스 미사용 | possible_keys 있으나 key = NULL |
| Using filesort | ORDER BY 컬럼 인덱스 권장 |
| Using temporary | GROUP BY / ORDER BY 최적화 권장 |

**로그 예시 — 이슈 없음**
```
[DynamicQuery] EXPLAIN 분석 시작
[DynamicQuery] [최적] 'p' 테이블 인덱스 사용 (type=ref)
[DynamicQuery] [최적] 'o' 테이블 인덱스 사용 (type=eq_ref)
[DynamicQuery] EXPLAIN 분석 완료 — 이슈 없음
```

**로그 예시 — 이슈 감지**
```
[DynamicQuery] 쿼리 성능 이슈:
[주의] 'o' 테이블 Full Scan 발생 (예상 5 rows). WHERE 조건 컬럼에 인덱스 추가를 권장합니다.
[주의] 'o' 테이블 인덱스 미사용. possible_keys: null
[정보] 'o' 테이블 filesort 발생. ORDER BY 컬럼에 인덱스 추가를 고려하세요.
```

> 샘플 데이터가 소량이라 Full Scan으로 표시되는 것은 정상이다.
> 실제 운영 테이블에 인덱스가 걸려 있으면 `[최적]` 로그로 바뀐다.

### 3. null-safe 조건 처리

```java
// buyerNameEnc가 null이면 AND 조건 자체가 생성되지 않음
.whereIf("o", "BUYER_NAME_ENC", buyerNameEnc)
```

---

## 실행 방법

### 사전 준비

MySQL 또는 MariaDB에 스키마를 먼저 생성한다.

```sql
CREATE DATABASE testdb DEFAULT CHARACTER SET utf8mb4;
```

### application.yml

MariaDB 사용 시:
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/testdb?characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    driver-class-name: org.mariadb.jdbc.Driver
    username: your_username
    password: your_password
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis:
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.example.dsl: DEBUG
```

MySQL 사용 시:
```yaml
url: jdbc:mysql://localhost:3306/testdb?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
driver-class-name: com.mysql.cj.jdbc.Driver
```

### build.gradle

```groovy
// MariaDB
implementation 'org.mariadb.jdbc:mariadb-java-client:2.7.0'

// MySQL
implementation 'com.mysql:mysql-connector-j:8.3.0'
```

### 서버 실행

```bash
./gradlew bootRun
```

### 샘플 API

| Method | URL | 설명 |
|--------|-----|------|
| GET | /sample/basic | 기본 WHERE + LIMIT |
| GET | /sample/basic?storeId=store01&merchantId=merchant01 | STORE + MERCHANT 조건 |
| GET | /sample/optional | 날짜 범위 조회 (buyerNameEnc 없으면 전체) |
| GET | /sample/optional?buyerNameEnc=enc_alice | 구매자명 암호화 조건 포함 |
| GET | /sample/join | INNER JOIN (approvalNo 없으면 전체) |
| GET | /sample/join?approvalNo=APV10001 | 승인번호로 필터 후 JOIN |
| GET | /sample/in?orderIds=ORD001,ORD002,ORD003 | WHERE IN 조회 |
| GET | /sample/validate | EXPLAIN 검증 포함 (서버 로그 확인) |
| GET | /sample/leftjoin | LEFT JOIN + AS 별칭 |

---

## 서비스 사용 예시

```java
@RequiredArgsConstructor
@Service
public class OrderService {

    private final DynamicQueryMapper mapper;
    private final DynamicQueryValidator validator;

    public List<Map<String, Object>> findOrders(
            String storeId, String frDt, String toDt, String approvalNo) {

        DynamicQuerySpec spec = new DynamicQuerySpec()
                .mainTable("ORDERS", "o")
                .innerJoin("PAYMENTS", "p")
                    .on("o", "ORDER_ID", "p", "ORDER_ID")
                    .on("o", "ORDER_DT", "p", "ORDER_DT")
                .select("o", "ORDER_ID")
                .select("o", "ORDER_DT")
                .select("p", "APPROVAL_NO")
                .where("o", "STORE_ID", storeId)
                .whereBetween("o", "ORDER_DT", frDt, toDt)
                .whereIf("p", "APPROVAL_NO", approvalNo)
                .orderBy("o", "ORDER_DT", true)
                .limit(100);

        validator.validate(spec);
        return mapper.selectDynamic(spec);
    }
}
```

---

## 프로젝트 구조

```
src/main/java/com/example/dsl/
├── spec/
│   ├── DynamicQuerySpec.java       # DSL 핵심 — 쿼리 명세 선언
│   └── DynamicQueryBuilder.java    # SQL 생성기 (MyBatis Provider)
├── validator/
│   └── DynamicQueryValidator.java  # EXPLAIN 기반 성능 검증
├── mapper/
│   └── DynamicQueryMapper.java     # MyBatis Mapper
├── sample/
│   └── DynamicQuerySample.java     # 사용 예시 6종
├── config/
│   └── ValidatorConfig.java        # Validator Bean 설정
├── SampleController.java           # REST API 엔드포인트
└── DynamicQuerySpecApplication.java
```

---

## 지원 기능

| 기능 | 지원 |
|------|------|
| SELECT / SELECT AS | ✅ |
| INNER JOIN / LEFT JOIN | ✅ |
| ON 컬럼 = 컬럼 | ✅ |
| ON 컬럼 = 고정값 | ✅ |
| WHERE = | ✅ |
| WHERE null-safe (whereIf) | ✅ |
| WHERE BETWEEN | ✅ |
| WHERE IN | ✅ |
| WHERE LIKE | ✅ |
| ORDER BY ASC/DESC | ✅ |
| LIMIT / OFFSET | ✅ |
| EXPLAIN 자동 검증 | ✅ (MySQL / MariaDB) |
| UNION ALL | 미지원 (별도 조회 후 Java 병합 권장) |
| 서브쿼리 | 미지원 |

---

## 설계 의도

이 DSL은 **필터용 쿼리** 특화다. 복잡한 집계나 UNION ALL은 의도적으로 배제했다.

다단계 조회 패턴에서 활용하면 효과적이다.

1. 조회 조건(필터)으로 ORDER_ID 목록을 뽑아온다 — **DynamicQuerySpec 담당**
2. 뽑아온 ORDER_ID로 각 테이블을 조회한다 (Chain JOIN)
3. Java에서 결과를 조합한다 (Fusion)

DynamicQuerySpec은 1번 단계를 XML 없이 처리하는 데 집중한다.


