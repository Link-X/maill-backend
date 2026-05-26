package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Artist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArtistMapper {

    int insert(Artist artist);
    int update(Artist artist);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);
    int deleteById(@Param("id") Long id);

    Artist selectById(@Param("id") Long id);
    Artist selectByName(@Param("name") String name);

    List<Artist> selectByCondition(@Param("status") Integer status,
                                   @Param("keyword") String keyword);

    /** page=offset, size=limit (XML uses them as LIMIT #{size} OFFSET #{page}) */
    List<Artist> selectEnabled(@Param("page") Integer page,
                               @Param("size") Integer size);

    int countEnabled();

    int incrFollowCount(@Param("id") Long id);
    int decrFollowCount(@Param("id") Long id);

    int refreshShowCount(@Param("id") Long id);
}
