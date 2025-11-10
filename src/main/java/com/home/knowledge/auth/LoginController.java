package com.home.knowledge.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {

    private final UserRepository userRepository;

    public LoginController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        session.setAttribute("loginUser", id.trim());
        redirectAttributes.addFlashAttribute("error", null);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("loginUser");
        return "redirect:/";
    }
}
