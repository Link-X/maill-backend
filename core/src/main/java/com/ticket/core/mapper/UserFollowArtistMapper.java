package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Artist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFollowArtistMapper {

    /** INSERT IGNORE — 幂等；返回插入行数(0/1) */
    int insertIgnore(@Param("userId") Long userId, @Param("artistId") Long artistId);

    int delete(@Param("userId") Long userId, @Param("artistId") Long artistId);

    int countFollow(@Param("userId") Long userId, @Param("artistId") Long artistId);

    /** page=offset,size=limit */
    List<Artist> selectFollowedArtists(@Param("userId") Long userId,
                                       @Param("page") Integer page,
                                       @Param("size") Integer size);

    int countFollowedByUser(@Param("userId") Long userId);
}
