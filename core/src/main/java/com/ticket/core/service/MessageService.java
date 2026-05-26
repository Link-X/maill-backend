package com.ticket.core.service;

import com.ticket.core.domain.entity.Message;
import com.ticket.core.domain.entity.UserMessage;
import com.ticket.core.mapper.MessageMapper;
import com.ticket.core.mapper.UserMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息服务:
 * - 单发(sendSingle)给指定 userId
 * - 群发(broadcast)只写 message,不写 user_message
 * - 用户拉取时懒生成广播副本
 */
@Slf4j
@Service
public class MessageService {

    public static final int TYPE_ORDER = 1;
    public static final int TYPE_OPEN_SALE = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_INTERACTION = 4;
    public static final int TYPE_FOLLOW_FEED = 5;

    public static final int LINK_NONE = 0;
    public static final int LINK_SHOW = 1;
    public static final int LINK_ARTIST = 2;
    public static final int LINK_ARTICLE = 3;
    public static final int LINK_ORDER = 4;
    public static final int LINK_URL = 5;

    private final MessageMapper messageMapper;
    private final UserMessageMapper userMessageMapper;

    public MessageService(MessageMapper messageMapper, UserMessageMapper userMessageMapper) {
        this.messageMapper = messageMapper;
        this.userMessageMapper = userMessageMapper;
    }

    /**
     * 单发:同时写 message 和 user_message
     */
    @Transactional
    public Message sendSingle(Long userId, Integer type, String title, String content,
                              Integer linkType, String linkTarget) {
        LocalDateTime now = LocalDateTime.now();
        Message m = new Message();
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setLinkType(linkType == null ? LINK_NONE : linkType);
        m.setLinkTarget(linkTarget);
        m.setBroadcast(0);
        m.setCreateTime(now);
        m.setUpdateTime(now);
        messageMapper.insert(m);

        UserMessage um = new UserMessage();
        um.setUserId(userId);
        um.setMessageId(m.getId());
        um.setIsRead(0);
        um.setCreateTime(now);
        um.setUpdateTime(now);
        userMessageMapper.insert(um);
        return m;
    }

    /** 单发给多个用户(批量) */
    @Transactional
    public Message sendToUsers(List<Long> userIds, Integer type, String title, String content,
                               Integer linkType, String linkTarget) {
        LocalDateTime now = LocalDateTime.now();
        Message m = new Message();
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setLinkType(linkType == null ? LINK_NONE : linkType);
        m.setLinkTarget(linkTarget);
        m.setBroadcast(0);
        m.setCreateTime(now);
        m.setUpdateTime(now);
        messageMapper.insert(m);
        if (userIds != null && !userIds.isEmpty()) {
            List<UserMessage> list = new java.util.ArrayList<>(userIds.size());
            for (Long uid : userIds) {
                UserMessage um = new UserMessage();
                um.setUserId(uid);
                um.setMessageId(m.getId());
                um.setIsRead(0);
                um.setReadAt(null);
                list.add(um);
            }
            userMessageMapper.batchInsert(list);
        }
        return m;
    }

    /** 群发:只写 message,user_message 在用户拉取时懒生成 */
    @Transactional
    public Message broadcast(Integer type, String title, String content,
                             Integer linkType, String linkTarget) {
        LocalDateTime now = LocalDateTime.now();
        Message m = new Message();
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setLinkType(linkType == null ? LINK_NONE : linkType);
        m.setLinkTarget(linkTarget);
        m.setBroadcast(1);
        m.setCreateTime(now);
        m.setUpdateTime(now);
        messageMapper.insert(m);
        return m;
    }

    /**
     * 用户拉取列表:先懒生成广播副本,再分页查 user_message
     */
    @Transactional
    public Map<String, Object> listForUser(Long userId, Integer type, Integer page, Integer size) {
        userMessageMapper.lazyInsertBroadcastsFor(userId);
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<UserMessage> list = userMessageMapper.selectByUser(userId, type, offset, size);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    /**
     * 各类型 + 总未读数(懒生成后再统计)
     * 性能：一次 GROUP BY 取所有类型计数，应用层组装。
     */
    @Transactional
    public Map<String, Integer> unreadCounts(Long userId) {
        userMessageMapper.lazyInsertBroadcastsFor(userId);
        Map<String, Integer> r = new HashMap<>();
        r.put("order", 0);
        r.put("openSale", 0);
        r.put("system", 0);
        r.put("interaction", 0);
        r.put("followFeed", 0);
        int total = 0;
        List<Map<String, Object>> rows = userMessageMapper.countUnreadGroupByType(userId);
        for (Map<String, Object> row : rows) {
            Number typeNum = (Number) row.get("type");
            Number cntNum = (Number) row.get("cnt");
            if (typeNum == null || cntNum == null) continue;
            int type = typeNum.intValue();
            int cnt = cntNum.intValue();
            total += cnt;
            switch (type) {
                case TYPE_ORDER:       r.put("order", cnt); break;
                case TYPE_OPEN_SALE:   r.put("openSale", cnt); break;
                case TYPE_SYSTEM:      r.put("system", cnt); break;
                case TYPE_INTERACTION: r.put("interaction", cnt); break;
                case TYPE_FOLLOW_FEED: r.put("followFeed", cnt); break;
                default: break;
            }
        }
        r.put("total", total);
        return r;
    }

    @Transactional
    public int markRead(Long userId, List<Long> userMessageIds) {
        if (userMessageIds == null || userMessageIds.isEmpty()) return 0;
        return userMessageMapper.markRead(userMessageIds, userId, LocalDateTime.now());
    }

    @Transactional
    public int markAllRead(Long userId, Integer type) {
        userMessageMapper.lazyInsertBroadcastsFor(userId);
        return userMessageMapper.markAllRead(userId, type, LocalDateTime.now());
    }

    @Transactional
    public int delete(Long userId, List<Long> userMessageIds) {
        if (userMessageIds == null || userMessageIds.isEmpty()) return 0;
        return userMessageMapper.deleteByIds(userMessageIds, userId);
    }

    public Map<String, Object> listAdmin(Integer type, Integer broadcast, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<Message> list = messageMapper.selectByCondition(type, broadcast, offset, size);
        int total = messageMapper.countByCondition(type, broadcast);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }
}
