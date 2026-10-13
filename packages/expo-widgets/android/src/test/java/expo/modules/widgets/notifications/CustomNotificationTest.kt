package expo.modules.widgets.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import io.github.jakex7.peek.notification.PeekNotificationViews
import org.junit.Assert.assertEquals
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
class CustomNotificationTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Before
  fun createChannel() {
    manager.createNotificationChannel(NotificationChannel("orders", "Orders", NotificationManager.IMPORTANCE_DEFAULT))
  }

  private fun postDirectly(name: String, id: String, props: Map<String, Any?>) {
    val notification = CustomNotificationBuilder.build(
      context = context,
      state = CustomNotificationState(name, id, props, CustomNotificationOptions(channelId = "orders")),
      views = PeekNotificationViews(RemoteViews(context.packageName, android.R.layout.simple_list_item_1))
    )
    NotificationManagerCompat.from(context).notify(CustomNotificationState.tag(name, id), 0, notification)
  }

  @Test
  fun `getProps reads the active notification and throws after cancel`() {
    postDirectly("OrderNotification", "order-1", mapOf("status" to "posted"))
    val notification = CustomNotification(context, "OrderNotification", "order-1")

    assertEquals(mapOf("status" to "posted"), notification.getProps())

    postDirectly("OrderNotification", "order-1", mapOf("status" to "updated"))

    assertEquals(mapOf("status" to "updated"), notification.getProps())

    notification.cancel()

    assertThrows(CustomNotificationNotFoundException::class.java) {
      notification.getProps()
    }
  }

  @Test
  fun `getProps throws when the notification is not active`() {
    val notification = CustomNotification(context, "OrderNotification", "missing")

    assertThrows(CustomNotificationNotFoundException::class.java) {
      notification.getProps()
    }
  }

  @Test
  fun `factory resolves active notifications into objects`() {
    postDirectly("OrderNotification", "order-1", mapOf("step" to 1))
    postDirectly("OrderNotification", "order-2", mapOf("step" to 2))
    postDirectly("OtherNotification", "order-3", mapOf("step" to 3))
    val factory = CustomNotificationFactory(context, "OrderNotification", layout = "layout")

    val active = factory.getActive()
    assertEquals(listOf("order-1", "order-2"), active.map { it.id }.sorted())
    assertEquals(mapOf("step" to 2), active.first { it.id == "order-2" }.getProps())

    val single = factory.get("order-1")
    assertNotNull(single)
    assertEquals("order-1", single!!.id)
    assertNull(factory.get("missing"))

    factory.cancelAll()
    assertEquals(emptyList<CustomNotification>(), factory.getActive())
    assertEquals(1, manager.activeNotifications.size)
  }

  @Test
  fun `delimiters in names and ids cannot overwrite or cancel another notification`() {
    postDirectly("Orders:EU", "123", mapOf("region" to "EU"))
    postDirectly("Orders", "EU:123", mapOf("region" to "other"))
    postDirectly("Orders%3AEU", "123", mapOf("region" to "escaped"))
    val factory = CustomNotificationFactory(context, "Orders:EU", layout = "layout")
    val other = CustomNotificationFactory(context, "Orders", layout = "layout")
    val escaped = CustomNotificationFactory(context, "Orders%3AEU", layout = "layout")

    assertEquals(3, manager.activeNotifications.size)
    assertEquals(mapOf("region" to "EU"), factory.get("123")!!.getProps())
    assertEquals(mapOf("region" to "other"), other.get("EU:123")!!.getProps())
    assertEquals(mapOf("region" to "escaped"), escaped.get("123")!!.getProps())

    factory.cancelAll()

    assertNull(factory.get("123"))
    assertNotNull(other.get("EU:123"))
    assertNotNull(escaped.get("123"))
    assertEquals(2, manager.activeNotifications.size)
  }
}
