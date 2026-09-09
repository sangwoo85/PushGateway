package com.sangwoo.push.service;

import com.sangwoo.push.config.PushProperties;
import com.sangwoo.push.domain.PushTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EnrollmentQrService {
    private final PushTopicService topics;
    private final PushProperties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Autowired
    public EnrollmentQrService(PushTopicService topics, PushProperties properties,
                               ObjectMapper mapper, MeterRegistry metrics) {
        this(topics, properties, mapper, metrics, Clock.systemUTC());
    }

    EnrollmentQrService(PushTopicService topics, PushProperties properties, ObjectMapper mapper,
                        MeterRegistry metrics, Clock clock) {
        this.topics = topics;
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    public IssuedQr issue(String userId, String departmentId) {
        try {
            EnrollmentPayload payload = createPayload(userId, departmentId);
            String json = mapper.writeValueAsString(payload);
            metrics.counter("push.qr.issue", "result", "success").increment();
            return new IssuedQr(toPng(json), Instant.parse(payload.expiresAt()));
        } catch (RuntimeException | IOException | WriterException ex) {
            metrics.counter("push.qr.issue", "result", "failure").increment();
            throw new IllegalStateException("QR issuance failed", ex);
        }
    }

    EnrollmentPayload createPayload(String userId, String departmentId) {
        try {
            if (properties.firebase().projectId().isBlank()) {
                throw new IllegalStateException("FIREBASE_PROJECT_ID is required");
            }
            String userTopic = topics.getOrCreate(PushTargetType.USER, userId);
            String departmentTopic = topics.getOrCreate(PushTargetType.DEPARTMENT, departmentId);
            String noticeTopic = topics.getOrCreate(PushTargetType.NOTICE, PushTopicService.NOTICE_TARGET_ID);
            Instant issued = clock.instant();
            Instant expires = issued.plus(properties.qr().ttl());
            String issuedAt = DateTimeFormatter.ISO_INSTANT.format(issued);
            String expiresAt = DateTimeFormatter.ISO_INSTANT.format(expires);
            byte[] nonceBytes = new byte[18];
            random.nextBytes(nonceBytes);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
            String canonical = canonical(1, properties.firebase().projectId(), userTopic,
                    departmentTopic, noticeTopic, issuedAt, expiresAt, nonce);
            String signature = Base64.getEncoder().encodeToString(sign(canonical));
            return new EnrollmentPayload(1, properties.firebase().projectId(),
                    new EnrollmentTopics(userTopic, departmentTopic, noticeTopic),
                    issuedAt, expiresAt, nonce, signature);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("QR payload signing failed", ex);
        }
    }

    public static String canonical(int version, String projectId, String userTopic,
                                   String departmentTopic, String noticeTopic,
                                   String issuedAt, String expiresAt, String nonce) {
        return String.join("|", Integer.toString(version), projectId, userTopic,
                departmentTopic, noticeTopic, issuedAt, expiresAt, nonce);
    }

    public String publicKeyX963Base64() {
        try {
            ECPublicKey key = (ECPublicKey) derivePublicKey(loadPrivateKey());
            int size = (key.getParams().getCurve().getField().getFieldSize() + 7) / 8;
            byte[] x = unsignedFixed(key.getW().getAffineX().toByteArray(), size);
            byte[] y = unsignedFixed(key.getW().getAffineY().toByteArray(), size);
            byte[] output = new byte[1 + size * 2];
            output[0] = 4;
            System.arraycopy(x, 0, output, 1, size);
            System.arraycopy(y, 0, output, 1 + size, size);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive enrollment public key", ex);
        }
    }

    private byte[] sign(String canonical) throws GeneralSecurityException, IOException {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(loadPrivateKey(), random);
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return signer.sign();
    }

    private PrivateKey loadPrivateKey() throws IOException, GeneralSecurityException {
        String path = properties.qr().privateKeyPath();
        if (path.isBlank()) throw new IllegalStateException("PUSH_QR_PRIVATE_KEY_PATH is required");
        String pem = Files.readString(Path.of(path), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private java.security.PublicKey derivePublicKey(PrivateKey privateKey) throws GeneralSecurityException, IOException {
        if (!(privateKey instanceof java.security.interfaces.ECPrivateKey)) {
            throw new GeneralSecurityException("EC private key required");
        }
        // JCA does not expose scalar multiplication. Re-read the matching public key from a sibling .pub file.
        String publicPath = properties.qr().privateKeyPath() + ".pub";
        String pem = Files.readString(Path.of(publicPath), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePublic(
                new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private byte[] toPng(String text) throws WriterException, IOException {
        var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 480, 480);
        var output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return output.toByteArray();
    }

    private static byte[] unsignedFixed(byte[] input, int size) {
        byte[] output = new byte[size];
        int source = Math.max(0, input.length - size);
        int length = Math.min(input.length, size);
        System.arraycopy(input, source, output, size - length, length);
        return output;
    }

    public record EnrollmentTopics(String user, String department, String notice) {}
    public record EnrollmentPayload(int version, String firebaseProjectId, EnrollmentTopics topics,
                                    String issuedAt, String expiresAt, String nonce, String signature) {}
    public record IssuedQr(byte[] png, Instant expiresAt) {}

}
