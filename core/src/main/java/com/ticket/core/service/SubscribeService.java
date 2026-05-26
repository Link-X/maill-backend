package com.ticket.core.service;

import com.ticket.core.domain.entity.ShowSubscribe;
import com.ticket.core.mapper.ShowSubscribeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubscribeService {

    private final ShowSubscribeMapper mapper;

    public SubscribeService(ShowSubscribeMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public boolean add(Long userId, Long showId, Integer notifyBeforeMinutes) {
        Integer mins = notifyBeforeMinutes == null || notifyBeforeMinutes < 0 ? 10 : notifyBeforeMinutes;
        return mapper.insertIgnore(userId, showId, mins) > 0;
    }

    @Transactional
    public boolean remove(Long userId, Long showId) {
        return mapper.delete(userId, showId) > 0;
    }

    public boolean isSubscribed(Long userId, Long showId) {
        return mapper.countSubscribe(userId, showId) > 0;
    }

    public Map<String, Object> list(Long userId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowSubscribe> list = mapper.selectByUser(userId, offset, size);
        int total = mapper.countByUser(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }
}
