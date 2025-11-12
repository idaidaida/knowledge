package com.home.knowledge.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Object user = (session != null) ? session.getAttribute("loginUser") : null;
        if (user == null) {
            String cookieUser = extractLoginCookie(request.getCookies());
            if (cookieUser != null) {
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("loginUser", cookieUser);
                return true;
            }
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }

    private String extractLoginCookie(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("isLogin".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
