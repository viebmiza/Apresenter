package org.churchpresenter.app.churchpresenter

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guard that stops a second copy of the app running against the same settings and library.
 *
 * It binds a loopback port and holds it for the process's life, so each test takes a port the OS
 * has just told us is free and gives the lock back afterwards — a held port would otherwise outlive
 * the test and fail every later one.
 */
class SingleInstanceLockTest {

    private val propertyName = "churchpresenter.singleInstancePort"
    private var previous: String? = null

    /** A port the OS reports free, so this never collides with whatever else is on the machine. */
    private fun freePort(): Int =
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    private fun useLockPort(port: Int) {
        previous = System.getProperty(propertyName)
        System.setProperty(propertyName, port.toString())
    }

    @AfterTest
    fun releaseEverything() {
        releaseSingleInstanceLock()
        previous.let { if (it == null) System.clearProperty(propertyName) else System.setProperty(propertyName, it) }
    }

    @Test
    fun `the first instance takes the lock`() {
        useLockPort(freePort())

        assertTrue(acquireSingleInstanceLock(), "nothing else holds it, so it is ours")
    }

    @Test
    fun `a second instance is refused`() {
        useLockPort(freePort())
        assertTrue(acquireSingleInstanceLock())

        assertFalse(acquireSingleInstanceLock(), "the port is held, which is how the second copy knows")
    }

    @Test
    fun `releasing it lets the next instance start`() {
        val port = freePort()
        useLockPort(port)
        assertTrue(acquireSingleInstanceLock())

        releaseSingleInstanceLock()

        assertTrue(acquireSingleInstanceLock(), "the port is free again once it is given back")
    }

    @Test
    fun `a port held by something else refuses the lock`() {
        val port = freePort()
        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use {
            useLockPort(port)

            assertFalse(acquireSingleInstanceLock(), "whoever holds the port owns the lock")
        }
    }
}
