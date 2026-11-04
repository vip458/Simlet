package com.qianxi.simlet.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 委托档位这条判断钉在纯函数上测：红米实测服务端报的是 api=13、patch=6，
 * 上一版拿 13 去比 130600，于是这一行在任何机器上都是灰的。
 */
class DelegateReadyTest {

    @Test
    fun `13 的小版本要到 6 才算支持`() {
        assertFalse(delegateReady(13, 5))
        assertTrue(delegateReady(13, 6))
        assertTrue(delegateReady(13, 7))
    }

    @Test
    fun `更高的 API 号不再看小版本`() {
        assertTrue(delegateReady(14, 0))
    }

    @Test
    fun `更低的 API 号一律不算`() {
        assertFalse(delegateReady(12, 9))
        assertFalse(delegateReady(1, 0))
    }

    @Test
    fun `attach 回包还没到时读到的负数不算`() {
        assertFalse(delegateReady(-1, -1))
        assertFalse(delegateReady(13, -1))
    }
}
