package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    int insert(Category category);

    int update(Category category);

    int deleteById(@Param("id") Long id);

    Category selectById(@Param("id") Long id);

    /** 按 name 精确查重 */
    Category selectByName(@Param("name") String name);

    /**
     * 列表查询（admin）
     * @param status  null 表示不过滤
     * @param keyword null/空 表示不过滤；按 name LIKE 前缀匹配
     */
    List<Category> selectByCondition(@Param("status") Integer status,
                                     @Param("keyword") String keyword);

    /** 用户端列表：仅 status=1，按 sort,id 排序 */
    List<Category> selectEnabled();

    /** 统计被 show 引用次数（删除前校验） */
    int countShowsByCategoryId(@Param("id") Long id);
}
