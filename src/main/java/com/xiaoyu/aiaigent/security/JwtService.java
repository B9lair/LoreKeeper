package com.xiaoyu.aiaigent.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.xiaoyu.aiaigent.common.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 */
@Service
public class JwtService {

    private final String secret;
    private final long expireMs;

    public JwtService(@Value("${lorekeeper.jwt.secret}") String secret,
                      @Value("${lorekeeper.jwt.expire-ms:604800000}") long expireMs) {
        this.secret = secret;
        this.expireMs = expireMs;
    }

    /** 签发含 userId / workId 的 JWT */
    public String issue(long userId, long workId) {
        return JWT.create()
                .setPayload("userId", userId)
                .setPayload("workId", workId)
                .setExpiresAt(new Date(System.currentTimeMillis() + expireMs))
                .setKey(secret.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /** 解析并校验 JWT，返回 tenant */
    public TenantContext.Tenant parse(String token) {
        JWT jwt = JWTUtil.parseToken(token);
        if (!jwt.setKey(secret.getBytes(StandardCharsets.UTF_8)).verify()) {
            throw new IllegalStateException("JWT 签名无效");
        }
        // 校验有效期（exp），过期抛异常
        JWTValidator.of(jwt).validateDate();

        Object userIdObj = jwt.getPayload("userId");
        Object workIdObj = jwt.getPayload("workId");
        if (!(userIdObj instanceof Number userId) || !(workIdObj instanceof Number workId)) {
            throw new IllegalStateException("JWT 缺少 userId/workId");
        }
        return new TenantContext.Tenant(userId.longValue(), workId.longValue());
    }
}
