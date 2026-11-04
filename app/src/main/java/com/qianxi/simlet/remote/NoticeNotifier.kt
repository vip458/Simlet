package com.qianxi.simlet.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qianxi.simlet.R
import com.qianxi.simlet.SimletActivity

/**
 * 公告在通知栏的那一份。
 *
 * 只在「这条公告落地时人不在首页」时发：亮着首页的那一次已经被居中弹窗说过了，
 * 同一个公告只有一个「主动出声」名额（见 [RemoteStore.takeNoticeAttention]），
 * 两处都响等于同一句话提醒两遍。
 *
 * 点它不是「打开 App 自己再找一遍」，而是直接把这一条的居中弹窗摆出来——
 * 人是从通知栏知道有事的，落地就该看见那件事本身。
 *
 * 没有常驻服务、没有厂商推送，所以这条通道的时效和公告行一样：下一次进前台、
 * 且距上次尝试够久（后台的 `bv`）才谈得上收到新公告。
 */
object NoticeNotifier {

    private const val CHANNEL = "hub_notice"

    /** 一次只挂一条公告：号固定，换公告时新的直接顶掉旧的，通知栏不会攒出一串。 */
    private const val BAR_ID = 1001

    /** 运行时权限有没有到手。13 起要用户点头，13 以下装好就有——这一条只用来判断「还该不该问」。 */
    fun granted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * 发得出去吗。比 [granted] 多一道总闸。
     *
     * 8~12 上「应用通知」那个总开关可以和权限无关地关着，这时 notify() 不报错、什么都不发。
     * 只看权限就会踩到：名额被通知栏占掉了，人却既没收到通知、下次进首页也不再弹窗——
     * 一条公告两种提示都没有。发不出去就别占名额。
     */
    fun allowed(context: Context): Boolean =
        granted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 挂上去，并把「通知栏现在挂的是哪条」记进缓存。
     *
     * 正文那一行放标题，不放公告正文：标题是后台写的、限长 40 字的那一句，本来就是给人扫一眼的；
     * 正文长短不一，塞进通知栏要么被截要么占满一屏，而点开就是完整弹窗，没必要在这里读完。
     */
    fun show(context: Context, store: RemoteStore, notice: Notice) {
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(context)
        val intent = Intent(context, SimletActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(SimletActivity.EXTRA_NOTICE_ID, notice.id)
        val tap = PendingIntent.getActivity(
            context, BAR_ID, intent,
            // 12 起必须点名可变性，不点名构建期就被拦下。
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val built = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notice)
            .setContentTitle(context.getString(R.string.notice_bar_title))
            .setContentText(notice.title)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(BAR_ID, built) }
            .onSuccess { store.noticeNotifiedId = notice.id }
    }

    /** 撤下来。没有挂着的东西时什么都不做。 */
    fun dismiss(context: Context, store: RemoteStore) {
        val notified = store.noticeNotifiedId
        if (notified <= 0) return
        store.noticeNotifiedId = 0
        NotificationManagerCompat.from(context).cancel(BAR_ID)
    }

    /**
     * 频道只在第一次发之前建。
     *
     * IMPORTANCE_DEFAULT：有声音、不抢 heads-up。一条工具类应用的公告不该把人手里的活打断，
     * 而系统创建之后这个档位就改不了了——想要更响的人在系统设置里自己调。
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notice_bar_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notice_bar_channel_desc)
            }
        )
    }
}
