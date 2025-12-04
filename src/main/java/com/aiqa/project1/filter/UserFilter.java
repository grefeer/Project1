package com.aiqa.project1.filter;

import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.utils.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// "/*" 拦截所有请求

//@WebFilter(urlPatterns = "/*")
//public class UserFilter implements Filter {
//
//    private static final Logger log = LoggerFactory.getLogger(UserFilter.class);
//    @Override
//    public void init(FilterConfig filterConfig) {
//        log.info("UserFilter init");
//    }
//
//    @Override
//    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
//        log.info("拦截到了请求...");
//
//        HttpServletRequest request = (HttpServletRequest) servletRequest;
//        HttpServletResponse response =(HttpServletResponse) servletResponse;
//        // 获取uri
//        String RequestURI = request.getRequestURI();
//        // 判断是否是登录以及注册操作
//        if (RequestURI.contains("/login") || RequestURI.contains("/register")) {
//            filterChain.doFilter(servletRequest, servletResponse);
//            return;
//        }
//        //获取 token
//        String token = request.getHeader("Authorization").replace("Bearer ", "");
////        log.info(token);
//        // 无 token
//        if (token == null || token.isEmpty()) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }
//        // 有 token，校验工具
//        try {
//
//            AuthInfo authInfo = JwtUtils.validateJwt(token);
//            // 根据 authInfo 的role配置放访问权限
//        } catch (Exception e) {
//            log.info("令牌非法，请重新登录");
//            log.error(e.getMessage());
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }
//        filterChain.doFilter(servletRequest, servletResponse);
//    }
//
//    @Override
//    public void destroy() {
//        log.info("UserFilter destroy");
//        Filter.super.destroy();
//    }
//
//}
