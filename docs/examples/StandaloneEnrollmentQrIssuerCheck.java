package com.sangwoo.push.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Executable smoke check; no real credentials, filesystem output or network access. */
public final class StandaloneEnrollmentQrIssuerCheck {
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var key = generator.generateKeyPair();
        var now = Instant.parse("2026-09-09T00:00:00Z");
        var issuer = new StandaloneEnrollmentQrIssuer("example-project", key.getPrivate(),
                Clock.fixed(now, ZoneOffset.UTC));
        var topics = new StandaloneEnrollmentQrIssuer.Topics(
                "usr_" + "a".repeat(32), "dept_" + "b".repeat(32), "notice_all");
        var result = issuer.issue(topics, Duration.ofMinutes(3));
        JsonNode payload = decode(result.png());
        check(payload.get("version").asInt() == 1, "version");
        check(payload.get("firebaseProjectId").asText().equals("example-project"), "project");
        check(payload.get("topics").get("user").asText().equals(topics.user()), "user topic");
        check(payload.get("topics").get("department").asText().equals(topics.department()), "department topic");
        check(payload.get("topics").get("notice").asText().equals("notice_all"), "notice topic");
        check(Instant.parse(payload.get("issuedAt").asText()).equals(now), "issuedAt");
        check(Instant.parse(payload.get("expiresAt").asText()).equals(now.plusSeconds(180)), "TTL");
        check(result.expiresAt().equals(now.plusSeconds(180)), "result expiry");
        check(payload.get("nonce").asText().matches("[A-Za-z0-9_-]{24}"), "nonce format");
        String canonical = String.join("|", payload.get("version").asText(),
                payload.get("firebaseProjectId").asText(), payload.get("topics").get("user").asText(),
                payload.get("topics").get("department").asText(), payload.get("topics").get("notice").asText(),
                payload.get("issuedAt").asText(), payload.get("expiresAt").asText(), payload.get("nonce").asText());
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(key.getPublic());
        verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        var signature = Base64.getDecoder().decode(payload.get("signature").asText());
        check(verifier.verify(signature), "DER signature");
        verifier.initVerify(key.getPublic());
        verifier.update((canonical + "tampered").getBytes(StandardCharsets.UTF_8));
        check(!verifier.verify(signature), "tamper rejection");
        var next = decode(issuer.issue(topics, Duration.ofMinutes(10)).png());
        check(!next.get("nonce").equals(payload.get("nonce")), "fresh nonce");
        reject(() -> issuer.issue(topics, Duration.ZERO));
        reject(() -> issuer.issue(topics, Duration.ofSeconds(-1)));
        reject(() -> issuer.issue(topics, Duration.ofSeconds(601)));
        reject(() -> issuer.issue(new StandaloneEnrollmentQrIssuer.Topics("usr_id", topics.department(),
                "notice_all"), Duration.ofMinutes(3)));
        reject(() -> issuer.issue(new StandaloneEnrollmentQrIssuer.Topics(topics.user(), topics.department(),
                "other"), Duration.ofMinutes(3)));
        System.out.println("PASS: PNG decode, protocol fields, signature, tamper, TTL, topics, nonce");
    }

    private static JsonNode decode(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        check(image.getWidth() == 480 && image.getHeight() == 480, "PNG dimensions");
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new ObjectMapper().readTree(new MultiFormatReader().decode(bitmap).getText());
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }

    private static void reject(CheckedAction action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid input accepted");
    }

    private interface CheckedAction { void run() throws Exception; }
}
