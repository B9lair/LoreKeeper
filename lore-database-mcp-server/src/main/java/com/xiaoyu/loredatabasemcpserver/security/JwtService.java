package com.xiaoyu.loredatabasemcpserver.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.xiaoyu.loredatabasemcpserver.common.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * JWT 验签：只解析校验，不签发
 */
@Service
public class JwtService {

    private final String secret;

    public JwtService(@Value("${lorekeeper.jwt.secret}") String secret) {
        this.secret = secret;
    }

    /** 解析并校验主项目签发的 JWT，返回 tenant */
    public TenantContext.Tenant parse(String token) {
        JWT jwt = JWTUtil.parseToken(token);
        if (!jwt.setKey(secret.getBytes(StandardCharsets.UTF_8)).verify()) {
            throw new IllegalStateException("JWT 签名无效");
        }
        JWTValidator.of(jwt).validateDate(); // 校验 exp，过期抛异常

        Object userIdObj = jwt.getPayload("userId");
        Object workIdObj = jwt.getPayload("workId");
        if (!(userIdObj instanceof Number userId) || !(workIdObj instanceof Number workId)) {
            throw new IllegalStateException("JWT 缺少 userId/workId");
        }
        return new TenantContext.Tenant(userId.longValue(), workId.longValue());
    }
}
