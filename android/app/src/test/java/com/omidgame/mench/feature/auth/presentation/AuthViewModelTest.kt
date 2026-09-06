package com.omidgame.mench.feature.auth.presentation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.omidgame.mench.feature.auth.domain.AuthFailureReason
import com.omidgame.mench.feature.auth.domain.AuthRepository
import com.omidgame.mench.feature.auth.domain.AuthResult
import com.omidgame.mench.feature.auth.domain.OtpChallenge
import com.omidgame.mench.feature.auth.domain.RequestOtpUseCase
import com.omidgame.mench.feature.auth.domain.VerifyOtpUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk()
        viewModel = AuthViewModel(RequestOtpUseCase(authRepository), VerifyOtpUseCase(authRepository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitting a valid phone transitions PhoneEntry to OtpEntry on success`() = runTest {
        val challenge = OtpChallenge(challengeId = "c1", phone = "+15551234567")
        coEvery { authRepository.requestOtp("+15551234567") } returns AuthResult.Success(challenge)

        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(AuthUiState.PhoneEntry)
            viewModel.onSubmitPhone("+15551234567")
            assertThat(awaitItem()).isEqualTo(AuthUiState.RequestingOtp)
            val otpState = awaitItem()
            assertThat(otpState).isInstanceOf(AuthUiState.OtpEntry::class.java)
            assertThat((otpState as AuthUiState.OtpEntry).challenge).isEqualTo(challenge)
        }
    }

    @Test
    fun `wrong code surfaces an error message and stops the loading spinner, without leaving OtpEntry`() = runTest {
        val challenge = OtpChallenge(challengeId = "c1", phone = "+15551234567")
        coEvery { authRepository.requestOtp(any()) } returns AuthResult.Success(challenge)
        coEvery { authRepository.verifyOtp(any(), any(), any()) } returns
            AuthResult.Failure(AuthFailureReason.INVALID_OR_EXPIRED_CODE)

        viewModel.uiState.test {
            awaitItem() // PhoneEntry
            viewModel.onSubmitPhone("+15551234567")
            awaitItem() // RequestingOtp
            awaitItem() // OtpEntry (idle)

            viewModel.onSubmitCode("00000", deviceName = "test-device")
            val verifying = awaitItem() as AuthUiState.OtpEntry
            assertThat(verifying.isVerifying).isTrue()

            val failed = awaitItem() as AuthUiState.OtpEntry
            assertThat(failed.isVerifying).isFalse()
            assertThat(failed.errorMessage).isNotNull()
        }
    }

    @Test
    fun `correct code transitions to SignedIn`() = runTest {
        val challenge = OtpChallenge(challengeId = "c1", phone = "+15551234567")
        coEvery { authRepository.requestOtp(any()) } returns AuthResult.Success(challenge)
        coEvery { authRepository.verifyOtp(any(), any(), any()) } returns AuthResult.Success(Unit)

        viewModel.uiState.test {
            awaitItem() // PhoneEntry
            viewModel.onSubmitPhone("+15551234567")
            awaitItem() // RequestingOtp
            awaitItem() // OtpEntry

            viewModel.onSubmitCode("54321", deviceName = "test-device")
            awaitItem() // OtpEntry(isVerifying = true)
            assertThat(awaitItem()).isEqualTo(AuthUiState.SignedIn)
        }
    }
}
