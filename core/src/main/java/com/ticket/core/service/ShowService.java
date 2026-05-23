package com.ticket.core.service;

import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.vo.ShowVO;
import com.ticket.core.mapper.ShowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演出服务（只负责 Show 维度的 CRUD / 列表 / 详情）。
 * 场次相关请见 {@link SessionService}。
 */
@Service
public class ShowService {

    private final ShowMapper showMapper;

    public ShowService(ShowMapper showMapper) {
        this.showMapper = showMapper;
    }

    /**
     * 创建演出（status 强制为 1，已上架）
     */
    public Show create(Show show) {
        LocalDateTime now = LocalDateTime.now();
        show.setStatus(1);
        show.setCreateTime(now);
        show.setUpdateTime(now);
        showMapper.insert(show);
        return show;
    }

    public Show update(Show show) {
        show.setUpdateTime(LocalDateTime.now());
        showMapper.update(show);
        return showMapper.selectById(show.getId());
    }

    public Show getById(Long id) {
        return showMapper.selectById(id);
    }

    /** 带 categoryName / cityName 的演出详情（用户端 / 列表展示用） */
    public ShowVO getVOById(Long id) {
        return showMapper.selectVOById(id);
    }

    /**
     * 列出演出
     * status 非空调 selectByStatus，否则 selectAll
     */
    public List<Show> listAll(Integer status) {
        if (status != null) {
            return showMapper.selectByStatus(status);
        }
        return showMapper.selectAll();
    }

    /**
     * 用户端分页列表（仅 status=1，带 categoryName / cityName）
     */
    public List<ShowVO> listPaged(String name, Long categoryId, String cityCode,
                                  String venue, int page, int size) {
        int offset = (page - 1) * size;
        return showMapper.selectVOByCondition(name, categoryId, cityCode, venue, 1, offset, size);
    }

    public int count(String name, Long categoryId, String cityCode, String venue) {
        return showMapper.countByCondition(name, categoryId, cityCode, venue, 1);
    }
}
