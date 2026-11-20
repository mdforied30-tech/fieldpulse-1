package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.PortalAuthStore
import com.example.auth.PortalSession
import com.example.data.model.AppMode
import com.example.data.model.PermissionSyncStatus
import com.example.data.repository.FieldPulseRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FieldPulse", appName)
  }

  @Test
  fun `admin credentials strictly enforce fixed super admin email and password`() {
    assertTrue(PortalAuthStore.verifyAdminCredentials("mdforied30@gmail.com", "F@'*-$()f1"))
    assertTrue(PortalAuthStore.verifyAdminCredentials("MDFORIED30@GMAIL.COM", "F@'*-$()f1"))
    assertFalse(PortalAuthStore.verifyAdminCredentials("other@gmail.com", "F@'*-$()f1"))
    assertFalse(PortalAuthStore.verifyAdminCredentials("mdforied30@gmail.com", "wrongpass"))
  }

  @Test
  fun `worker registration authentication and saved session persist cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val reg = PortalAuthStore.registerWorkerAccount(
      context = context,
      name = "Rafiq Islam",
      email = "rafiq@gmail.com",
      phone = "01700000000",
      department = "Electrical",
      password = "pass1234"
    )
    assertTrue(reg.isSuccess)
    assertTrue(PortalAuthStore.authenticateWorker(context, "rafiq@gmail.com", "pass1234").isSuccess)
    assertTrue(PortalAuthStore.authenticateWorker(context, "rafiq@gmail.com", "badpass").isFailure)

    val session = PortalSession(
      loggedIn = true,
      mode = AppMode.WORKER,
      email = "rafiq@gmail.com",
      name = "Rafiq Islam",
      phone = "01700000000",
      department = "Electrical"
    )
    PortalAuthStore.saveActiveSession(context, session)
    val loaded = PortalAuthStore.loadSession(context)
    assertTrue(loaded.loggedIn)
    assertEquals(AppMode.WORKER, loaded.mode)
    assertEquals("rafiq@gmail.com", loaded.email)

    PortalAuthStore.clearActiveSession(context)
    assertFalse(PortalAuthStore.loadSession(context).loggedIn)
  }

  @Test
  fun `permission sync computes ALL_GRANTED only when all core sensors are enabled`() {
    assertEquals(
      PermissionSyncStatus.ALL_GRANTED,
      FieldPulseRepository.computePermissionStatus(true, true, true)
    )
    assertEquals(
      PermissionSyncStatus.PARTIAL,
      FieldPulseRepository.computePermissionStatus(true, false, true)
    )
    assertEquals(
      PermissionSyncStatus.PENDING,
      FieldPulseRepository.computePermissionStatus(false, false, false)
    )
  }
}
