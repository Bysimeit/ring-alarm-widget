package dev.ringalarmwidget.ui

import android.content.Context
import dev.ringalarmwidget.R
import dev.ringalarmwidget.core.auth.AuthResult
import dev.ringalarmwidget.core.auth.LeaseFailure
import dev.ringalarmwidget.core.auth.TokenLease
import dev.ringalarmwidget.core.auth.TwoFactorDelivery
import dev.ringalarmwidget.core.auth.TwoFactorPrompt

fun Context.messageFor(result: AuthResult): String? = when (result) {
    is AuthResult.Success -> null
    is AuthResult.TwoFactorRequired -> messageFor(result.prompt)
    AuthResult.InvalidCredentials -> getString(R.string.error_invalid_credentials)
    AuthResult.InvalidTwoFactorCode -> getString(R.string.error_invalid_code)
    is AuthResult.RateLimited -> rateLimitMessage(result.retryAfterSeconds)
    is AuthResult.Failed -> getString(R.string.error_unreachable)
}

fun Context.messageFor(prompt: TwoFactorPrompt): String = when (prompt.delivery) {
    TwoFactorDelivery.SMS -> prompt.maskedDestination
        ?.let { getString(R.string.twofactor_sent_sms, it) }
        ?: getString(R.string.twofactor_sent_sms_unknown)

    TwoFactorDelivery.EMAIL -> getString(R.string.twofactor_sent_email)
    TwoFactorDelivery.AUTHENTICATOR -> getString(R.string.twofactor_sent_authenticator)
    TwoFactorDelivery.UNKNOWN -> getString(R.string.twofactor_sent_unknown)
}

fun Context.messageFor(lease: TokenLease): String? = when (lease) {
    is TokenLease.Active -> null
    TokenLease.SignedOut -> getString(R.string.error_signed_out)
    is TokenLease.Unavailable -> when (val cause = lease.cause) {
        is LeaseFailure.RateLimited -> rateLimitMessage(cause.retryAfterSeconds)
        is LeaseFailure.Unreachable -> getString(R.string.error_unreachable)
    }
}

fun Context.resendDelayMessage(seconds: Long?): String? = seconds?.let {
    resources.getQuantityString(R.plurals.twofactor_resend_in, it.toInt(), it.toInt())
}

private fun Context.rateLimitMessage(seconds: Long?): String =
    if (seconds == null) {
        getString(R.string.error_rate_limited_unknown)
    } else {
        resources.getQuantityString(R.plurals.error_rate_limited, seconds.toInt(), seconds.toInt())
    }
