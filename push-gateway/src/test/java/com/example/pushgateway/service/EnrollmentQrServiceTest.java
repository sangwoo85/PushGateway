package com.example.pushgateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.pushgateway.config.PushProperties;
import com.example.pushgateway.domain.PushTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnrollmentQrServiceTest {
    @TempDir Path temp;

    @Test
    void createsIosCompatibleSignedPayloadAndX963PublicKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        Path privatePath = temp.resolve("enrollment-key.pem");
        writePem(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(Path.of(privatePath + ".pub"), "PUBLIC KEY", pair.getPublic().getEncoded());

        var topicService = mock(PushTopicService.class);
        when(topicService.getOrCreate(PushTargetType.USER, "user-1"))
                .thenReturn("usr_01234567890123456789012345678901");
        when(topicService.getOrCreate(PushTargetType.DEPARTMENT, "dept-1"))
                .thenReturn("dept_01234567890123456789012345678901");
        when(topicService.getOrCreate(PushTargetType.NOTICE, "ALL")).thenReturn("notice_all");
        var properties = new PushProperties(
                new PushProperties.Scheduler(false, Duration.ofSeconds(30), 100, Duration.ofMinutes(5), 5),
                new PushProperties.Firebase(false, "depl-project"),
                new PushProperties.Qr(privatePath.toString(), Duration.ofMinutes(3)),
                new PushProperties.TestPage(false, "", "", true, 10));
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        var service = new EnrollmentQrService(topicService, properties, new ObjectMapper(),
                new SimpleMeterRegistry(), Clock.fixed(now, ZoneOffset.UTC));

        var payload = service.createPayload("user-1", "dept-1");
        assertThat(Instant.parse(payload.expiresAt())).isEqualTo(now.plus(Duration.ofMinutes(3)));
        assertThat(payload.nonce()).hasSizeGreaterThanOrEqualTo(16);
        String canonical = EnrollmentQrService.canonical(payload.version(), payload.firebaseProjectId(),
                payload.topics().user(), payload.topics().department(), payload.topics().notice(),
                payload.issuedAt(), payload.expiresAt(), payload.nonce());
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(payload.signature()))).isTrue();
        byte[] x963 = Base64.getDecoder().decode(service.publicKeyX963Base64());
        assertThat(x963).hasSize(65);
        assertThat(x963[0]).isEqualTo((byte) 4);
    }

    private void writePem(Path path, String type, byte[] bytes) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }
}
