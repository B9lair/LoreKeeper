package com.xiaoyu.lorenotifymcpserver.common;

import com.xiaoyu.lorenotifymcpserver.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 SSE 连接请求头 Authorization: Bearer <JWT> 解析 tenant 塞入 TenantContext
 * Spring Boot 会自动注册 OncePerRequestFilter bean，无需额外 security 依赖
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public TenantFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少认证 token\"}");
            return;
        }
        try {
            TenantContext.set(jwtService.parse(auth.substring(7)));
        }
        catch (Exception e) {
            // 无效/过期 token：直接拒绝（连接级鉴权，堵住端口裸奔）
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"token 无效或已过期\"}");
            return;
        }
        try {
            chain.doFilter(request, response);
        }
        finally {
            TenantContext.clear();
        }
    }
}
