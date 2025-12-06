//package com.aiqa.project1.interceptor;
//
//import com.aiqa.project1.pojo.AuthInfo;
//import com.aiqa.project1.utils.JwtUtils;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.lang.Nullable;
//import org.springframework.web.servlet.HandlerInterceptor;
//import org.springframework.web.servlet.ModelAndView;
//
//public class DocumentIntercepotr implements HandlerInterceptor {
//    private static final Logger log = LoggerFactory.getLogger(UserInterceptor.class);
//
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        String token = request.getHeader("Authorization");
//        AuthInfo authInfo = JwtUtils.validateJwt(token);
//        request.setAttribute("authInfo", authInfo);
//
//        return true;
//    }
//
//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
//
//    }
//
//}
