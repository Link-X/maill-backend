package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Artist;
import com.ticket.core.domain.entity.ShowArtist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShowArtistMapper {

    int insert(ShowArtist link);
    int batchInsert(@Param("links") List<ShowArtist> links);

    int deleteByShowId(@Param("showId") Long showId);
    int deleteByArtistId(@Param("artistId") Long artistId);

    List<Artist> selectArtistsByShowId(@Param("showId") Long showId);
    List<ShowArtist> selectByShowIds(@Param("showIds") List<Long> showIds);

    List<Long> selectShowIdsByArtistId(@Param("artistId") Long artistId,
                                       @Param("page") Integer page,
                                       @Param("size") Integer size);

    int countShowsByArtistId(@Param("artistId") Long artistId);
}
