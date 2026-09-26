// 全局在线状态:全项目唯一的网络检测点,供壳层离线横幅消费。
//
// 策略:`isOnline = hasNetwork && validated`。
// 横幅要回答的问题是「AI 问答还能不能用」,所以真正要紧的是网络可达性,
// 而不只是「连上了某个网络」—— 连上 captive portal(酒店/校园网登录页)时
// hasNetwork 为真但出不了网,只按 hasNetwork 判定会漏报。
// 代价:部分厂商 ROM 上报 NET_CAPABILITY_VALIDATED 较慢,刚连上网络的几秒内
// 可能闪一下横幅;UI 侧初始值给 true 兜底,启动时不闪。我们认为「刚连上时
// 谨慎提示」优于「portal 下漏报」,故保留 && validated。
package com.betterlife.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** 当前是否在线。先发一次快照,再跟随系统回调更新,重复值不重复发射。 */
    val online: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshot())
            }

            override fun onLost(network: Network) {
                trySend(snapshot())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(snapshot())
            }
        }
        trySend(snapshot())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun snapshot(): Boolean {
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        return isOnline(
            hasNetwork = capabilities != null,
            validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        )
    }
}

/** 纯函数,便于 JVM 单测;策略见文件头注释。 */
internal fun isOnline(hasNetwork: Boolean, validated: Boolean): Boolean = hasNetwork && validated
