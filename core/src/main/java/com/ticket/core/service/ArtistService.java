package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Artist;
import com.ticket.core.mapper.ArtistMapper;
import com.ticket.core.mapper.UserFollowArtistMapper;
import com.ticket.core.mq.event.SearchSyncEvent;
import com.ticket.core.mq.producer.SearchSyncProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 艺人服务:CRUD + 关注关系。
 */
@Service
public class ArtistService {

    private final ArtistMapper artistMapper;
    private final UserFollowArtistMapper followMapper;
    private final SearchSyncProducer searchSyncProducer;

    public ArtistService(ArtistMapper artistMapper,
                         UserFollowArtistMapper followMapper,
                         SearchSyncProducer searchSyncProducer) {
        this.artistMapper = artistMapper;
        this.followMapper = followMapper;
        this.searchSyncProducer = searchSyncProducer;
    }

    @Transactional
    public Artist save(Artist artist) {
        LocalDateTime now = LocalDateTime.now();
        if (artist.getStatus() == null) artist.setStatus(1);
        if (artist.getGender() == null) artist.setGender(0);

        if (artist.getId() == null) {
            if (artist.getName() != null) artist.setName(artist.getName().trim());
            if (artist.getName() != null && artistMapper.selectByName(artist.getName()) != null) {
                throw new BusinessException(ErrorCode.ARTIST_NAME_DUPLICATED);
            }
            if (artist.getFollowCount() == null) artist.setFollowCount(0);
            if (artist.getShowCount() == null) artist.setShowCount(0);
            artist.setCreateTime(now);
            artist.setUpdateTime(now);
            artistMapper.insert(artist);
            // 发送搜索同步事件
            searchSyncProducer.send(SearchSyncEvent.upsert("artist", artist.getId()));
            return artist;
        }
        Artist exist = artistMapper.selectById(artist.getId());
        if (exist == null) throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        if (artist.getName() != null) {
            artist.setName(artist.getName().trim());
            Artist byName = artistMapper.selectByName(artist.getName());
            if (byName != null && !byName.getId().equals(artist.getId())) {
                throw new BusinessException(ErrorCode.ARTIST_NAME_DUPLICATED);
            }
        }
        artist.setUpdateTime(now);
        artistMapper.update(artist);
        // 发送搜索同步事件
        searchSyncProducer.send(SearchSyncEvent.upsert("artist", artist.getId()));
        return artistMapper.selectById(artist.getId());
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (artistMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        }
        artistMapper.updateStatus(id, status, LocalDateTime.now());
        // 状态变化也需要同步到 ES
        searchSyncProducer.send(SearchSyncEvent.upsert("artist", id));
    }

    @Transactional
    public void delete(Long id) {
        artistMapper.deleteById(id);
        // 同步删除 ES 文档
        searchSyncProducer.send(SearchSyncEvent.delete("artist", id));
    }

    public Artist getById(Long id) {
        return artistMapper.selectById(id);
    }

    public List<Artist> listByCondition(Integer status, String keyword) {
        return artistMapper.selectByCondition(status, keyword);
    }

    public List<Artist> listEnabled(Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        return artistMapper.selectEnabled(offset, size);
    }

    public int countEnabled() {
        return artistMapper.countEnabled();
    }

    @Transactional
    public boolean follow(Long userId, Long artistId) {
        if (artistMapper.selectById(artistId) == null) {
            throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        }
        int inserted = followMapper.insertIgnore(userId, artistId);
        if (inserted > 0) {
            artistMapper.incrFollowCount(artistId);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean unfollow(Long userId, Long artistId) {
        int deleted = followMapper.delete(userId, artistId);
        if (deleted > 0) {
            artistMapper.decrFollowCount(artistId);
            return true;
        }
        return false;
    }

    public boolean isFollowing(Long userId, Long artistId) {
        return followMapper.countFollow(userId, artistId) > 0;
    }

    public List<Artist> listFollowedArtists(Long userId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        return followMapper.selectFollowedArtists(userId, offset, size);
    }
}
