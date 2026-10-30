package com.qianxi.simlet.boot

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qianxi.simlet.core.PlanStore
import com.qianxi.simlet.core.ShizukuLink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 开机后延迟尝试回写；不主动拉起 Shizuku 界面，避免打扰。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        schedule(appContext, jobId = JOB_ID, delayMs = 10_000L)
        schedule(appContext, jobId = JOB_ID_RETRY, delayMs = 120_000L)
    }

    private fun schedule(context: Context, jobId: Int, delayMs: Long) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(jobId, ComponentName(context, BootReapplyService::class.java))
            .setMinimumLatency(delayMs)
            .setOverrideDeadline(delayMs + 60_000L)
            .setPersisted(true)
            .build()
        runCatching { scheduler.schedule(job) }
            .onFailure { Log.w(TAG, "调度回写任务失败 id=$jobId", it) }
    }

    companion object {
        private const val TAG = "SimletBoot"
        private const val JOB_ID = 41001
        private const val JOB_ID_RETRY = 41002
    }
}

class BootReapplyService : JobService() {

    @Volatile
    private var worker: Thread? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        worker = Thread({
            if (!awaitShizuku(READY_TIMEOUT_MS)) {
                jobFinished(params, true)
                return@Thread
            }
            val done = runCatching { PlanStore.reapplyAfterBoot(applicationContext) }.getOrDefault(false)
            Log.i(TAG, "开机回写${if (done) "完成" else "跳过"}")
            jobFinished(params, false)
        }, "SimletBoot").apply { start() }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        worker?.interrupt()
        worker = null
        return true
    }

    private fun awaitShizuku(timeoutMs: Long): Boolean {
        if (ShizukuLink.granted()) return true
        val latch = CountDownLatch(1)
        val listener = ShizukuLink.addBinderListener { if (ShizukuLink.granted()) latch.countDown() }
        return try {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (ShizukuLink.granted()) return true
                if (Thread.currentThread().isInterrupted) return false
                latch.await(2_000L, TimeUnit.MILLISECONDS)
            }
            ShizukuLink.granted()
        } finally {
            ShizukuLink.removeBinderListener(listener)
        }
    }

    companion object {
        private const val TAG = "SimletBoot"
        private const val READY_TIMEOUT_MS = 90_000L
    }
}
