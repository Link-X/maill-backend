package com.ticket.core.mapper;
import com.ticket.core.domain.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserFavoriteMapper {
    /** INSERT IGNORE 幂等 */
    int insertIgnore(@Param("userId") Long userId,
                     @Param("showId") Long showId,
                     @Param("groupId") Long groupId);
    int delete(@Param("userId") Long userId, @Param("showId") Long showId);
    int updateGroup(@Param("userId") Long userId, @Param("showId") Long showId,
                    @Param("groupId") Long groupId);

    int countFavorite(@Param("userId") Long userId, @Param("showId") Long showId);

    /** 我的收藏列表 join show + show_vo;groupId 可为 'unset'(NULL)/null(不过滤) */
    List<UserFavorite> selectByUser(@Param("userId") Long userId,
                                    @Param("groupId") Long groupId,
                                    @Param("unset") Boolean unset,
                                    @Param("offset") Integer offset,
                                    @Param("size") Integer size);

    int countByUser(@Param("userId") Long userId,
                    @Param("groupId") Long groupId,
                    @Param("unset") Boolean unset);

    /** 演出的被收藏次数(可选,用于报表) */
    int countByShowId(@Param("showId") Long showId);
}
