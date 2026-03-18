# DynamicQuerySpec

> Java DSL for type-safe, XML-free dynamic SQL — with built-in EXPLAIN validation

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0-red)](https://mybatis.org/)

---

## 왜 만들었나

조회 조건이 하나 추가될 때마다 MyBatis XML을 열고, `<if test="">` 절을 추가하고, 테스트하는 사이클을 반복해야 했다.
조건이 10개면 XML 한 파일이 100줄을 넘어가고, PAYMENTS 테이블을 JOIN하려면 또 별도 XML과 Bean을 만들어야 했다.

또한, xml 기반으로 로직을 짜다보니 컨벤션 관리가 안됨을 직면했다. DSL으로 관리하면 최소한의 컨벤션 유지가 되기에 모듈을 제작했다.

DynamicQuerySpec은 이 문제를 Java 메서드 체이닝으로 해결한다.

**Before — XML 방식**
```xml
<select id="selectOrders" parameterType="map">
    SELECT o.ORDER_ID, o.ORDER_DT
    FROM ORDERS o
    <if test="approvalNo != null">
    INNER JOIN PAYMENTS p ON o.ORDER_ID = p.ORDER_ID
    </if>
    WHERE 1=1
    <if test="storeId != null">AND o.STORE_ID = #{storeId}</if>
    <if test="approvalNo != null">AND p.APPROVAL_NO = #{approvalNo}</if>
    <if test="frDt != null">AND o.ORDER_DT BETWEEN #{frDt} AND #{toDt}</if>
</select>
```

**After — DynamicQuerySpec**
```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .innerJoin("PAYMENTS", "p")
        .on("o", "ORDER_ID", "p", "ORDER_ID")
    .select("o", "ORDER_ID")
    .select("o", "ORDER_DT")
    .where("o", "STORE_ID", storeId)
    .whereIf("p", "APPROVAL_NO", approvalNo)  // null이면 자동 제외
    .whereBetween("o", "ORDER_DT", frDt, toDt)
    .limit(100)
    .debug();  // 개발 시 파라미터 치환된 SQL 로그 출력
```

---

## 지원 기능

| 카테고리 | 메서드 | 설명 |
|----------|--------|------|
| **FROM** | `mainTable(table, alias)` | 메인 테이블 설정 |
| **JOIN** | `innerJoin(table, alias)` | INNER JOIN |
| | `leftJoin(table, alias)` | LEFT OUTER JOIN |
| | `.on(la, lc, ra, rc)` | 컬럼 = 컬럼 |
| | `.on(la, lc, op, ra, rc)` | 컬럼 연산자 컬럼 |
| | `.onValue(alias, col, fixedVal)` | 컬럼 = 고정값 |
| | `.onRaw(rawSql)` | 원시 ON 조건 |
| **SELECT** | `select(alias, col)` | 일반 컬럼 |
| | `selectAs(alias, col, asAlias)` | AS 별칭 |
| | `selectCount/Sum/Avg/Min/Max(alias, col, asAlias)` | 집계함수 |
| | `distinct()` | SELECT DISTINCT |
| | `selectRaw(expr)` | 원시 표현식 |
| | `selectCase(asAlias, CaseWhen...)` | CASE WHEN THEN ELSE '' END |
| | `selectCaseElse(asAlias, else, CaseWhen...)` | CASE WHEN THEN ELSE val END |
| **WHERE** | `where(alias, col, val)` | = (null이면 제외) |
| | `whereIf(alias, col, val)` | = null-safe (의도 명시용) |
| | `whereNot(alias, col, val)` | != |
| | `whereGt/Gte/Lt/Lte(alias, col, val)` | > / >= / < / <= |
| | `whereBetween(alias, col, from, to)` | BETWEEN |
| | `whereIn(alias, col, List)` | IN |
| | `whereNotIn(alias, col, List)` | NOT IN |
| | `whereLike(alias, col, val)` | LIKE (% 직접 포함) |
| | `whereNull(alias, col)` | IS NULL |
| | `whereNotNull(alias, col)` | IS NOT NULL |
| | `whereRaw(template, params...)` | {} 플레이스홀더 원시 조건 |
| **GROUP BY** | `groupBy(alias, col)` | GROUP BY |
| **HAVING** | `having(expr, op, val)` | HAVING 조건 |
| | `havingRaw(rawSql)` | 원시 HAVING 조건 |
| **ORDER BY** | `orderBy(alias, col, desc)` | ORDER BY (true=DESC) |
| **페이징** | `limit(n)` | LIMIT |
| | `offset(n)` | OFFSET |
| **디버그** | `debug()` | 파라미터 치환 SQL 로그 출력 |
| **미지원** | UNION ALL, 서브쿼리 | 별도 조회 후 Java 병합 권장 |

---

## 핵심 기능 예시

### 집계 + GROUP BY + HAVING

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .select("o", "STORE_ID")
    .selectCount("o", "ORDER_ID", "orderCnt")
    .selectSum("o", "AMOUNT", "totalAmount")
    .where("o", "STORE_ID", storeId)
    .groupBy("o", "STORE_ID")
    .having("totalAmount", ">", 100000)
    .orderBy("o", "STORE_ID", false)
    .debug();
```

```sql
SELECT o.STORE_ID, COUNT(o.ORDER_ID) AS orderCnt, SUM(o.AMOUNT) AS totalAmount
FROM ORDERS o
WHERE o.STORE_ID = 'store01'
GROUP BY o.STORE_ID
HAVING totalAmount > 100000
ORDER BY o.STORE_ID ASC
```

---

### CASE WHEN THEN ELSE END

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .select("o", "ORDER_ID")
    .selectCaseElse("statusLabel", "알 수 없음",
        CaseWhen.of("o", "STATUS", "0", "정상"),
        CaseWhen.of("o", "STATUS", "1", "취소"),
        CaseWhen.of("o", "STATUS", "2", "부분취소"))
    .where("o", "STORE_ID", storeId);
```

```sql
SELECT o.ORDER_ID,
    CASE WHEN o.STATUS = '0' THEN '정상'
         WHEN o.STATUS = '1' THEN '취소'
         WHEN o.STATUS = '2' THEN '부분취소'
    ELSE '알 수 없음' END AS statusLabel
FROM ORDERS o
WHERE o.STORE_ID = 'store01'
```

---

### LEFT OUTER JOIN + 복합 ON 조건

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .leftJoin("ORDER_HISTORY", "h")
        .on("o", "ORDER_ID", "h", "ORDER_ID")
        .onValue("h", "STATUS", "0")
        .onRaw("DATE_FORMAT(h.SETTLE_DT, '%Y%m') = DATE_FORMAT(o.ORDER_DT, '%Y%m')")
    .select("o", "ORDER_ID")
    .selectAs("o", "AMOUNT", "amount")
    .selectAs("h", "SETTLE_DT", "settleDt")
    .where("o", "STORE_ID", storeId)
    .whereBetween("o", "ORDER_DT", frDt, toDt);
```

---

### whereRaw — {} 플레이스홀더 바인딩

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .select("o", "ORDER_ID")
    .where("o", "STORE_ID", storeId)
    .whereRaw("DATE_FORMAT(o.ORDER_DT, '%Y%m') = {}", yearMonth)
    .whereRaw("o.AMOUNT BETWEEN {} AND {}", minAmount, maxAmount);
```

---

### debug() — 파라미터 치환 SQL 즉시 출력

```java
new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .where("o", "STORE_ID", "store01")
    .whereIf("o", "BUYER_NAME_ENC", null)  // null → 조건 생략
    .whereBetween("o", "ORDER_DT", "20260101", "20260131")
    .limit(100)
    .debug();
```

```
[DynamicQuerySpec DEBUG]
SELECT
    o.*
FROM ORDERS o
WHERE o.STORE_ID = 'store01'
    AND o.ORDER_DT BETWEEN '20260101' AND '20260131'
LIMIT 100

// BUYER_NAME_ENC (null 이라 조건 생략됨)
```

---

### null-safe 동작 정리

`where()`, `whereIf()` 모두 null 또는 빈 문자열이면 조건을 생략한다.

```java
String approvalNo = null; // 외부에서 전달되지 않은 값

new DynamicQuerySpec()
    .mainTable("ORDERS", "o")
    .innerJoin("PAYMENTS", "p")
        .on("o", "ORDER_ID", "p", "ORDER_ID")
    .where("o", "STORE_ID", storeId)
    .whereIf("p", "APPROVAL_NO", approvalNo) // null → AND 조건 자체 생략
    .limit(100);
// → WHERE o.STORE_ID = ? (approvalNo 조건 없음)
```

---

## EXPLAIN 자동 검증

> MySQL / MariaDB 환경에서만 동작한다. H2는 자동으로 스킵된다.

```java
validator.setMode(DynamicQueryValidator.Mode.WARN);  // 개발환경: 로그만 출력
validator.setMode(DynamicQueryValidator.Mode.ERROR); // 운영환경: Exception throw

validator.validate(spec);
List<Map<String, Object>> result = mapper.selectDynamic(spec);
```

**검증 항목**

| 항목 | 설명 |
|------|------|
| Full Table Scan | `type = ALL` → 인덱스 DDL 권장 |
| 인덱스 미사용 | possible_keys 있으나 key = NULL |
| Using filesort | ORDER BY 컬럼 인덱스 권장 |
| Using temporary | GROUP BY / ORDER BY 최적화 권장 |

**로그 예시 — Full Scan 감지**
```
[DynamicQuery] 쿼리 성능 이슈:
[주의] 'o' 테이블 Full Scan 발생 (예상 5 rows).
  → 권장 DDL: CREATE INDEX IDX_ORDERS_01 ON ORDERS(STORE_ID);
[정보] 'o' 테이블 filesort 발생. ORDER BY 컬럼에 인덱스 추가를 고려하세요.
```

> 샘플 데이터가 소량이라 Full Scan으로 표시되는 것은 정상이다.

---

## 실행 방법

### 사전 준비

```sql
CREATE DATABASE testdb DEFAULT CHARACTER SET utf8mb4;
```

### application.yml

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
implementation 'org.mariadb.jdbc:mariadb-java-client:2.7.0'  // MariaDB
// implementation 'com.mysql:mysql-connector-j:8.3.0'          // MySQL
```

### 서버 실행

```bash
./gradlew bootRun
```

### 샘플 API

| Method | URL | 설명 |
|--------|-----|------|
| GET | /sample/basic | 기본 WHERE + LIMIT |
| GET | /sample/optional?buyerNameEnc=enc_alice | null-safe whereIf |
| GET | /sample/join?approvalNo=APV10001 | INNER JOIN 필터 |
| GET | /sample/in?orderIds=ORD001,ORD002 | WHERE IN |
| GET | /sample/validate | EXPLAIN 검증 (로그 확인) |
| GET | /sample/leftjoin | LEFT OUTER JOIN + AS |

---

## 프로젝트 구조

```
src/main/java/com/example/dsl/
├── spec/
│   ├── DynamicQuerySpec.java       # DSL 핵심 — 쿼리 명세 선언
│   ├── DynamicQueryBuilder.java    # SQL 생성기 (MyBatis SelectProvider)
│   └── DynamicQueryKeys.java       # 파라미터 키 상수 및 Injection 방어 패턴
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

## 설계 의도

이 DSL은 **필터용 쿼리** 특화다. 복잡한 집계나 UNION ALL은 의도적으로 배제했다.

다단계 조회 패턴에서 활용하면 효과적이다.

1. 조회 조건(필터)으로 ORDER_ID 목록을 뽑아온다 → **DynamicQuerySpec 담당**
2. 뽑아온 ORDER_ID로 각 테이블을 조회한다 (Chain JOIN)
3. Java에서 결과를 조합한다 (Fusion)

DynamicQuerySpec은 1번 단계를 XML 없이 처리하는 데 집중한다.

---

## SQL Injection 방어

`whereRaw`, `havingRaw`, `onRaw`, `selectRaw`, `onValue`에 외부 입력이 직접 포함되면 런타임에 즉시 차단한다.

```java
// 차단되는 패턴 예시
.whereRaw("o.STATUS = '0' OR 1=1")      // OR 1=1 → 차단
.whereRaw("o.STATUS = '0'; DROP TABLE") // ; → 차단
.whereRaw("${userInput}")               // EL 표현식 → 차단
.onValue("p", "TYPE", "' OR '1'='1")   // 특수문자 → 차단

// 올바른 외부값 처리 — {} 플레이스홀더 사용
.whereRaw("DATE_FORMAT(o.ORDER_DT, '%Y%m') = {}", yearMonth)
```

---

## 테스트 실행

### 단위 테스트

```bash
./gradlew test
```

테스트 케이스 목록 (`DynamicQuerySpecTest`):

| 테스트 | 검증 내용 |
|--------|-----------|
| `testBasic` | WHERE + LIMIT 기본 조회 |
| `testWhereIf_null` | null 조건 자동 제외 확인 |
| `testWhereIf_withValue` | 값 있을 때 조건 적용 |
| `testWhereNot` | != 조건 + debug() 로그 확인 |
| `testWhereRange` | whereGte / whereLte 범위 조건 |
| `testWhereNotIn` | NOT IN 조건 |
| `testNullCheck` | IS NULL / IS NOT NULL |
| `testWhereBetween` | BETWEEN 날짜 범위 |
| `testInnerJoin` | INNER JOIN + 승인번호 필터 |
| `testLeftJoinWithAlias` | LEFT OUTER JOIN + AS 별칭 |
| `testWhereIn` | WHERE IN 목록 조회 |
| `testAggregates` | COUNT / SUM / AVG / MIN / MAX |
| `testGroupByHaving` | GROUP BY + HAVING |
| `testCaseWhen` | CASE WHEN THEN ELSE END |
| `testWithValidation` | EXPLAIN 검증 포함 실행 |
| `testPaging` | LIMIT + OFFSET 페이징 |

---

## 샘플 API 상세

서버 실행 후 아래 URL로 각 기능을 확인할 수 있다.
샘플 데이터는 서버 시작 시 `schema.sql`에서 자동으로 생성된다.

### 기본 조회

```bash
# 전체 조회 (storeId=store01, merchantId=merchant01)
curl "http://localhost:8080/sample/basic"

# 특정 가맹점 조회
curl "http://localhost:8080/sample/basic?storeId=store01&merchantId=merchant02"
```

### null-safe 조건 (whereIf)

```bash
# buyerNameEnc 없음 → 조건 생략, 전체 조회
curl "http://localhost:8080/sample/optional"

# buyerNameEnc 있음 → 조건 적용
curl "http://localhost:8080/sample/optional?buyerNameEnc=enc_alice"

# 날짜 범위 지정
curl "http://localhost:8080/sample/optional?frDt=20260110&toDt=20260120"
```

### INNER JOIN + 승인번호 필터

```bash
# approvalNo 없음 → 전체 JOIN 조회
curl "http://localhost:8080/sample/join"

# 특정 승인번호 필터
curl "http://localhost:8080/sample/join?approvalNo=APV10001"

# 날짜 범위 + 승인번호
curl "http://localhost:8080/sample/join?approvalNo=APV10001&frDt=20260101&toDt=20260131"
```

### WHERE IN

```bash
# 기본 (ORD001, ORD002)
curl "http://localhost:8080/sample/in"

# 직접 지정
curl "http://localhost:8080/sample/in?orderIds=ORD001,ORD002,ORD003"
```

### EXPLAIN 검증

```bash
# 서버 로그에서 EXPLAIN 결과 확인
curl "http://localhost:8080/sample/validate"
```

기대 로그:
```
[DynamicQuery] EXPLAIN 분석 시작
[DynamicQuery] 쿼리 성능 이슈:
[주의] 'o' 테이블 Full Scan 발생 (예상 5 rows). WHERE 조건 컬럼에 인덱스 추가를 권장합니다.
[주의] 'o' 테이블 인덱스 미사용. possible_keys: null
[정보] 'o' 테이블 filesort 발생. ORDER BY 컬럼에 인덱스 추가를 고려하세요.
```

> 샘플 데이터가 소량이라 Full Scan으로 표시되는 것은 정상이다.
> 실제 운영 테이블에 인덱스가 걸려 있으면 `[최적]` 로그로 바뀐다.

### LEFT OUTER JOIN + AS 별칭

```bash
curl "http://localhost:8080/sample/leftjoin"

# 날짜 범위 지정
curl "http://localhost:8080/sample/leftjoin?frDt=20260110&toDt=20260120"
```

### 응답 예시

```bash
curl "http://localhost:8080/sample/join?approvalNo=APV10001"
```

```json
[
  { "ORDER_ID": "ORD001", "ORDER_DT": "20260110", "STATUS": "0" },
  { "ORDER_ID": "ORD003", "ORDER_DT": "20260120", "STATUS": "2" }
]
```