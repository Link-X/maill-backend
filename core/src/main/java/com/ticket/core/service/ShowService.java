package com.ticket.core.service;

import com.ticket.core.domain.entity.Artist;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowArtist;
import com.ticket.core.domain.vo.ShowVO;
import com.ticket.core.mapper.ArtistMapper;
import com.ticket.core.mapper.ShowArtistMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mq.event.SearchSyncEvent;
import com.ticket.core.mq.producer.SearchSyncProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShowService {

    private final ShowMapper showMapper;
    private final ShowArtistMapper showArtistMapper;
    private final ArtistMapper artistMapper;
    private final SearchSyncProducer searchSyncProducer;
    private final com.ticket.core.cache.CacheInvalidationBroadcaster cacheBroadcaster;

    public ShowService(ShowMapper showMapper,
                       ShowArtistMapper showArtistMapper,
                       ArtistMapper artistMapper,
                       SearchSyncProducer searchSyncProducer,
                       com.ticket.core.cache.CacheInvalidationBroadcaster cacheBroadcaster) {
        this.showMapper = showMapper;
        this.showArtistMapper = showArtistMapper;
        this.artistMapper = artistMapper;
        this.searchSyncProducer = searchSyncProducer;
        this.cacheBroadcaster = cacheBroadcaster;
    }

    @Transactional
    public Show create(Show show, List<Long> artistIds, Map<Long, String> artistRoles) {
        LocalDateTime now = LocalDateTime.now();
        show.setStatus(1);
        if (show.getReviewMode() == null) show.setReviewMode(1);
        if (show.getAvgRating() == null) show.setAvgRating(java.math.BigDecimal.ZERO);
        if (show.getReviewCount() == null) show.setReviewCount(0);
        show.setCreateTime(now);
        show.setUpdateTime(now);
        showMapper.insert(show);
        syncShowArtists(show.getId(), artistIds, artistRoles);
        Show saved = showMapper.selectById(show.getId());
        saved.setArtists(showArtistMapper.selectArtistsByShowId(show.getId()));
        // 发送搜索同步事件：写 ES
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("show", show.getId()));
        return saved;
    }

    /** 旧签名兼容(若有其他 caller) */
    @Transactional
    public Show create(Show show) {
        return create(show, null, null);
    }

    @Transactional
    public Show update(Show show, List<Long> artistIds, Map<Long, String> artistRoles) {
        show.setUpdateTime(LocalDateTime.now());
        // 不在 update 中修改 avg_rating / review_count,XML 也未 set 它们
        showMapper.update(show);
        if (artistIds != null) {
            syncShowArtists(show.getId(), artistIds, artistRoles);
        }
        Show updated = showMapper.selectById(show.getId());
        updated.setArtists(showArtistMapper.selectArtistsByShowId(show.getId()));
        // 广播到所有实例失效演出缓存
        cacheBroadcaster.invalidateShow(show.getId());
        // 发送搜索同步事件：写 ES
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("show", show.getId()));
        return updated;
    }

    @Transactional
    public Show update(Show show) {
        return update(show, null, null);
    }

    private void syncShowArtists(Long showId, List<Long> artistIds, Map<Long, String> artistRoles) {
        // 收集旧关联 artistId(用于变更后刷新 show_count)
        List<Artist> oldArtists = showArtistMapper.selectArtistsByShowId(showId);
        Set<Long> affected = new HashSet<>();
        for (Artist a : oldArtists) affected.add(a.getId());

        showArtistMapper.deleteByShowId(showId);

        if (artistIds != null && !artistIds.isEmpty()) {
            List<ShowArtist> links = new ArrayList<>();
            int sort = 10;
            for (Long aid : artistIds) {
                ShowArtist sa = new ShowArtist();
                sa.setShowId(showId);
                sa.setArtistId(aid);
                sa.setRole(artistRoles != null ? artistRoles.get(aid) : null);
                sa.setSort(sort);
                links.add(sa);
                affected.add(aid);
                sort += 10;
            }
            showArtistMapper.batchInsert(links);
        }
        // 重算冗余 show_count
        for (Long aid : affected) {
            artistMapper.refreshShowCount(aid);
        }
    }

    public Show getById(Long id) {
        Show show = showMapper.selectById(id);
        if (show != null) {
            show.setArtists(showArtistMapper.selectArtistsByShowId(id));
        }
        return show;
    }

    public ShowVO getVOById(Long id) {
        ShowVO vo = showMapper.selectVOById(id);
        if (vo != null) {
            vo.setArtists(showArtistMapper.selectArtistsByShowId(id));
        }
        return vo;
    }

    public List<Show> listAll(Integer status) {
        if (status != null) {
            return showMapper.selectByStatus(status);
        }
        return showMapper.selectAll();
    }

    public List<ShowVO> listPaged(String name, Long categoryId, String cityCode,
                                  String venue, int page, int size) {
        int offset = (page - 1) * size;
        return showMapper.selectVOByCondition(name, categoryId, cityCode, venue, 1, offset, size);
    }

    public int count(String name, Long categoryId, String cityCode, String venue) {
        return showMapper.countByCondition(name, categoryId, cityCode, venue, 1);
    }

    /**
     * 按艺人查演出列表(VO 含 categoryName/cityName)。
     * 用于艺人主页"参演演出"模块。
     * 不返回 artists 字段(列表场景与 listPaged 保持一致,详情接口才带)。
     */
    public List<ShowVO> listByArtistId(Long artistId, int page, int size) {
        int offset = (page - 1) * size;
        List<Long> ids = showArtistMapper.selectShowIdsByArtistId(artistId, offset, size);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return showMapper.selectVOByIds(ids);
    }

    /** 按艺人统计演出总数(分页器用) */
    public int countByArtistId(Long artistId) {
        return showArtistMapper.countShowsByArtistId(artistId);
    }
}
