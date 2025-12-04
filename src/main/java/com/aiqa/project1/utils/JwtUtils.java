package com.aiqa.project1.utils;
import com.aiqa.project1.pojo.AuthInfo;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;



public class JwtUtils {

    private static Environment environment;

    private static SecretKey SECRET_KEY = null;
    private static long EXPIRATION_TIME = 0;

    public static void setEnvironment(Environment environment) {
        JwtUtils.environment = environment;
        String base64SecretKey = environment.getProperty("system.jwt.secret-key");
        SECRET_KEY = Keys.hmacShaKeyFor(base64SecretKey.getBytes());
        EXPIRATION_TIME = Long.parseLong(environment.getProperty("system.jwt.expiration", "7200000"));
    }

    public static String GenerateJwt(Map<String, Object> map, String subject) {
        Date now = new Date();
        return Jwts.builder().signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .setClaims(map != null ? map : Map.of())                // 设置主要内容
                .setSubject(subject)                                    // 设置主题（用户名）
                .setIssuedAt(now)                                // 设置开始时间
                .setExpiration(new Date(now.getTime()
                        + EXPIRATION_TIME))                            //设置过期时间
                .compact();
    }


    private static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = ParseJwt(token);
        System.out.println(claims);
        return claimsResolver.apply(claims);
    }

    public static Claims ParseJwt(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(jwt.replace("Bearer ", ""))
                .getBody();
    }

    public static AuthInfo validateJwt(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        Claims claims = ParseJwt(jwt);
        String username = claims.get("username", String.class);
        String password = claims.get("password", String.class);
        String role_jwt = claims.get("role", String.class);
        if (role_jwt == null || role_jwt.isEmpty()) {
            return null;
        }
        return new AuthInfo(username, password, role_jwt);
    }

    public static String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

}
