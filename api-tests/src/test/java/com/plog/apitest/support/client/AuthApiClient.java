package com.plog.apitest.support.client;

import io.restassured.response.Response;

public final class AuthApiClient extends ApiClient {

    public AuthApiClient(String baseUrl) {
        super(baseUrl, null);
    }

    /** POST /api/auth/login */
    public Response login(Object body) {
        return request().body(body).post("/api/auth/login");
    }
}
