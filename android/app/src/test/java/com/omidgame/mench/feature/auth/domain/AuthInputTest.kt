package com.omidgame.mench.feature.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthInputTest {

    @Test
    fun `already valid E164 is unchanged`() {
        assertThat(normalizePhoneInput("+15551234567")).isEqualTo("+15551234567")
        assertThat(normalizePhoneInput("+989123456789")).isEqualTo("+989123456789")
    }

    @Test
    fun `iranian mobile written locally becomes E164`() {
        assertThat(normalizePhoneInput("09123456789")).isEqualTo("+989123456789")
        assertThat(normalizePhoneInput("9123456789")).isEqualTo("+989123456789")
        assertThat(normalizePhoneInput("+09123456789")).isEqualTo("+989123456789")
    }

    @Test
    fun `persian digits spaces and dashes are cleaned`() {
        assertThat(normalizePhoneInput("۰۹۱۲ ۳۴۵-۶۷۸۹")).isEqualTo("+989123456789")
        assertThat(normalizePhoneInput("+98 (912) 345 6789")).isEqualTo("+989123456789")
    }

    @Test
    fun `double zero prefix becomes plus`() {
        assertThat(normalizePhoneInput("00989123456789")).isEqualTo("+989123456789")
    }

    @Test
    fun `otp code accepts persian and arabic digits and drops junk`() {
        assertThat(normalizeOtpCode("۱۲۳۴۵")).isEqualTo("12345")
        assertThat(normalizeOtpCode("١٢٣٤٥")).isEqualTo("12345")
        assertThat(normalizeOtpCode("12 3-45")).isEqualTo("12345")
    }
}
