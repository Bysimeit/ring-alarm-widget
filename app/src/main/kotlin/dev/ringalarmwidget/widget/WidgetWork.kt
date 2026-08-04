package dev.ringalarmwidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.data.PanelOutcome
import dev.ringalarmwidget.data.PanelRepository
import java.util.concurrent.TimeUnit

class PanelWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = PanelRepository(applicationContext)
        val requested = inputData.getString(DATA_MODE)
            ?.let { name -> AlarmMode.entries.firstOrNull { it.name == name } }
            ?.takeIf { System.currentTimeMillis() - inputData.getLong(DATA_ASKED_AT, 0) < REQUEST_TTL_MILLIS }

        val outcome = if (requested == null) repository.read() else repository.set(requested)

        updateWidgets(applicationContext)

        if (outcome is PanelOutcome.Ready) {
            WidgetWork.settle(applicationContext, outcome.snapshot.transitionEndsAtEpochMillis)
        }

        return if (outcome is PanelOutcome.Ready) Result.success() else Result.failure()
    }

    companion object {
        const val DATA_MODE = "mode"
        const val DATA_ASKED_AT = "asked_at"
        const val REQUEST_TTL_MILLIS = 120_000L
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
        enqueue(context, Data.EMPTY)
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

    fun apply(context: Context, mode: AlarmMode) {
        enqueue(
            context,
            Data.Builder()
                .putString(PanelWorker.DATA_MODE, mode.name)
                .putLong(PanelWorker.DATA_ASKED_AT, System.currentTimeMillis())
                .build(),
        )
    }

    private fun enqueue(context: Context, data: Data) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<PanelWorker>()
                .setInputData(data)
                .setConstraints(connected())
                .build()
        )
    }

    private fun connected() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private const val PERIODIC = "panel-refresh"
    private const val SETTLE = "panel-settle"
    private const val SETTLE_MILLIS = 2_000L
    private const val REFRESH_MINUTES = 15L
    private const val FLEX_MINUTES = 5L
}
