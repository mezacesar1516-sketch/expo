@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package expo.modules.widgets.notifications

import android.app.PendingIntent
import android.content.Context
import androidx.glance.EmittableButton
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.SendBroadcastIntentAction
import androidx.glance.layout.EmittableColumn
import androidx.test.core.app.ApplicationProvider
import expo.modules.widgets.InteractionSource
import expo.modules.widgets.WIDGET_INTERACTION_NOTIFICATION_ID_EXTRA
import expo.modules.widgets.WIDGET_INTERACTION_SOURCE_EXTRA
import expo.modules.widgets.WIDGET_INTERACTION_TARGET_EXTRA
import expo.modules.widgets.WidgetInteraction
import expo.modules.widgets.toGlanceAction
import io.github.jakex7.peek.emittables.peekEmittableNotificationViews
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationInteractionTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  private fun renderButton(name: String, id: String, target: String = "increment"): PendingIntent = runBlocking {
    val action = WidgetInteraction(InteractionSource.Notification(name, id), target).toGlanceAction(context) as SendBroadcastIntentAction
    val root = EmittableColumn().also { column ->
      column.children += EmittableButton().also {
        it.text = "Increment"
        it.modifier = GlanceModifier.clickable(action)
      }
    }
    // Exercise Glance's actual PendingIntent creation, including its default view IDs.
    peekEmittableNotificationViews(context, root)
    checkNotNull(PendingIntent.getBroadcast(context, 0, action.intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
  }

  @Test
  fun `equivalent layouts keep each notification and factory button independent`() {
    val first = renderButton("Orders", "order-1")
    val second = renderButton("Orders", "order-2")
    val otherFactory = renderButton("OtherOrders", "order-1")

    assertNotEquals(first, second)
    assertNotEquals(first, otherFactory)
    assertEquals("order-1", shadowOf(first).savedIntent.getStringExtra(WIDGET_INTERACTION_NOTIFICATION_ID_EXTRA))
    assertEquals("Orders", shadowOf(first).savedIntent.getStringExtra(WIDGET_INTERACTION_SOURCE_EXTRA))
    assertEquals("order-2", shadowOf(second).savedIntent.getStringExtra(WIDGET_INTERACTION_NOTIFICATION_ID_EXTRA))
  }

  @Test
  fun `targets remain independent when occupying the same view position`() {
    val first = renderButton("Orders", "order-1", "increment")
    val second = renderButton("Orders", "order-1", "decrement")

    assertNotEquals(first, second)
    assertEquals("increment", shadowOf(first).savedIntent.getStringExtra(WIDGET_INTERACTION_TARGET_EXTRA))
    assertEquals("decrement", shadowOf(second).savedIntent.getStringExtra(WIDGET_INTERACTION_TARGET_EXTRA))
    assertEquals(first, renderButton("Orders", "order-1", "increment"))
  }

  @Test
  fun `intent identity escapes delimiters in every component`() {
    val first = renderButton("Orders/EU", "123", "increment")
    val second = renderButton("Orders", "EU/123", "increment")
    val third = renderButton("Orders", "EU", "123/increment")
    val escaped = renderButton("Orders%2FEU", "123", "increment")

    assertEquals(4, setOf(first, second, third, escaped).size)
  }
}
