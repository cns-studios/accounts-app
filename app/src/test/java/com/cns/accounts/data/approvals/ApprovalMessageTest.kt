package com.cns.accounts.data.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The signed strings must match what auth-v2 rebuilds byte for byte (internal/service/approvals.go),
 * or every decision and enrollment fails signature verification.
 */
class ApprovalMessageTest {
    @Test
    fun decisionMessageFormat() {
        val approve = approvalMessage("3f1c", 42, "recovery_phrase_create", "nonce", approve = true, expiresAtUnix = 1_790_000_000)
        assertEquals("cns-approval-v1|3f1c|42|recovery_phrase_create|nonce|approve|1790000000", approve.decodeToString())
        val deny = approvalMessage("3f1c", 42, "login", "nonce", approve = false, expiresAtUnix = 1)
        assertEquals("cns-approval-v1|3f1c|42|login|nonce|deny|1", deny.decodeToString())
    }

    @Test
    fun everyFieldIsBoundIntoTheMessage() {
        val base = approvalMessage("id", 1, "login", "n", true, 10).decodeToString()
        listOf(
            approvalMessage("id2", 1, "login", "n", true, 10),
            approvalMessage("id", 2, "login", "n", true, 10),
            approvalMessage("id", 1, "authorize", "n", true, 10),
            approvalMessage("id", 1, "login", "m", true, 10),
            approvalMessage("id", 1, "login", "n", false, 10),
            approvalMessage("id", 1, "login", "n", true, 11),
        ).forEach { assertNotEquals(base, it.decodeToString()) }
    }

    @Test
    fun enrollMessageFormat() =
        assertEquals("cns-approver-enroll-v1|42|Y2hhbGxlbmdl", enrollMessage(42, "Y2hhbGxlbmdl").decodeToString())
}
