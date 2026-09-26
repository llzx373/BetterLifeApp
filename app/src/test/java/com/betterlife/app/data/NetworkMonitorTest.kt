package com.betterlife.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线判定策略:hasNetwork && validated(策略理由见 NetworkMonitor.kt 文件头注释)。
 * 只测纯函数;callbackFlow 的注册/注销走真机手动验证(飞行模式)。
 */
class NetworkMonitorTest {

    @Test
    fun `有网络且已验证时在线`() {
        assertTrue(isOnline(hasNetwork = true, validated = true))
    }

    @Test
    fun `没有任何网络时离线`() {
        assertFalse(isOnline(hasNetwork = false, validated = false))
    }

    @Test
    fun `无网络时 validated 为真仍离线`() {
        // 防御非法组合:validated 不可能脱离网络单独成立,真出现也不该报在线
        assertFalse(isOnline(hasNetwork = false, validated = true))
    }

    @Test
    fun `连上网络但未验证时离线`() {
        // captive portal(酒店/校园网登录页)就是这种:连上了但出不了网,AI 问答不可用
        assertFalse(isOnline(hasNetwork = true, validated = false))
    }
}
