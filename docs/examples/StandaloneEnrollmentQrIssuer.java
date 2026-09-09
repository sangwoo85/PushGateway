package com.sangwoo.push.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/** QR-only example. Caller MUST authorize the issuer and resolve trusted, active DB bindings. */
public final class StandaloneEnrollmentQrIssuer {
    private final String projectId;
    private final PrivateKey privateKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper = new ObjectMapper();

    public StandaloneEnrollmentQrIssuer(String projectId, PrivateKey privateKey, Clock clock)
            throws Exception {
        if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{4,28}[a-z0-9]")) {
            throw new IllegalArgumentException("Firebase project ID required (not display name)");
        }
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec expected = parameters.getParameterSpec(ECParameterSpec.class);
        if (!(privateKey instanceof ECPrivateKey ec)
                || !ec.getParams().getCurve().equals(expected.getCurve())
                || !ec.getParams().getGenerator().equals(expected.getGenerator())
                || !ec.getParams().getOrder().equals(expected.getOrder())
                || ec.getParams().getCofactor() != expected.getCofactor()) {
            throw new IllegalArgumentException("P-256 signing private key required");
        }
        this.projectId = projectId;
        this.privateKey = privateKey;
        this.clock = Objects.requireNonNull(clock);
    }

    public static PrivateKey loadPkcs8Pem(Path path) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    public IssuedQr issue(Topics topics, Duration ttl) throws Exception {
        Objects.requireNonNull(topics);
        if (!validTopic(topics.user(), "usr_") || !validTopic(topics.department(), "dept_")
                || !"notice_all".equals(topics.notice())) {
            throw new IllegalArgumentException("Invalid enrollment topics");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()
                || ttl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("TTL must be > 0 and <= 10 minutes");
        }
        Instant issued = clock.instant();
        Instant expires = issued.plus(ttl);
        byte[] nonceBytes = new byte[18];
        random.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String canonical = String.join("|", "1", projectId, topics.user(), topics.department(),
                topics.notice(), issued.toString(), expires.toString(), nonce);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey, random);
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        Payload payload = new Payload(1, projectId, topics, issued.toString(), expires.toString(),
                nonce, Base64.getEncoder().encodeToString(signer.sign()));
        var matrix = new QRCodeWriter().encode(mapper.writeValueAsString(payload),
                BarcodeFormat.QR_CODE, 480, 480);
        var output = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", output);
        return new IssuedQr(output.toByteArray(), expires);
    }

    private static boolean validTopic(String value, String prefix) {
        return value != null && value.length() <= 255
                && value.matches(prefix + "[A-Za-z0-9_-]{32,}");
    }

    public record Topics(String user, String department, String notice) {}
    public record Payload(int version, String firebaseProjectId, Topics topics,
                          String issuedAt, String expiresAt, String nonce, String signature) {}
    public record IssuedQr(byte[] png, Instant expiresAt) {}
}
