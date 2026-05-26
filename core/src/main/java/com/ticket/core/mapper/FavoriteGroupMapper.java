package com.ticket.core.mapper;
import com.ticket.core.domain.entity.FavoriteGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FavoriteGroupMapper {
    int insert(FavoriteGroup g);
    int update(FavoriteGroup g);
    int deleteById(@Param("id") Long id);

    FavoriteGroup selectById(@Param("id") Long id);
    FavoriteGroup selectByUserName(@Param("userId") Long userId, @Param("name") String name);
    List<FavoriteGroup> selectByUser(@Param("userId") Long userId);

    /** 删除分组时,把关联收藏的 group_id 置 NULL */
    int unsetGroupForFavorites(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
