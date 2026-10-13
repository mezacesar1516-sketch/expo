package expo.modules.widgets.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import io.github.jakex7.peek.notification.PeekNotificationViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomNotificationBuilderTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Before
  fun createChannels() {
    manager.createNotificationChannel(NotificationChannel("orders", "Orders", NotificationManager.IMPORTANCE_DEFAULT))
  }

  private fun views() = PeekNotificationViews(
    collapsed = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
  )

  private fun build(options: CustomNotificationOptions, props: Map<String, Any?>? = mapOf("status" to "Preparing")): Notification {
    return CustomNotificationBuilder.build(
      context = context,
      state = CustomNotificationState("OrderNotification", "order-1", props, options),
      views = views()
    )
  }

  @Test
  fun `ongoing notification is not auto cancelled and alerts once`() {
    val notification = build(CustomNotificationOptions(ongoing = true, autoCancel = false, alertOnlyOnce = true))

    assertEquals(Notification.FLAG_ONGOING_EVENT, notification.flags and Notification.FLAG_ONGOING_EVENT)
    assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
    assertEquals(Notification.FLAG_ONLY_ALERT_ONCE, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    assertEquals(0L, notification.timeoutAfter)
  }

  @Test
  fun `dismissible notification maps autoCancel and timeout`() {
    val notification = build(
      CustomNotificationOptions(ongoing = false, autoCancel = true, alertOnlyOnce = false, timeoutAfter = 15_000L)
    )

    assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
    assertEquals(Notification.FLAG_AUTO_CANCEL, notification.flags and Notification.FLAG_AUTO_CANCEL)
    assertEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    assertEquals(15_000L, notification.timeoutAfter)
  }

  @Test
  fun `title and text reach the notification`() {
    val notification = build(CustomNotificationOptions(title = "Order #1", text = "Preparing"))

    assertEquals("Order #1", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    assertEquals("Preparing", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  @Test
  fun `custom views are attached`() {
    val notification = build(CustomNotificationOptions())

    @Suppress("DEPRECATION")
    assertNotNull(notification.contentView)
  }

  @Test
  fun `name id props and options round trip through extras`() {
    val options = CustomNotificationOptions(
      channelId = "orders",
      title = "Order #1",
      ongoing = true,
      autoCancel = false,
      timeoutAfter = 42L,
      url = "myapp://orders/1",
      color = "#FF0000"
    )
    val notification = build(options, props = mapOf("status" to "Preparing", "eta" to 15))

    val active = CustomNotificationState.fromNotification(notification)

    assertNotNull(active)
    assertEquals("OrderNotification", active!!.name)
    assertEquals("order-1", active.id)
    assertEquals(mapOf("status" to "Preparing", "eta" to 15), active.props)
    assertEquals(options, active.options)
  }

  @Test
  fun `a foreign notification is not recognized`() {
    val foreign = Notification.Builder(context, "other").setContentTitle("x").build()

    assertNull(CustomNotificationState.fromNotification(foreign))
  }

  @Test
  fun `default channel is created when channelId is missing`() {
    val notification = build(CustomNotificationOptions(channelId = null))

    val channel = manager.getNotificationChannel(notification.channelId)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_LOW, channel!!.importance)
    assertEquals("expo_widgets_notifications", notification.channelId)
  }

  @Test
  fun `explicit channelId is used`() {
    val notification = build(CustomNotificationOptions(channelId = "orders"))

    assertEquals("orders", notification.channelId)
  }

  @Test
  fun `unknown channelId is rejected instead of being dropped by the system`() {
    assertThrows(MissingNotificationChannelException::class.java) {
      build(CustomNotificationOptions(channelId = "missing"))
    }
  }

  @Test
  fun `content intent opens the url`() {
    val notification = build(CustomNotificationOptions(url = "myapp://orders/1"))

    assertNotNull(notification.contentIntent)
  }

  @Test
  fun `tag is unique per name and id`() {
    assertNotEquals(
      CustomNotificationState.tag("OrderNotification", "a"),
      CustomNotificationState.tag("OrderNotification", "b")
    )
    assertNotEquals(
      CustomNotificationState.tag("OrderNotification", "a"),
      CustomNotificationState.tag("OtherNotification", "a")
    )
  }
}
