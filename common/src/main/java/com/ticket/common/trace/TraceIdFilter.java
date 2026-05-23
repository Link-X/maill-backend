package com.ticket.common.trace;

import com.ticket.common.result.Result;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 Filter：
 * - 进入请求时，从 Header `X-Trace-Id` 读取 traceId（外部调用方可指定）；没有就用 UUID 生成
 * - 写入 MDC（key 为 {@link Result#TRACE_ID_KEY}），所有 logback %X{traceId} 能拿到
 * - 通过响应头 `X-Trace-Id` 回写给客户端，客户端报错可以直接贴这个 ID
 * - 请求结束清理 MDC，防止线程池场景下污染下一次请求
 */
@Configuration
public class TraceIdFilter {

    public static final String HEADER = "X-Trace-Id";

    @Bean
    public FilterRegistrationBean<Filter> traceIdFilterRegistration() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter((request, response, chain) -> doFilter(request, response, chain));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    private void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String traceId = null;
        if (req instanceof HttpServletRequest) {
            traceId = ((HttpServletRequest) req).getHeader(HEADER);
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(Result.TRACE_ID_KEY, traceId);
        if (resp instanceof HttpServletResponse) {
            ((HttpServletResponse) resp).setHeader(HEADER, traceId);
        }
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(Result.TRACE_ID_KEY);
        }
    }
}
