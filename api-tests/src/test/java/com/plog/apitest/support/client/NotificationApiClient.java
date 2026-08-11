package com.plog.apitest.support.client;

import io.restassured.response.Response;

public final class NotificationApiClient extends ApiClient {

    public NotificationApiClient(String baseUrl, String accessToken) {
        super(baseUrl, accessToken);
    }

    /** GET /api/notifications */
    public Response getNotifications(Object page, Object size) {
        return request()
                .queryParam("page", page)
                .queryParam("size", size)
                .get("/api/notifications");
    }
}
