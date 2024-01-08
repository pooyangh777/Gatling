package com.example.gatling;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.status;
import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.core.ScenarioBuilder;

import java.time.Duration;
import java.util.Random;

import static io.gatling.javaapi.core.CoreDsl.*;

public class SimpleGatlingSimulation extends Simulation {

    private static final int DEVICE_UID_LENGTH = 5;

    public String generateRandomId(int length) {
        Random random = new Random();
        int randomNumber = random.nextInt(100000);
        return String.format("%0" + length + "d", randomNumber);
    }

    HttpProtocolBuilder httpProtocol = http.baseUrl("http://localhost:8080")
            .header("Content-Type", "application/json")
            .header("Accept-Encoding", "gzip");

    {
        ScenarioBuilder scn = scenario("otp test")
                .exec(session -> {
                    String randomId = generateRandomId(DEVICE_UID_LENGTH);
                    return session.set("random", randomId);
                })
                .exec(http("handshake")
                        .post("/api/oauth2/otp/handshake")
                        .queryParam("deviceUID", "${random}")
                        .queryParam("deviceOs", "android")
                        .queryParam("deviceOsVersion", "7.7")
                        .queryParam("deviceType", "MOBILE_PHONE")
                        .queryParam("deviceName", "zxc")
                        .check(status().is(200), jsonPath("$.keyId").saveAs("keyId")))
                .exec(http("authorize")
                        .post("/api/oauth2/otp/authorize")
                        .queryParam("identity", "123456789987")
                        .header("keyId", "${keyId}")
                        .check(status().is(200)))
                .exec(http("verify")
                        .post("/api/oauth2/otp/verify")
                        .queryParam("identity", "123456789987")
                        .queryParam("otp", "${random}")
                        .header("keyId", "${keyId}")
                        .check(status().is(200), jsonPath("$.refresh_token").saveAs("refresh_token")))
                .exec(http("refresh")
                        .get("/api/oauth2/otp/refresh")
                        .queryParam("refreshToken", "${refresh_token}")
                        .header("keyId", "${keyId}"));

        ;

        setUp(scn.injectOpen(constantUsersPerSec(10).during(Duration.ofMinutes(1)))
                .protocols(httpProtocol))
                .assertions(global().responseTime().percentile3().lt(1000),
                        global().successfulRequests().percent().gt(95.0));
    }
}

