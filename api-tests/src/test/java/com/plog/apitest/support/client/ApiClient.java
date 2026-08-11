package com.plog.apitest.support.client;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

import io.restassured.specification.RequestSpecification;

public abstract class ApiClient {

    private final String baseUrl;
    private final String accessToken;

    protected ApiClient(String baseUrl, String accessToken) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
    }

    protected RequestSpecification request() {
        RequestSpecification request = given().baseUri(baseUrl).contentType(JSON);
        return accessToken == null ? request : request.auth().oauth2(accessToken);
    }
}
