package com.home.knowledge.post;

import com.home.knowledge.comment.CommentRepository;
import com.home.knowledge.like.LikeRepository;
import com.home.knowledge.notify.NotificationRepository;
import com.home.knowledge.read.ReadRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PostController {

    private final PostRepository repository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final ReadRepository readRepository;
    private final NotificationRepository notificationRepository;

    public PostController(PostRepository repository,
                          CommentRepository commentRepository,
                          LikeRepository likeRepository,
                          ReadRepository readRepository,
                          NotificationRepository notificationRepository) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.readRepository = readRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/")
    public String timeline(@org.springframework.web.bind.annotation.RequestParam(name = "unread", required = false) Boolean unread,
                           jakarta.servlet.http.HttpSession session,
                           Model model) {
        var posts = repository.findAll();
        if (Boolean.TRUE.equals(unread)) {
            String user = (String) session.getAttribute("loginUser");
            if (StringUtils.hasText(user)) {
                var readIds = readRepository.findReadPostIds(user);
                posts.removeIf(p -> readIds.contains(p.getId()));
            }
            model.addAttribute("unreadFilter", true);
        } else {
            model.addAttribute("unreadFilter", false);
        }
        model.addAttribute("posts", posts);
        addNotificationsToModel(session, model);
        model.addAttribute("filterUser", null);
        return "timeline";
    }

    @GetMapping("/users/{username}")
    public String userTimeline(@PathVariable String username, Model model) {
        var posts = repository.findByUsername(username);
        model.addAttribute("posts", posts);
        model.addAttribute("filterUser", username);
        return "timeline";
    }

    @PostMapping("/posts")
    public String create(
            @RequestParam(required = false) String title,
            @RequestParam String content,
            @RequestParam(name = "linkUrl") String linkUrl,
            @RequestParam(required = false, name = "imageUrl") String imageUrl,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (!StringUtils.hasText(loginUser)) {
            redirectAttributes.addFlashAttribute("error", "投稿するにはログインが必要です");
            return "redirect:/login";
        }
        if (!StringUtils.hasText(content) || !StringUtils.hasText(linkUrl)) {
            redirectAttributes.addFlashAttribute("error", "本文とニュースURLは必須です");
            return "redirect:/posts/new";
        }
        var post = repository.save(
                loginUser.trim(),
                (title != null ? title.trim() : null),
                content.trim(),
                (imageUrl != null ? imageUrl.trim() : null),
                linkUrl.trim()
        );
        notificationRepository.markSeen(loginUser.trim(), "POST", post.getId());
        return "redirect:/posts/" + post.getId();
    }

    @PostMapping("/comments")
    public String addComment(
            @RequestParam long postId,
            @RequestParam String content,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (!StringUtils.hasText(loginUser)) {
            redirectAttributes.addFlashAttribute("error", "コメントするにはログインが必要です");
            return "redirect:/login";
        }
        if (postId <= 0 || !StringUtils.hasText(content)) {
            redirectAttributes.addFlashAttribute("error", "コメント内容を入力してください");
            return "redirect:/posts/" + postId;
        }
        var comment = commentRepository.save(postId, loginUser.trim(), content.trim());
        notificationRepository.markSeen(loginUser.trim(), "COMMENT", comment.getId());
        return "redirect:/posts/" + postId;
    }

    @GetMapping("/posts/{id}")
    public String detail(@PathVariable long id, Model model, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        var post = opt.get();
        model.addAttribute("post", post);
        model.addAttribute("comments", commentRepository.findByPostId(id));
        String loginUser = (String) session.getAttribute("loginUser");
        model.addAttribute("likeCount", likeRepository.countByPostId(id));
        model.addAttribute("likedByMe", (loginUser != null && likeRepository.likedByUser(id, loginUser)));
        addNotificationsToModel(session, model);
        return "post_detail";
    }

    @GetMapping("/posts/{id}/go")
    public String goToLink(@PathVariable long id, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        String user = (String) session.getAttribute("loginUser");
        if (org.springframework.util.StringUtils.hasText(user)) {
            readRepository.markRead(id, user.trim());
        }
        String link = opt.get().getLinkUrl();
        if (!org.springframework.util.StringUtils.hasText(link)) {
            return "redirect:/posts/" + id;
        }
        return "redirect:" + link;
    }

    @org.springframework.web.bind.annotation.PostMapping("/notifications/seen")
    public String markNotificationsSeen(jakarta.servlet.http.HttpSession session, jakarta.servlet.http.HttpServletRequest request) {
        String user = (String) session.getAttribute("loginUser");
        if (org.springframework.util.StringUtils.hasText(user)) {
            notificationRepository.upsertLastSeen(user, new java.sql.Timestamp(System.currentTimeMillis()));
        }
        String ref = request.getHeader("Referer");
        return "redirect:" + (ref != null ? ref : "/");
    }

    private void addNotificationsToModel(jakarta.servlet.http.HttpSession session, Model model) {
        String user = (String) session.getAttribute("loginUser");
        if (org.springframework.util.StringUtils.hasText(user)) {
            int count = notificationRepository.countUnread(user);
            var list = notificationRepository.listUnread(user, 10);
            model.addAttribute("notificationCount", count);
            model.addAttribute("notifications", list);
        } else {
            model.addAttribute("notificationCount", 0);
            model.addAttribute("notifications", java.util.List.of());
        }
    }

    @GetMapping("/posts/new")
    public String newPost(Model model) {
        return "post_new";
    }

    @GetMapping("/posts/{id}/edit")
    public String editPost(@PathVariable long id, jakarta.servlet.http.HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        var post = opt.get();
        String user = (String) session.getAttribute("loginUser");
        if (!post.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "編集権限がありません");
            return "redirect:/posts/" + id;
        }
        model.addAttribute("post", post);
        return "post_edit";
    }

    @PostMapping("/posts/{id}/edit")
    public String updatePost(
            @PathVariable long id,
            @RequestParam(required = false) String title,
            @RequestParam String content,
            @RequestParam String linkUrl,
            @RequestParam(required = false, name = "imageUrl") String imageUrl,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        var post = opt.get();
        String user = (String) session.getAttribute("loginUser");
        if (!post.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "編集権限がありません");
            return "redirect:/posts/" + id;
        }
        if (!StringUtils.hasText(content) || !StringUtils.hasText(linkUrl)) {
            redirectAttributes.addFlashAttribute("error", "本文とニュースURLは必須です");
            return "redirect:/posts/" + id + "/edit";
        }
        repository.update(id, (title != null ? title.trim() : null), content.trim(), (imageUrl != null ? imageUrl.trim() : null), linkUrl.trim());
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/delete")
    public String deletePost(@PathVariable long id, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        var post = opt.get();
        String user = (String) session.getAttribute("loginUser");
        if (!post.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "削除権限がありません");
            return "redirect:/posts/" + id;
        }
        repository.delete(id);
        return "redirect:/";
    }

    @PostMapping("/posts/{id}/like")
    public String like(@PathVariable long id, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        String user = (String) session.getAttribute("loginUser");
        if (!org.springframework.util.StringUtils.hasText(user)) {
            redirectAttributes.addFlashAttribute("error", "ログインが必要です");
            return "redirect:/login";
        }
        likeRepository.like(id, user.trim());
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/unlike")
    public String unlike(@PathVariable long id, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        String user = (String) session.getAttribute("loginUser");
        if (!org.springframework.util.StringUtils.hasText(user)) {
            redirectAttributes.addFlashAttribute("error", "ログインが必要です");
            return "redirect:/login";
        }
        likeRepository.unlike(id, user.trim());
        return "redirect:/posts/" + id;
    }

    @GetMapping("/comments/{id}/edit")
    public String editComment(@PathVariable long id, jakarta.servlet.http.HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        var c = commentRepository.findById(id);
        if (c == null) {
            redirectAttributes.addFlashAttribute("error", "Comment not found");
            return "redirect:/";
        }
        String user = (String) session.getAttribute("loginUser");
        if (!c.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "編集権限がありません");
            return "redirect:/posts/" + c.getPostId();
        }
        model.addAttribute("comment", c);
        return "comment_edit";
    }

    @PostMapping("/comments/{id}/edit")
    public String updateComment(@PathVariable long id, @RequestParam String content, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        var c = commentRepository.findById(id);
        if (c == null) {
            redirectAttributes.addFlashAttribute("error", "Comment not found");
            return "redirect:/";
        }
        String user = (String) session.getAttribute("loginUser");
        if (!c.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "編集権限がありません");
            return "redirect:/posts/" + c.getPostId();
        }
        if (!StringUtils.hasText(content)) {
            redirectAttributes.addFlashAttribute("error", "コメント内容を入力してください");
            return "redirect:/comments/" + id + "/edit";
        }
        commentRepository.updateContent(id, content.trim());
        return "redirect:/posts/" + c.getPostId();
    }

    @PostMapping("/comments/{id}/delete")
    public String deleteComment(@PathVariable long id, jakarta.servlet.http.HttpSession session, RedirectAttributes redirectAttributes) {
        var c = commentRepository.findById(id);
        if (c == null) {
            redirectAttributes.addFlashAttribute("error", "Comment not found");
            return "redirect:/";
        }
        String user = (String) session.getAttribute("loginUser");
        if (!c.getUsername().equals(user)) {
            redirectAttributes.addFlashAttribute("error", "削除権限がありません");
            return "redirect:/posts/" + c.getPostId();
        }
        commentRepository.delete(id);
        return "redirect:/posts/" + c.getPostId();
    }
}
