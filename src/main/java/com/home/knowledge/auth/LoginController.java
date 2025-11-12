package com.home.knowledge.auth;

import com.home.knowledge.notify.NotificationRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Timestamp;
import java.time.Instant;

@Controller
public class LoginController {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public LoginController(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/login")
    public String loginForm(jakarta.servlet.http.HttpSession session) {
        Object user = session.getAttribute("loginUser");
        if (user != null) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(
            @RequestParam("id") String id,
            @RequestParam("password") String password,
            HttpSession session,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(password)) {
            model.addAttribute("error", "ID と パスワードを入力してください");
            return "login";
        }
        if (!userRepository.validate(id.trim(), password)) {
            model.addAttribute("error", "ID または パスワードが正しくありません");
            return "login";
        }
        String uid = id.trim();
        session.setAttribute("loginUser", uid);
        Cookie cookie = new Cookie("isLogin", uid);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 30);
        response.addCookie(cookie);
        notificationRepository.upsertLastSeen(uid, Timestamp.from(Instant.now()));
        redirectAttributes.addFlashAttribute("error", null);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        session.removeAttribute("loginUser");
        Cookie cookie = new Cookie("isLogin", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/";
    }
}
