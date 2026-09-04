package com.sangwoo.push.enrollment

import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

class EnrollmentQrValidator(
    private val expectedProjectId: String,
    private val publicKeyX963Base64: String,
    private val json: Json = Json { ignoreUnknownKeys = false }
) {
    fun validate(rawValue: String, now: Instant = Instant.now()): EnrollmentQrPayload {
        val payload = try {
            json.decodeFromString<EnrollmentQrPayload>(rawValue)
        } catch (_: Exception) {
            throw EnrollmentException.InvalidFormat()
        }
        if (payload.version != 1 || payload.firebaseProjectId != expectedProjectId) {
            throw EnrollmentException.WrongSystem()
        }
        val issued = tryInstant(payload.issuedAt)
        val expires = tryInstant(payload.expiresAt)
        if (issued > expires || Duration.between(issued, expires) > Duration.ofMinutes(10)) {
            throw EnrollmentException.InvalidDate()
        }
        if (issued > now.plusSeconds(60)) throw EnrollmentException.IssuedInFuture()
        if (expires <= now) throw EnrollmentException.Expired()
        if (payload.nonce.length < 16 ||
            !validTopic(payload.topics.user, "usr_") ||
            !validTopic(payload.topics.department, "dept_") ||
            payload.topics.notice != "notice_all"
        ) throw EnrollmentException.InvalidFormat()
        if (!verify(payload)) throw EnrollmentException.InvalidSignature()
        return payload
    }

    private fun tryInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (_: Exception) {
        throw EnrollmentException.InvalidDate()
    }

    private fun validTopic(value: String, prefix: String): Boolean =
        value.startsWith(prefix) && value.length >= prefix.length + 32 &&
            value.matches(Regex("^[A-Za-z0-9._~-]+$"))

    private fun verify(payload: EnrollmentQrPayload): Boolean {
        return try {
            val publicBytes = Base64.getDecoder().decode(publicKeyX963Base64)
            if (publicBytes.size != 65 || publicBytes[0] != 0x04.toByte()) return false
            val parameters = AlgorithmParameters.getInstance("EC").apply {
                init(ECGenParameterSpec("secp256r1"))
            }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val point = ECPoint(
                BigInteger(1, publicBytes.copyOfRange(1, 33)),
                BigInteger(1, publicBytes.copyOfRange(33, 65))
            )
            val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, parameters))
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(key)
            signature.update(payload.canonical())
            signature.verify(Base64.getDecoder().decode(payload.signature))
        } catch (_: Exception) {
            false
        }
    }
}
