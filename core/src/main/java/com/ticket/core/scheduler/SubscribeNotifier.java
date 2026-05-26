package com.ticket.core.scheduler;

import com.ticket.core.domain.vo.PendingSubscribeNotify;
import com.ticket.core.domain.vo.SubscribeSessionRef;
import com.ticket.core.mapper.ShowSubscribeMapper;
import com.ticket.core.mapper.ShowSubscribeSessionNotifyMapper;
import com.ticket.core.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开售提醒定时任务:订阅维度是"演出",推送维度按"场次×日"合并。
 *
 * 每分钟跑两次扫描:
 *  - scanPre:开售前 N 分钟提醒("即将开售")
 *  - scanOpen:开售时提醒("已开售")
 *
 * 按 (subscribeId, 开售日历日) 分组,把同一天开售的多个场次合并成一条消息,
 * 一个用户最多每天每演出收一条 pre + 一条 open,避免巡演场景下消息洪水。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class SubscribeNotifier {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日");

    private final ShowSubscribeMapper subscribeMapper;
    private final ShowSubscribeSessionNotifyMapper notifyMapper;
    private final MessageService messageService;

    public SubscribeNotifier(ShowSubscribeMapper subscribeMapper,
                             ShowSubscribeSessionNotifyMapper notifyMapper,
                             MessageService messageService) {
        this.subscribeMapper = subscribeMapper;
        this.notifyMapper = notifyMapper;
        this.messageService = messageService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanPre() {
        LocalDateTime now = LocalDateTime.now();
        List<PendingSubscribeNotify> pending = subscribeMapper.selectPendingPreBySession(now);
        if (pending.isEmpty()) return;
        Map<GroupKey, List<PendingSubscribeNotify>> grouped = groupByUserDay(pending);

        for (Map.Entry<GroupKey, List<PendingSubscribeNotify>> entry : grouped.entrySet()) {
            List<PendingSubscribeNotify> group = entry.getValue();
            PendingSubscribeNotify head = group.get(0);
            List<SubscribeSessionRef> pairs = group.stream()
                    .map(p -> new SubscribeSessionRef(p.getSubscribeId(), p.getSessionId()))
                    .collect(Collectors.toList());
            // 先标记已通知,upsert 失败就直接放弃本轮发消息,避免下分钟重复发洪水
            try {
                notifyMapper.batchUpsertNotifiedPre(pairs);
            } catch (Exception e) {
                log.error("[SUBSCRIBE-PRE] mark notified failed userId={} showId={} err={}",
                        head.getUserId(), head.getShowId(), e.getMessage(), e);
                continue;
            }
            try {
                String title = String.format("[开售提醒] %s %s 即将开售",
                        head.getShowName(), entry.getKey().date.format(MD));
                String content = buildPreContent(head, group);
                messageService.sendSingle(
                        head.getUserId(),
                        MessageService.TYPE_OPEN_SALE,
                        title,
                        content,
                        MessageService.LINK_SHOW,
                        String.valueOf(head.getShowId())
                );
            } catch (Exception e) {
                log.warn("[SUBSCRIBE-PRE] send fail (already marked notified) userId={} showId={} err={}",
                        head.getUserId(), head.getShowId(), e.getMessage());
            }
        }
        log.info("[SUBSCRIBE-PRE] processed {} groups ({} sessions)", grouped.size(), pending.size());
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanOpen() {
        LocalDateTime now = LocalDateTime.now();
        List<PendingSubscribeNotify> pending = subscribeMapper.selectPendingOpenBySession(now);
        if (pending.isEmpty()) return;
        Map<GroupKey, List<PendingSubscribeNotify>> grouped = groupByUserDay(pending);

        for (Map.Entry<GroupKey, List<PendingSubscribeNotify>> entry : grouped.entrySet()) {
            List<PendingSubscribeNotify> group = entry.getValue();
            PendingSubscribeNotify head = group.get(0);
            List<SubscribeSessionRef> pairs = group.stream()
                    .map(p -> new SubscribeSessionRef(p.getSubscribeId(), p.getSessionId()))
                    .collect(Collectors.toList());
            try {
                notifyMapper.batchUpsertNotifiedOpen(pairs);
            } catch (Exception e) {
                log.error("[SUBSCRIBE-OPEN] mark notified failed userId={} showId={} err={}",
                        head.getUserId(), head.getShowId(), e.getMessage(), e);
                continue;
            }
            try {
                String title = "[已开售] " + head.getShowName();
                String content = buildOpenContent(head, group);
                messageService.sendSingle(
                        head.getUserId(),
                        MessageService.TYPE_OPEN_SALE,
                        title,
                        content,
                        MessageService.LINK_SHOW,
                        String.valueOf(head.getShowId())
                );
            } catch (Exception e) {
                log.warn("[SUBSCRIBE-OPEN] send fail (already marked notified) userId={} showId={} err={}",
                        head.getUserId(), head.getShowId(), e.getMessage());
            }
        }
        log.info("[SUBSCRIBE-OPEN] processed {} groups ({} sessions)", grouped.size(), pending.size());
    }

    /** 按 (订阅 ID, 开售日历日) 分组,保持时间顺序 */
    private Map<GroupKey, List<PendingSubscribeNotify>> groupByUserDay(List<PendingSubscribeNotify> rows) {
        Map<GroupKey, List<PendingSubscribeNotify>> map = new LinkedHashMap<>();
        for (PendingSubscribeNotify r : rows) {
            GroupKey k = new GroupKey(r.getSubscribeId(), r.getOpenSaleTime().toLocalDate());
            map.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        for (List<PendingSubscribeNotify> list : map.values()) {
            list.sort(Comparator.comparing(PendingSubscribeNotify::getOpenSaleTime));
        }
        return map;
    }

    private String buildPreContent(PendingSubscribeNotify head, List<PendingSubscribeNotify> group) {
        Integer mins = head.getNotifyBeforeMinutes() == null ? 10 : head.getNotifyBeforeMinutes();
        if (group.size() == 1) {
            PendingSubscribeNotify one = group.get(0);
            return String.format("您关注的演出 \"%s\" 将于 %s 开售(%d 分钟后),请提前准备。",
                    head.getShowName(), one.getOpenSaleTime().format(HM), mins);
        }
        String times = group.stream()
                .map(p -> p.getOpenSaleTime().format(HM))
                .collect(Collectors.joining(" / "));
        return String.format("您关注的演出 \"%s\" 今日有 %d 场即将开售:%s,请提前准备。",
                head.getShowName(), group.size(), times);
    }

    private String buildOpenContent(PendingSubscribeNotify head, List<PendingSubscribeNotify> group) {
        if (group.size() == 1) {
            return String.format("您关注的演出 \"%s\" 现已开售,立即购票!", head.getShowName());
        }
        String times = group.stream()
                .map(p -> p.getOpenSaleTime().format(HM))
                .collect(Collectors.joining(" / "));
        return String.format("您关注的演出 \"%s\" 今日 %d 场已开售:%s,立即购票!",
                head.getShowName(), group.size(), times);
    }

    private record GroupKey(Long subscribeId, LocalDate date) {}
}
