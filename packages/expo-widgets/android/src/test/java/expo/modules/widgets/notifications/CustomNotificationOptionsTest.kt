package expo.modules.widgets.notifications

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.facebook.react.bridge.JavaOnlyMap
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.runtime.Runtime
import expo.modules.kotlin.types.ConverterContext
import expo.modules.kotlin.types.convert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomNotificationOptionsTest {
  private val converterContext = object : ConverterContext {
    override val applicationContext = ApplicationProvider.getApplicationContext<Context>()
    override val runtime: Runtime? = null
  }

  private inline fun <reified T> fromBridge(values: Map<String, Any?>): T {
    val entries = values.flatMap { (key, value) -> listOf(key, value) }.toTypedArray()
    return convert(JavaOnlyMap.of(*entries), converterContext)
  }

  @Test
  fun `record conversion reads every supported key`() {
    val options = fromBridge<CustomNotificationOptions>(
      mapOf(
        "channelId" to "orders",
        "title" to "Order #1",
        "text" to "Preparing",
        "ongoing" to true,
        "autoCancel" to false,
        "alertOnlyOnce" to false,
        "timeoutAfter" to 15000.0,
        "url" to "myapp://orders/1",
        "smallIcon" to "ic_order",
        "color" to "#FF0000"
      )
    )

    assertEquals(
      CustomNotificationOptions(
        channelId = "orders",
        title = "Order #1",
        text = "Preparing",
        ongoing = true,
        autoCancel = false,
        alertOnlyOnce = false,
        timeoutAfter = 15000L,
        url = "myapp://orders/1",
        smallIcon = "ic_order",
        color = "#FF0000"
      ),
      options
    )
  }

  @Test
  fun `record conversion uses defaults for missing keys`() {
    assertEquals(CustomNotificationOptions(), fromBridge<CustomNotificationOptions>(emptyMap()))
  }

  @Test
  fun `merge overrides only the keys that are present`() {
    val stored = CustomNotificationOptions(
      channelId = "orders",
      title = "Order #1",
      ongoing = true,
      autoCancel = false,
      timeoutAfter = 42L
    )

    val overrides = fromBridge<CustomNotificationUpdateOptions>(
      mapOf("ongoing" to false, "autoCancel" to true, "timeoutAfter" to null)
    )
    val merged = stored.merge(overrides)

    assertEquals(stored, stored.merge(fromBridge<CustomNotificationUpdateOptions>(emptyMap())))
    assertEquals(
      stored.copy(ongoing = false, autoCancel = true, timeoutAfter = null),
      merged
    )
  }

  @Test
  fun `update record can clear nullable fields without resetting omitted booleans`() {
    val stored = CustomNotificationOptions(
      channelId = "orders",
      title = "Order #1",
      text = "Preparing",
      ongoing = true,
      autoCancel = false,
      alertOnlyOnce = false,
      timeoutAfter = 42L,
      url = "myapp://orders/1",
      smallIcon = "ic_order",
      color = "#FF0000"
    )
    val overrides = fromBridge<CustomNotificationUpdateOptions>(
      mapOf(
        "channelId" to null,
        "title" to null,
        "text" to null,
        "timeoutAfter" to null,
        "url" to null,
        "smallIcon" to null,
        "color" to null
      )
    )

    assertEquals(
      CustomNotificationOptions(ongoing = true, autoCancel = false, alertOnlyOnce = false),
      stored.merge(overrides)
    )
  }

  @Test
  fun `update record converts numeric timeout and false flags`() {
    val overrides = fromBridge<CustomNotificationUpdateOptions>(
      mapOf("timeoutAfter" to 15000.0, "autoCancel" to false, "alertOnlyOnce" to false)
    )

    assertEquals(
      CustomNotificationOptions(timeoutAfter = 15000L, autoCancel = false, alertOnlyOnce = false),
      CustomNotificationOptions().merge(overrides)
    )
  }

  @Test
  fun `record conversion rejects invalid option types for posts and updates`() {
    listOf(
      mapOf("ongoing" to "true"),
      mapOf("timeoutAfter" to "15000"),
      mapOf("title" to false),
      mapOf("autoCancel" to null)
    ).forEach { values ->
      assertThrows(CodedException::class.java) {
        fromBridge<CustomNotificationOptions>(values)
      }
      assertThrows(CodedException::class.java) {
        fromBridge<CustomNotificationUpdateOptions>(values)
      }
    }
  }

  @Test
  fun `bundle parcel round trip keeps every field including null and zero timeout`() {
    val options = CustomNotificationOptions(
      channelId = "orders",
      title = "Order #1",
      text = "Preparing",
      ongoing = true,
      autoCancel = false,
      alertOnlyOnce = false,
      timeoutAfter = 15000L,
      url = "myapp://orders/1",
      smallIcon = "ic_order",
      color = "#FF0000"
    )

    listOf(options, CustomNotificationOptions(), options.copy(timeoutAfter = 0L)).forEach { expected ->
      val parcel = Parcel.obtain()
      try {
        parcel.writeBundle(expected.toBundle())
        parcel.setDataPosition(0)
        val restored = checkNotNull(parcel.readBundle())

        assertEquals(expected, CustomNotificationOptions.fromBundle(restored))
      } finally {
        parcel.recycle()
      }
    }

    assertEquals(CustomNotificationOptions(), CustomNotificationOptions.fromBundle(Bundle.EMPTY))
  }
}
