package com.example.dsl.mapper;

import com.example.dsl.spec.DynamicQueryBuilder;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * {@link com.example.dsl.spec.DynamicQuerySpec} 기반 동적 쿼리 실행 Mapper.
 *
 * <h3>동작 원리</h3>
 * <p>
 * MyBatis SelectProvider는 SQL 생성을 {@link DynamicQueryBuilder}에 위임한다.
 * 파라미터는 {@code Map<String, Object>} 형태로 전달된다.
 * </p>
 *
 * <h3>사용 방법</h3>
 * <pre>{@code
 * DynamicQuerySpec spec = new DynamicQuerySpec()
 *     .mainTable("ORDERS", "o")
 *     .where("o", "STORE_ID", storeId)
 *     .limit(100);
 *
 * // 반드시 buildParamMap()으로 파라미터 맵을 생성해서 전달
 * Map<String, Object> paramMap = DynamicQueryBuilder.buildParamMap(spec);
 * List<Map<String, Object>> result = mapper.selectDynamic(paramMap);
 * }</pre>
 */
@Mapper
public interface DynamicQueryMapper {

    /**
     * 동적 쿼리를 실행하고 결과를 반환한다.
     *
     * <p>파라미터 맵은 반드시 {@link DynamicQueryBuilder#buildParamMap(com.example.dsl.spec.DynamicQuerySpec)}
     * 으로 생성해야 한다. SQL과 파라미터 키가 일치해야 MyBatis 바인딩이 정상 동작한다.</p>
     *
     * @param paramMap {@code DynamicQueryBuilder.buildParamMap(spec)}으로 생성된 파라미터 맵
     * @return 조회 결과 목록
     */
    @SelectProvider(type = DynamicQueryBuilder.class, method = "buildSql")
    List<Map<String, Object>> selectDynamic(Map<String, Object> paramMap);
}