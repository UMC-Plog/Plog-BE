package com.plog.apitest.support.client;

import io.restassured.response.Response;

public final class AvailabilityApiClient extends ApiClient {

    public AvailabilityApiClient(String baseUrl) {
        super(baseUrl, null);
    }

    /** GET /v3/api-docs */
    public Response health() {
        return request().get("/v3/api-docs");
    }
}
