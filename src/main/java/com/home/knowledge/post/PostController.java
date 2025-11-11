package com.home.knowledge.post;

import com.home.knowledge.comment.CommentRepository;
import com.home.knowledge.like.LikeRepository;
import com.home.knowledge.notify.NotificationRepository;
import com.home.knowledge.read.ReadRepository;
import com.home.knowledge.summary.ArticleAiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class PostController {

    private final PostRepository repository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final ReadRepository readRepository;
    private final NotificationRepository notificationRepository;
    private final ArticleAiService summaryService;

    public PostController(PostRepository repository,
                          CommentRepository commentRepository,
                          LikeRepository likeRepository,
                          ReadRepository readRepository,
                          NotificationRepository notificationRepository,
                          ArticleAiService summaryService) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.readRepository = readRepository;
        this.notificationRepository = notificationRepository;
        this.summaryService = summaryService;
    }

    @GetMapping("/")
    public String timeline(@org.springframework.web.bind.annotation.RequestParam(name = "unread", required = false) Boolean unread,
                           jakarta.servlet.http.HttpSession session,
                           Model model) {
        var posts = repository.findAll();
        String user = (String) session.getAttribute("loginUser");
        Set<Long> readIds = Set.of();
        if (StringUtils.hasText(user)) {
            readIds = readRepository.findReadPostIds(user);
        }
        if (Boolean.TRUE.equals(unread)) {
            var readIdSet = readIds;
            posts.removeIf(p -> readIdSet.contains(p.getId()));
            model.addAttribute("unreadFilter", true);
        } else {
            model.addAttribute("unreadFilter", false);
        }
        model.addAttribute("posts", posts);
        model.addAttribute("readPostIds", readIds);
        Map<Long, Integer> likeCounts = new HashMap<>();
        Map<Long, List<String>> readersByPost = new HashMap<>();
        posts.forEach(p -> {
            likeCounts.put(p.getId(), likeRepository.countByPostId(p.getId()));
            readersByPost.put(p.getId(), readRepository.findReadersByPostId(p.getId()));
        });
        model.addAttribute("likeCounts", likeCounts);
        model.addAttribute("readersByPost", readersByPost);
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
            @RequestParam(name = "linkUrl") String linkUrl,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        String loginUser = (String) session.getAttribute("loginUser");
        if (!StringUtils.hasText(loginUser)) {
            redirectAttributes.addFlashAttribute("error", "投稿するにはログインが必要です");
            return "redirect:/login";
        }
        if (!StringUtils.hasText(linkUrl)) {
            redirectAttributes.addFlashAttribute("error", "ニュースURLは必須です");
            return "redirect:/posts/new";
        }
        String trimmedLink = linkUrl.trim();
        ArticleAiService.ArticleDraft draft = summaryService.buildDraft(trimmedLink);
        var post = repository.save(
                loginUser.trim(),
                draft.title(),
                draft.content(),
                null,
                trimmedLink,
                draft.summary()
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
        var comments = commentRepository.findByPostId(id);
        model.addAttribute("post", post);
        model.addAttribute("comments", comments);
        String loginUser = (String) session.getAttribute("loginUser");
        model.addAttribute("likeCount", likeRepository.countByPostId(id));
        model.addAttribute("likedByMe", (loginUser != null && likeRepository.likedByUser(id, loginUser)));
        boolean isRead = false;
        if (StringUtils.hasText(loginUser)) {
            readRepository.markRead(id, loginUser.trim());
            notificationRepository.markSeen(loginUser.trim(), "POST", id);
            var commentIds = comments.stream()
                    .map(c -> c.getId())
                    .collect(java.util.stream.Collectors.toSet());
            notificationRepository.markCommentsSeen(loginUser.trim(), commentIds);
            isRead = true;
        }
        var readers = readRepository.findReadersByPostId(id);
        model.addAttribute("readers", readers);
        model.addAttribute("readersCount", readers.size());
        model.addAttribute("isRead", isRead);
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
            Set<Long> newPostIds = new HashSet<>();
            Set<Long> newCommentPosts = new HashSet<>();
            for (var row : list) {
                if ("POST".equals(row.kind)) {
                    newPostIds.add(row.refId);
                } else if ("COMMENT".equals(row.kind) && row.postId != null) {
                    newCommentPosts.add(row.postId);
                }
            }
            model.addAttribute("newPostIds", newPostIds);
            model.addAttribute("newCommentPosts", newCommentPosts);
        } else {
            model.addAttribute("notificationCount", 0);
            model.addAttribute("notifications", java.util.List.of());
            model.addAttribute("newPostIds", Set.of());
            model.addAttribute("newCommentPosts", Set.of());
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
