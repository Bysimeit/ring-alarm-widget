package dev.ringalarmwidget.widget

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.data.PanelOutcome
import dev.ringalarmwidget.data.PanelRepository
import java.util.concurrent.TimeUnit

class PanelWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = PanelRepository(applicationContext)
        val asked = inputData.getString(DATA_MODE)
            ?.let { name -> AlarmMode.entries.firstOrNull { it.name == name } }

        val askedAt = inputData.getLong(DATA_ASKED_AT, 0)
        if (asked != null && System.currentTimeMillis() - askedAt >= REQUEST_TTL_MILLIS) {
            Log.i(LOG_TAG, "${asked.name} attempt=$runAttemptCount abandoned age=${System.currentTimeMillis() - askedAt}ms")
            repository.abandonChange()
            updateWidgets(applicationContext)
            return Result.success()
        }

        val mayRetry = asked != null && runAttemptCount < MAX_ATTEMPTS
        val bypass = inputData.getStringArray(DATA_BYPASS)?.toList().orEmpty()
        val startedAt = SystemClock.elapsedRealtime()
        val outcome = if (asked == null) {
            repository.read()
        } else {
            repository.set(asked, mayRetry, bypass)
        }

        Log.i(
            LOG_TAG,
            "${asked?.name ?: "READ"} attempt=$runAttemptCount " +
                "${SystemClock.elapsedRealtime() - startedAt}ms ${describe(outcome)}",
        )

        updateWidgets(applicationContext)

        if (outcome is PanelOutcome.Ready) {
            WidgetWork.settle(applicationContext, outcome.snapshot.transitionEndsAtEpochMillis)
            return Result.success()
        }

        return if (mayRetry && outcome.isTransient) Result.retry() else Result.success()
    }

    companion object {
        const val DATA_MODE = "mode"
        const val DATA_ASKED_AT = "asked_at"
        const val DATA_BYPASS = "bypass"
        const val REQUEST_TTL_MILLIS = 120_000L
        const val MAX_ATTEMPTS = 3
        const val LOG_TAG = "RingAlarmWidget"

        private fun describe(outcome: PanelOutcome): String = when (outcome) {
            is PanelOutcome.Ready -> "ready mode=${outcome.snapshot.mode?.name}"
            PanelOutcome.SignedOut -> "signed-out"
            PanelOutcome.NoLocation -> "no-location"
            is PanelOutcome.Unavailable -> "unavailable cause=${outcome.cause}"
            is PanelOutcome.NeedsBypass -> "bypass-required requested=${outcome.requested.name}"
            is PanelOutcome.Failed -> "failed ${describe(outcome.result)}"
        }

        private fun describe(result: PanelResult): String = when (result) {
            is PanelResult.Ready -> "ready"
            PanelResult.NoSecurityPanel -> "no-security-panel"
            PanelResult.NoUsableAsset -> "no-usable-asset"
            is PanelResult.Rejected -> "rejected observed=${result.observed?.name}"
            is PanelResult.BypassRequired -> "bypass-required"
            is PanelResult.TimedOut -> "timed-out stage=${result.stage}"
            is PanelResult.Unreachable -> "unreachable status=${result.statusCode} ${result.diagnostic}"
        }
    }
}

object WidgetWork {

    fun start(context: Context) {
        schedule(context)
        refresh(context)
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<PanelWorker>(
                REFRESH_MINUTES, TimeUnit.MINUTES,
                FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(connected())
                .build(),
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun refresh(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH,
            ExistingWorkPolicy.KEEP,
            request(Data.EMPTY, urgent = true),
        )
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH,
            ExistingWorkPolicy.REPLACE,
            request(Data.EMPTY, urgent = true),
        )
    }

    fun settle(context: Context, transitionEndsAt: Long?) {
        val wait = (transitionEndsAt ?: return) + SETTLE_MILLIS - System.currentTimeMillis()
        if (wait <= 0) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            SETTLE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PanelWorker>()
                .setInitialDelay(wait, TimeUnit.MILLISECONDS)
                .setConstraints(connected())
                .build(),
        )
    }

    fun apply(context: Context, mode: AlarmMode, bypassZids: List<String> = emptyList()) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(REFRESH)
        manager.enqueueUniqueWork(
            CHANGE,
            ExistingWorkPolicy.REPLACE,
            request(
                Data.Builder()
                    .putString(PanelWorker.DATA_MODE, mode.name)
                    .putLong(PanelWorker.DATA_ASKED_AT, System.currentTimeMillis())
                    .putStringArray(PanelWorker.DATA_BYPASS, bypassZids.toTypedArray())
                    .build(),
                urgent = true,
            ),
        )
    }

    private fun request(data: Data, urgent: Boolean): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<PanelWorker>()
            .setInputData(data)
            .setConstraints(connected())
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .apply {
                if (urgent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()

    private fun connected() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val PERIODIC = "panel-refresh"
    private const val REFRESH = "panel-once"
    private const val CHANGE = "panel-change"
    private const val SETTLE = "panel-settle"
    private const val SETTLE_MILLIS = 2_000L
    private const val REFRESH_MINUTES = 15L
    private const val FLEX_MINUTES = 5L
    private const val BACKOFF_SECONDS = 15L
}
