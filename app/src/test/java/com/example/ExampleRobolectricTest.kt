package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.IntentType
import com.example.service.CommandRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Shadab AI Jarvis", appName)
  }

  @Test
  fun `test command router alarm parsing in hindi`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val router = CommandRouter(context)
    val result = router.processCommand("सुबह 7 बजे का अलार्म लगाओ")
    assertEquals(IntentType.ALARM, result.intentType)
    assertTrue(result.spokenResponseHindi.contains("अलार्म"))
  }

  @Test
  fun `test command router timer parsing in hinglish`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val router = CommandRouter(context)
    val result = router.processCommand("5 minute ka timer lagao")
    assertEquals(IntentType.TIMER, result.intentType)
    assertTrue(result.spokenResponseHindi.contains("टाइमर"))
  }

  @Test
  fun `test command router call requires confirmation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val router = CommandRouter(context)
    val result = router.processCommand("कॉल करो 9876543210")
    assertEquals(IntentType.CALL_CONFIRM, result.intentType)
    assertNotNull(result.confirmationAction)
    assertEquals("9876543210", result.confirmationAction?.recipient)
  }
}
