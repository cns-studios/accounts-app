package com.cns.accounts.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLabelsTest {
    @Test
    fun userFacingEvents() {
        listOf("login_success", "approval", "recovery_phrase_created", "password_changed", "require_app_disabled", "something_new")
            .forEach { assertTrue(it, isUserFacingEvent(it)) }
    }

    @Test
    fun bookkeepingEventsAreHidden() {
        listOf("refresh_reuse_detected", "login_challenge_approval", "token_issued", "token_refreshed", "auth_code_issued", "service_data_put")
            .forEach { assertFalse(it, isUserFacingEvent(it)) }
    }
}
