package com.ticket.payment.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.payment.gateway.PaymentGateway;
import com.ticket.payment.gateway.PaymentGatewayFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 支付服务 — 创建支付记录、调用网关、更新订单状态.
 *
 * 采用分阶段事务,避免长事务占用 DB 连接：
 *   1. 事务内:校验订单 + insert payment(支付中)
 *   2. 事务外:调用网关(远程 I/O)
 *   3. 事务内:根据结果原子更新 payment + order 状态
 *
 * 使用 TransactionTemplate 编程式事务,避免同类自调用导致 @Transactional 失效。
 */
@Slf4j
@Service
public class PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayFactory gatewayFactory;
    private final SnowflakeIdGenerator snowflake;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(OrderMapper orderMapper,
                          PaymentMapper paymentMapper,
                          PaymentGatewayFactory gatewayFactory,
                          SnowflakeIdGenerator snowflake,
                          PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.gatewayFactory = gatewayFactory;
        this.snowflake = snowflake;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 处理支付
     *
     * @param orderId 订单 ID
     * @param channel 支付渠道（如 "mock"、"alipay"）
     * @return 支付记录
     */
    public Payment processPayment(Long orderId, String channel) {
        // 阶段 1:事务内创建支付记录
        Payment payment = transactionTemplate.execute(status -> {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
            }
            if (order.getStatus() != 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许支付");
            }
            Payment p = new Payment();
            p.setId(snowflake.nextId());
            p.setOrderId(orderId);
            p.setPaymentNo(String.valueOf(snowflake.nextId()));
            p.setChannel(channel != null ? channel : "mock");
            p.setAmount(order.getTotalAmount());
            p.setStatus(0); // 支付中
            paymentMapper.insert(p);
            return p;
        });

        // 阶段 2:事务外调用网关(可能耗时,不持有 DB 连接)
        PaymentGateway gateway = gatewayFactory.getGateway(payment.getChannel());
        boolean success;
        try {
            success = gateway.pay(payment.getPaymentNo(), payment.getAmount());
        } catch (Exception e) {
            log.error("支付网关调用异常,orderId={} paymentNo={}", orderId, payment.getPaymentNo(), e);
            markPaymentFailedInTx(payment);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付网关异常,请稍后重试");
        }

        // 阶段 3:事务内根据结果原子更新 payment + order 状态
        if (success) {
            return transactionTemplate.execute(status -> {
                LocalDateTime now = LocalDateTime.now();
                int affected = orderMapper.updateStatusAndPayTime(orderId, 1, now);
                if (affected == 0) {
                    // 订单不在待支付状态(并发取消或超时),把 payment 也标记为失败,保证两表一致
                    log.warn("支付成功但订单已取消,orderId={} paymentNo={}", orderId, payment.getPaymentNo());
                    paymentMapper.updateStatus(payment.getId(), 2);
                    payment.setStatus(2);
                    return payment;
                }
                paymentMapper.updateStatus(payment.getId(), 1);
                payment.setStatus(1);
                payment.setTradeNo("TRADE-" + System.currentTimeMillis());
                payment.setCallbackTime(now);
                log.info("支付成功 orderId={} paymentNo={}", orderId, payment.getPaymentNo());
                return payment;
            });
        } else {
            markPaymentFailedInTx(payment);
            log.warn("支付失败 orderId={} paymentNo={}", orderId, payment.getPaymentNo());
            return payment;
        }
    }

    private void markPaymentFailedInTx(Payment payment) {
        transactionTemplate.executeWithoutResult(status -> {
            paymentMapper.updateStatus(payment.getId(), 2);
            payment.setStatus(2);
        });
    }
}
