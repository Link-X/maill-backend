package com.ticket.core.scheduler;

import com.ticket.core.domain.entity.ShowSubscribe;
import com.ticket.core.mapper.ShowSubscribeMapper;
import com.ticket.core.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开售提醒定时任务:每分钟扫描需要推送的订阅,产生站内信,并标记已推送。
 */
@Slf4j
@Component
public class SubscribeNotifier {

    private final ShowSubscribeMapper mapper;
    private final MessageService messageService;

    public SubscribeNotifier(ShowSubscribeMapper mapper, MessageService messageService) {
        this.mapper = mapper;
        this.messageService = messageService;
    }

    // 不加 @Transactional：外层事务会让 sendSingle(REQUIRED) 加入外层；
    // 一旦循环中 catch 吞掉异常，外层已被 Spring 标 rollback-only，
    // 提交时抛 UnexpectedRollbackException，会导致已成功的 markNotifiedPre 全部回滚 → 重复推送。
    // 让每条订阅各自独立：sendSingle 走自己的事务，markNotifiedPre 是单 UPDATE。
    @Scheduled(cron = "0 * * * * ?")
    public void scanPre() {
        LocalDateTime now = LocalDateTime.now();
        List<ShowSubscribe> pending = mapper.selectPendingPre(now);
        for (ShowSubscribe s : pending) {
            try {
                String showName = s.getShow() != null ? s.getShow().getName() : "演出";
                Integer mins = s.getNotifyBeforeMinutes() == null ? 10 : s.getNotifyBeforeMinutes();
                messageService.sendSingle(
                        s.getUserId(),
                        MessageService.TYPE_OPEN_SALE,
                        "[开售提醒] " + showName + " 即将开售",
                        "您关注的演出 \"" + showName + "\" 将在 " + mins + " 分钟后开售,请提前准备。",
                        MessageService.LINK_SHOW,
                        String.valueOf(s.getShowId())
                );
                mapper.markNotifiedPre(s.getId());
            } catch (Exception e) {
                log.warn("[SUBSCRIBE-PRE] send fail subId={} err={}", s.getId(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) log.info("[SUBSCRIBE-PRE] processed {} pre-notifications", pending.size());
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanOpen() {
        LocalDateTime now = LocalDateTime.now();
        List<ShowSubscribe> pending = mapper.selectPendingOpen(now);
        for (ShowSubscribe s : pending) {
            try {
                String showName = s.getShow() != null ? s.getShow().getName() : "演出";
                messageService.sendSingle(
                        s.getUserId(),
                        MessageService.TYPE_OPEN_SALE,
                        "[已开售] " + showName,
                        "您关注的演出 \"" + showName + "\" 现已开售,立即购票!",
                        MessageService.LINK_SHOW,
                        String.valueOf(s.getShowId())
                );
                mapper.markNotifiedOpen(s.getId());
            } catch (Exception e) {
                log.warn("[SUBSCRIBE-OPEN] send fail subId={} err={}", s.getId(), e.getMessage());
            }
        }
        if (!pending.isEmpty()) log.info("[SUBSCRIBE-OPEN] processed {} open-notifications", pending.size());
    }
}
