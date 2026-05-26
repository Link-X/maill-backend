package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.FavoriteGroup;
import com.ticket.core.domain.entity.UserFavorite;
import com.ticket.core.mapper.FavoriteGroupMapper;
import com.ticket.core.mapper.UserFavoriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FavoriteService {

    private final FavoriteGroupMapper groupMapper;
    private final UserFavoriteMapper favMapper;

    public FavoriteService(FavoriteGroupMapper groupMapper, UserFavoriteMapper favMapper) {
        this.groupMapper = groupMapper;
        this.favMapper = favMapper;
    }

    // ----- 收藏 -----
    @Transactional
    public boolean add(Long userId, Long showId, Long groupId) {
        if (groupId != null) {
            FavoriteGroup g = groupMapper.selectById(groupId);
            if (g == null || !g.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
            }
        }
        return favMapper.insertIgnore(userId, showId, groupId) > 0;
    }

    @Transactional
    public boolean remove(Long userId, Long showId) {
        return favMapper.delete(userId, showId) > 0;
    }

    @Transactional
    public void move(Long userId, Long showId, Long groupId) {
        if (groupId != null) {
            FavoriteGroup g = groupMapper.selectById(groupId);
            if (g == null || !g.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
            }
        }
        favMapper.updateGroup(userId, showId, groupId);
    }

    public boolean isFavorited(Long userId, Long showId) {
        return favMapper.countFavorite(userId, showId) > 0;
    }

    public Map<String, Object> list(Long userId, Long groupId, Boolean unset, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<UserFavorite> list = favMapper.selectByUser(userId, groupId, unset, offset, size);
        int total = favMapper.countByUser(userId, groupId, unset);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    // ----- 分组 -----
    @Transactional
    public FavoriteGroup createGroup(Long userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分组名不能为空");
        }
        String trimmed = name.trim();
        if (groupMapper.selectByUserName(userId, trimmed) != null) {
            throw new BusinessException(ErrorCode.FAVORITE_GROUP_NAME_DUPLICATED);
        }
        LocalDateTime now = LocalDateTime.now();
        FavoriteGroup g = new FavoriteGroup();
        g.setUserId(userId);
        g.setName(trimmed);
        g.setSort(0);
        g.setCreateTime(now);
        g.setUpdateTime(now);
        groupMapper.insert(g);
        return g;
    }

    @Transactional
    public FavoriteGroup renameGroup(Long userId, Long id, String name) {
        FavoriteGroup g = groupMapper.selectById(id);
        if (g == null || !g.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
        }
        String trimmed = name == null ? "" : name.trim();
        FavoriteGroup byName = groupMapper.selectByUserName(userId, trimmed);
        if (byName != null && !byName.getId().equals(id)) {
            throw new BusinessException(ErrorCode.FAVORITE_GROUP_NAME_DUPLICATED);
        }
        g.setName(trimmed);
        g.setUpdateTime(LocalDateTime.now());
        groupMapper.update(g);
        return g;
    }

    @Transactional
    public void deleteGroup(Long userId, Long id) {
        FavoriteGroup g = groupMapper.selectById(id);
        if (g == null || !g.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FAVORITE_GROUP_NOT_FOUND);
        }
        groupMapper.unsetGroupForFavorites(userId, id);
        groupMapper.deleteById(id);
    }

    public List<FavoriteGroup> listGroups(Long userId) {
        return groupMapper.selectByUser(userId);
    }
}
