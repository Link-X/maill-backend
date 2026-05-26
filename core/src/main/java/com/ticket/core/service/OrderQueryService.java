package com.ticket.core.service;

import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.entity.City;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.CityMapper;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import com.ticket.core.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单查询服务：只读路径。
 *
 * 与 {@link OrderCommandService} 配对：那边管下单/取消/退款这些写入与状态机，
 * 这边只负责把订单查出来并装配成 OrderStatusResponse（含 show / session / city / tickets）。
 *
 * 批量预取（items / sessions / shows / cities / tickets）抽在 {@link #assembleStatusResponses}，
 * 用户列表与管理端列表共用，避免 N+1。
 */
@Service
public class OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShowMapper showMapper;
    private final ShowSessionMapper showSessionMapper;
    private final TicketMapper ticketMapper;
    private final CityMapper cityMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public OrderQueryService(OrderMapper orderMapper,
                             OrderItemMapper orderItemMapper,
                             ShowMapper showMapper,
                             ShowSessionMapper showSessionMapper,
                             TicketMapper ticketMapper,
                             CityMapper cityMapper,
                             org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.showMapper = showMapper;
        this.showSessionMapper = showSessionMapper;
        this.ticketMapper = ticketMapper;
        this.cityMapper = cityMapper;
        this.redisTemplate = redisTemplate;
    }

    public Order getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    /**
     * 异步建单状态查询:
     *  1. DB 找到订单 → SUCCESS + 订单详情
     *  2. Redis pending = FAILED:reason → FAILED + reason
     *  3. Redis pending = PROCESSING → PROCESSING
     *  4. 都没有 → NOT_FOUND(可能 60s TTL 过期了)
     */
    public com.ticket.core.domain.dto.OrderCreateStatus getCreateStatus(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            return com.ticket.core.domain.dto.OrderCreateStatus.success(buildStatusResponse(order));
        }
        String pending = redisTemplate.opsForValue()
                .get(com.ticket.common.constant.RedisKeys.orderCreatePending(orderNo));
        if (pending == null) {
            return com.ticket.core.domain.dto.OrderCreateStatus.notFound();
        }
        if (pending.startsWith("FAILED:")) {
            return com.ticket.core.domain.dto.OrderCreateStatus.failed(pending.substring("FAILED:".length()));
        }
        return com.ticket.core.domain.dto.OrderCreateStatus.processing();
    }

    public Order getById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }

    public List<OrderStatusResponse> getUserOrders(Long userId, int page, int size,
                                                   Integer status,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectByUserId(userId, offset, size, status, startTime, endTime);
        return assembleStatusResponses(orders);
    }

    public int countUserOrders(Long userId, Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        return orderMapper.countByUserId(userId, status, startTime, endTime);
    }

    public List<OrderStatusResponse> getAdminOrders(int page, int size,
                                                    Long showId, Long sessionId, String orderNo,
                                                    Integer status,
                                                    LocalDateTime startTime, LocalDateTime endTime) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectByAdminCondition(
                showId, sessionId, orderNo, status, startTime, endTime, offset, size);
        return assembleStatusResponses(orders);
    }

    public int countAdminOrders(Long showId, Long sessionId, String orderNo,
                                Integer status,
                                LocalDateTime startTime, LocalDateTime endTime) {
        return orderMapper.countByAdminCondition(showId, sessionId, orderNo, status, startTime, endTime);
    }

    /** 单条订单的 status response —— 内部走批量装配以保持代码单一来源 */
    public OrderStatusResponse buildStatusResponse(Order order) {
        List<OrderStatusResponse> list = assembleStatusResponses(List.of(order));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 把一组 Order 装配成 OrderStatusResponse 列表。
     * 一次批量查询 items / sessions / shows / cities / tickets，避免 N+1。
     */
    private List<OrderStatusResponse> assembleStatusResponses(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        List<Long> sessionIds = orders.stream().map(Order::getSessionId).distinct().collect(Collectors.toList());

        Map<Long, List<OrderItem>> itemsByOrderId = orderItemMapper.selectByOrderIds(orderIds)
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<ShowSession> sessions = showSessionMapper.selectByIds(sessionIds);
        Map<Long, ShowSession> sessionsById = sessions.stream()
                .collect(Collectors.toMap(ShowSession::getId, s -> s));

        List<Long> showIds = sessions.stream().map(ShowSession::getShowId).distinct().collect(Collectors.toList());
        Map<Long, Show> showsById = showIds.isEmpty() ? Map.of()
                : showMapper.selectByIds(showIds).stream().collect(Collectors.toMap(Show::getId, s -> s));

        List<String> cityCodes = showsById.values().stream()
                .map(Show::getCityCode)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> cityNameByCode = cityCodes.isEmpty() ? Map.of()
                : cityMapper.selectByCodes(cityCodes).stream()
                    .collect(Collectors.toMap(City::getCode, City::getName));

        Map<Long, List<Ticket>> ticketsByOrderId = ticketMapper.selectByOrderIds(orderIds)
                .stream().collect(Collectors.groupingBy(Ticket::getOrderId));

        return orders.stream()
                .map(o -> buildOne(o, itemsByOrderId, sessionsById, showsById, ticketsByOrderId, cityNameByCode))
                .collect(Collectors.toList());
    }

    private OrderStatusResponse buildOne(
            Order order,
            Map<Long, List<OrderItem>> itemsByOrderId,
            Map<Long, ShowSession> sessionsById,
            Map<Long, Show> showsById,
            Map<Long, List<Ticket>> ticketsByOrderId,
            Map<String, String> cityNameByCode) {

        List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
        ShowSession session = sessionsById.get(order.getSessionId());
        Show show = session != null ? showsById.get(session.getShowId()) : null;
        List<Ticket> tickets = ticketsByOrderId.getOrDefault(order.getId(), List.of());

        OrderStatusResponse resp = new OrderStatusResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreateTime(order.getCreateTime());
        resp.setPayTime(order.getPayTime());
        resp.setExpireTime(order.getExpireTime());
        resp.setSeatInfos(items.stream().map(OrderItem::getSeatInfo).collect(Collectors.toList()));
        if (show != null) {
            resp.setShowName(show.getName());
            resp.setShowVenue(show.getVenue());
            resp.setShowCityCode(show.getCityCode());
            resp.setShowAddress(show.getAddress());
            if (show.getCityCode() != null) {
                resp.setShowCityName(cityNameByCode.get(show.getCityCode()));
            }
        }
        if (session != null) {
            resp.setSessionName(session.getName());
            resp.setSessionStartTime(session.getStartTime());
        }
        resp.setTickets(tickets.stream().map(t -> {
            OrderStatusResponse.TicketInfo info = new OrderStatusResponse.TicketInfo();
            info.setTicketNo(t.getTicketNo());
            info.setQrCode(t.getQrCode());
            info.setStatus(t.getStatus());
            info.setVerifyTime(t.getVerifyTime());
            return info;
        }).collect(Collectors.toList()));

        return resp;
    }
}
