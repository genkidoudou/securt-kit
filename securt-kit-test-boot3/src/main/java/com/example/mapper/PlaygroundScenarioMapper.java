package com.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Playground 复杂查询场景专用 Mapper（表别名 / 列别名 / JOIN / 函数边界）。
 */
@Mapper
public interface PlaygroundScenarioMapper {

    List<Map<String, Object>> selectByPhoneTableAlias(@Param("phone") String phone);

    List<Map<String, Object>> selectByPhoneColumnAlias(@Param("phone") String phone);

    List<Map<String, Object>> selectJoinUserOrders(@Param("userId") Long userId,
                                                   @Param("phone") String phone);

    List<Map<String, Object>> selectByPhoneLike(@Param("pattern") String pattern);

    List<Map<String, Object>> selectFuncOnCipher(@Param("phone") String phone);
}
