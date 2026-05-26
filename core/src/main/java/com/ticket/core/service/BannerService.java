package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Banner;
import com.ticket.core.mapper.BannerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Banner / 运营位服务
 */
@Service
public class BannerService {

    private final BannerMapper bannerMapper;

    public BannerService(BannerMapper bannerMapper) {
        this.bannerMapper = bannerMapper;
    }

    @Transactional
    public Banner save(Banner banner) {
        LocalDateTime now = LocalDateTime.now();
        if (banner.getSort() == null) banner.setSort(0);
        if (banner.getStatus() == null) banner.setStatus(0);
        if (banner.getLinkType() == null) banner.setLinkType(0);

        if (banner.getId() == null) {
            banner.setCreateTime(now);
            banner.setUpdateTime(now);
            bannerMapper.insert(banner);
            return banner;
        }
        Banner exist = bannerMapper.selectById(banner.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        banner.setUpdateTime(now);
        bannerMapper.update(banner);
        return bannerMapper.selectById(banner.getId());
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (bannerMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        bannerMapper.updateStatus(id, status, LocalDateTime.now());
    }

    /**
     * 拖拽排序:按 orderedIds 顺序写入 sort,从 10 开始,步长 10。
     */
    @Transactional
    public void reorder(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        int sort = 10;
        for (Long id : orderedIds) {
            bannerMapper.updateSort(id, sort, now);
            sort += 10;
        }
    }

    @Transactional
    public void delete(Long id) {
        bannerMapper.deleteById(id);
    }

    public Banner getById(Long id) {
        return bannerMapper.selectById(id);
    }

    public List<Banner> listByCondition(Integer status) {
        return bannerMapper.selectByCondition(status);
    }

    /** 用户端:仅 status=1 且当前时间在 start_at/end_at 范围内 */
    public List<Banner> listEffective() {
        return bannerMapper.selectEffective(LocalDateTime.now());
    }
}
