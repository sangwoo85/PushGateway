package com.sangwoo.push.enrollment

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

class EnrollmentQrValidatorTest {
    private val pair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    private val publicKey = (pair.public as ECPublicKey).let {
        byteArrayOf(4) + fixed(it.w.affineX.toByteArray()) + fixed(it.w.affineY.toByteArray())
    }
    private val validator = EnrollmentQrValidator("depl-162ae", Base64.getEncoder().encodeToString(publicKey))
    private val now = Instant.parse("2026-09-04T00:00:00Z")

    @Test fun acceptsValidGatewayCompatibleSignature() {
        val payload = signed(now.minusSeconds(10), now.plusSeconds(300))
        assertEquals("notice_all", validator.validate(Json.encodeToString(payload), now).topics.notice)
    }

    @Test fun rejectsWrongProjectExpiredFutureAndSignature() {
        val valid = signed(now.minusSeconds(10), now.plusSeconds(300))
        assertThrows(EnrollmentException.WrongSystem::class.java) {
            validator.validate(Json.encodeToString(valid.copy(firebaseProjectId = "other")), now)
        }
        assertThrows(EnrollmentException.Expired::class.java) {
            validator.validate(Json.encodeToString(signed(now.minusSeconds(100), now.minusSeconds(1))), now)
        }
        assertThrows(EnrollmentException.IssuedInFuture::class.java) {
            validator.validate(Json.encodeToString(signed(now.plusSeconds(61), now.plusSeconds(120))), now)
        }
        assertThrows(EnrollmentException.InvalidSignature::class.java) {
            validator.validate(Json.encodeToString(valid.copy(nonce = "changed-nonce-value")), now)
        }
    }

    @Test fun rejectsInvalidTopicsAndTooLongTtl() {
        val valid = signed(now, now.plusSeconds(300))
        assertThrows(EnrollmentException.InvalidFormat::class.java) {
            validator.validate(Json.encodeToString(valid.copy(topics = valid.topics.copy(user = "user-1"))), now)
        }
        assertThrows(EnrollmentException.InvalidDate::class.java) {
            validator.validate(Json.encodeToString(signed(now, now.plusSeconds(601))), now)
        }
    }

    private fun signed(issued: Instant, expires: Instant): EnrollmentQrPayload {
        val base = EnrollmentQrPayload(
            1, "depl-162ae",
            EnrollmentTopics("usr_${"a".repeat(32)}", "dept_${"b".repeat(32)}", "notice_all"),
            issued.toString(), expires.toString(), "nonce-1234567890123456", ""
        )
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(base.canonical())
        return base.copy(signature = Base64.getEncoder().encodeToString(signer.sign()))
    }

    private fun fixed(value: ByteArray): ByteArray = ByteArray(32).also { out ->
        val source = maxOf(0, value.size - 32)
        val length = minOf(value.size, 32)
        value.copyInto(out, 32 - length, source, source + length)
    }
}
