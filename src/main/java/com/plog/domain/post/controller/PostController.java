package com.plog.domain.post.controller;

import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.service.PostService;
import com.plog.global.api.code.SuccessCode;
import com.plog.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<PostDto.Response>> createPost(
            @PathVariable Long projectId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody PostDto.CreateRequest request
    ) {
        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.CREATED, postService.createPost(projectId, userId, request)));
    }

    @GetMapping
    public ApiResponse<PostDto.FeedResponse> getFeed(
            @PathVariable Long projectId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(postService.getFeed(projectId, userId, cursor, size));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDto.Response> getPost(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ApiResponse.success(postService.getPost(projectId, postId, userId));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostDto.Response> updatePost(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody PostDto.UpdateRequest request
    ) {
        return ApiResponse.success(postService.updatePost(projectId, postId, userId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<PostDto.DeletedResponse> deletePost(
            @PathVariable Long projectId,
            @PathVariable Long postId,
            @AuthenticationPrincipal(expression = "userId") Long userId
    ) {
        return ApiResponse.success(postService.deletePost(projectId, postId, userId));
    }

}
