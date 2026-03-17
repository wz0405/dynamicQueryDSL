package com.example.dsl.mapper;

import com.example.dsl.spec.DynamicQueryBuilder;
import com.example.dsl.spec.DynamicQuerySpec;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * {@link DynamicQuerySpec} 기반 동적 쿼리 실행 Mapper.
 *
 * <p>{@link DynamicQueryBuilder}가 런타임에 SQL을 생성하므로
 * XML 없이 Java 코드로 모든 쿼리를 구성할 수 있다.</p>
 */
@Mapper
public interface DynamicQueryMapper {

    /**
     * {@link DynamicQuerySpec}으로 구성된 쿼리를 실행하고 결과를 반환한다.
     *
     * @param spec 쿼리 명세
     * @return 조회 결과 목록
     */
    @SelectProvider(type = DynamicQueryBuilder.class, method = "build")
    List<Map<String, Object>> selectDynamic(DynamicQuerySpec spec);
}
