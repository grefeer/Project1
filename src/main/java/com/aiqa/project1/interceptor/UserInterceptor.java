package com.aiqa.project1.interceptor;



import com.aiqa.project1.pojo.AuthInfo;
import com.aiqa.project1.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class UserInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(UserInterceptor.class);



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果是 OPTIONS 请求，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        //获取 token
        String token = request.getHeader("Authorization");
//        log.info(token);
        // 无 token
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 有 token，校验工具
        try {
            AuthInfo authInfo = JwtUtils.validateJwt(token);
            request.setAttribute("authInfo", authInfo);

        } catch (Exception e) {
            log.info("令牌非法，请重新登录");
            log.error(e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        return true;
    }
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        log.info("拦截器后处理");
    }
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
//    }

}
