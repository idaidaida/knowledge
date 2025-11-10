package com.home.knowledge.post;

import com.home.knowledge.comment.CommentRepository;
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

    public PostController(PostRepository repository, CommentRepository commentRepository) {
        this.repository = repository;
        this.commentRepository = commentRepository;
    }

    @GetMapping("/")
    public String timeline(Model model) {
        var posts = repository.findAll();
        model.addAttribute("posts", posts);
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
        commentRepository.save(postId, loginUser.trim(), content.trim());
        return "redirect:/posts/" + postId;
    }

    @GetMapping("/posts/{id}")
    public String detail(@PathVariable long id, Model model, RedirectAttributes redirectAttributes) {
        var opt = repository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Post not found");
            return "redirect:/";
        }
        var post = opt.get();
        model.addAttribute("post", post);
        model.addAttribute("comments", commentRepository.findByPostId(id));
        return "post_detail";
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

