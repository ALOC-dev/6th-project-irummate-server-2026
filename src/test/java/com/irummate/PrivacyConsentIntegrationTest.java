package com.irummate;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrivacyConsentIntegrationTest extends SecurityIntegrationSupport {
    private static final String CONSENTS = "/api/privacy/consents";
    private static final String DETAILS = """
            {"realName":"Test Member","studentId":"20260001","age":20,
             "gender":"MALE","department":"Engineering","phoneNumber":"010-1111-2222"}
            """;
    private static final String ONBOARDING = """
            {"profileCollection":{"agreed":true,"documentVersion":"1.0"},
             "aiCertTraining":{"agreed":false,"documentVersion":"1.0"}}
            """;

    @Test
    void onboardingGatesProfileAndKeepsIsolatedHistoryAcrossRetriesAndWithdrawal() throws Exception {
        var migration = Files.readString(Path.of("migration-privacy-consents.sql"));
        jdbc.execute("DROP TABLE privacy_consent_history, privacy_consents");
        jdbc.execute(migration);
        jdbc.execute(migration);

        String alice = access(ALICE, aliceSession);
        String bob = access(BOB, bobSession);
        assertEquals(401, jsonRequest("GET", CONSENTS + "/me", null, null).statusCode());
        assertEquals("NOT_DECIDED", consentStatus(alice, 0));
        assertCode(403, "PROFILE_CONSENT_REQUIRED", jsonRequest("POST", "/api/users/details", alice, DETAILS));
        assertCode(403, "PROFILE_CONSENT_REQUIRED",
                jsonRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING.replace("true", "false")));
        assertCode(409, "CONSENT_VERSION_CONFLICT",
                jsonRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING.replace("1.0", "2.0")));
        assertEquals(0, historyCount());

        jdbc.execute("""
                ALTER TABLE privacy_consent_history
                ADD CONSTRAINT reject_ai_history_for_test CHECK (consent_type <> 'AI_CERT_TRAINING')
                """);
        try {
            assertEquals(500, jsonRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING).statusCode());
            assertEquals(0, historyCount());
            assertEquals("NOT_DECIDED", consentStatus(alice, 0));
        } finally {
            jdbc.execute("ALTER TABLE privacy_consent_history DROP CONSTRAINT reject_ai_history_for_test");
        }

        var first = http.sendAsync(buildRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING),
                HttpResponse.BodyHandlers.ofString());
        var retry = http.sendAsync(buildRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, first.get().statusCode());
        assertEquals(204, retry.get().statusCode());
        assertEquals(2, historyCount());
        assertEquals("AGREED", consentStatus(alice, 0));
        assertEquals("DECLINED", consentStatus(alice, 1));
        assertEquals("NOT_DECIDED", consentStatus(bob, 0));
        assertEquals(201, jsonRequest("POST", "/api/users/details", alice, DETAILS).statusCode());

        String ai = CONSENTS + "/AI_CERT_TRAINING";
        assertEquals(204, jsonRequest("PUT", ai, alice, """
                {"action":"AGREE","documentVersion":"1.0"}
                """).statusCode());
        assertEquals(204, jsonRequest("PUT", ai, alice, "{\"action\":\"WITHDRAW\"}").statusCode());
        assertEquals(204, jsonRequest("PUT", ai, alice, "{\"action\":\"WITHDRAW\"}").statusCode());
        assertEquals(204, jsonRequest("POST", CONSENTS + "/onboarding", alice, ONBOARDING).statusCode());
        assertEquals("WITHDRAWN", consentStatus(alice, 1));
        assertEquals(4, historyCount());
        assertCode(403, "CONSENT_NOT_WITHDRAWABLE",
                jsonRequest("PUT", CONSENTS + "/PROFILE_COLLECTION", alice, "{\"action\":\"WITHDRAW\"}"));
    }

    private int historyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM privacy_consent_history", Integer.class);
    }

    private String consentStatus(String token, int index) throws Exception {
        var response = jsonRequest("GET", CONSENTS + "/me", token, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("data").path("consents").get(index).path("status").asText();
    }

    private void assertCode(int status, String code, HttpResponse<String> response) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json.readTree(response.body()).path("code").asText());
    }

    private HttpResponse<String> jsonRequest(String method, String path, String token, String body) throws Exception {
        return http.send(buildRequest(method, path, token, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest buildRequest(String method, String path, String token, String body) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) return request.method(method, HttpRequest.BodyPublishers.noBody()).build();
        return request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
