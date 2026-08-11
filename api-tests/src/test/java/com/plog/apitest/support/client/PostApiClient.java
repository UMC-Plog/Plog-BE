package com.plog.apitest.support.client;

import io.restassured.response.Response;

public final class PostApiClient extends ApiClient {

    public PostApiClient(String baseUrl, String accessToken) {
        super(baseUrl, accessToken);
    }

    /** POST /api/projects/{projectId}/posts */
    public Response createPost(Object projectId, Object body) {
        return request().body(body).post("/api/projects/{projectId}/posts", projectId);
    }

    /** DELETE /api/projects/{projectId}/posts/{postId} */
    public Response deletePost(Object projectId, Object postId) {
        return request().delete("/api/projects/{projectId}/posts/{postId}", projectId, postId);
    }
}
