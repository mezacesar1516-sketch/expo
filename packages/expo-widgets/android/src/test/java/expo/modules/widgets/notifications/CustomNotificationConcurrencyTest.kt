package expo.modules.widgets.notifications

import android.app.NotificationManager
import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import io.github.jakex7.peek.notification.PeekNotificationViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomNotificationConcurrencyTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private var beforeRender: suspend (String) -> Unit = {}
  private val notifications = CustomNotifications { _, state ->
    beforeRender(state.id)
    PeekNotificationViews(RemoteViews(context.packageName, android.R.layout.simple_list_item_1))
  }

  private suspend fun post(id: String = "order-1", name: String = "Orders", count: Int = 0) {
    notifications.post(context, CustomNotificationState(name, id, mapOf("count" to count), CustomNotificationOptions()))
  }

  private suspend fun increment(id: String = "order-1"): Boolean = notifications.update(context, "Orders", id) { active ->
    val count = (active.props!!["count"] as Number).toInt()
    active.copy(props = mapOf("count" to count + 1))
  }

  private fun count(id: String = "order-1"): Int? =
    (CustomNotificationState.find(context, "Orders", id)?.props?.get("count") as? Number)?.toInt()

  @Test
  fun `overlapping presses read the preceding update instead of losing an increment`() = runBlocking {
    withTimeout(5000) {
      post()
      val release = CompletableDeferred<Unit>()
      var renders = 0
      beforeRender = {
        renders++
        release.await()
      }

      val first = async(start = CoroutineStart.UNDISPATCHED) { increment() }
      val second = async(start = CoroutineStart.UNDISPATCHED) { increment() }
      assertEquals(1, renders)
      assertFalse(first.isCompleted)
      assertFalse(second.isCompleted)

      release.complete(Unit)

      assertTrue(first.await())
      assertTrue(second.await())
      assertEquals(2, renders)
      assertEquals(2, count())
    }
  }

  @Test
  fun `cancel stops rendering and queued updates but permits a new post`() = runBlocking {
    withTimeout(5000) {
      post()
      val release = CompletableDeferred<Unit>()
      beforeRender = { release.await() }
      val first = async(start = CoroutineStart.UNDISPATCHED) { runCatching { increment() } }
      val queued = async(start = CoroutineStart.UNDISPATCHED) { runCatching { increment() } }

      notifications.cancel(context, "Orders", "order-1")

      assertNull(count())
      assertTrue(first.await().exceptionOrNull() is CustomNotificationCancelledException)
      assertTrue(queued.await().exceptionOrNull() is CustomNotificationCancelledException)
      val replacement = async(start = CoroutineStart.UNDISPATCHED) { post(count = 10) }
      release.complete(Unit)

      replacement.await()
      assertEquals(10, count())
      assertTrue(increment())
      assertEquals(11, count())
    }
  }

  @Test
  fun `cancelAll invalidates an initial post before it becomes active and preserves other factories`() = runBlocking {
    withTimeout(5000) {
      post(id = "other", name = "OtherOrders")
      val release = CompletableDeferred<Unit>()
      beforeRender = { release.await() }
      val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { post() } }
      assertNull(count())

      notifications.cancelAll(context, "Orders")
      release.complete(Unit)

      assertTrue(pending.await().exceptionOrNull() is CustomNotificationCancelledException)
      assertNull(count())
      assertNotNull(CustomNotificationState.find(context, "OtherOrders", "other"))
    }
  }

  @Test
  fun `a system dismissal during rendering prevents the update from reposting`() = runBlocking {
    withTimeout(5000) {
      post()
      val release = CompletableDeferred<Unit>()
      beforeRender = { release.await() }
      val pending = async(start = CoroutineStart.UNDISPATCHED) { increment() }

      manager.cancel(CustomNotificationState.tag("Orders", "order-1"), 0)
      release.complete(Unit)

      assertFalse(pending.await())
      assertNull(count())
    }
  }

  @Test
  fun `a slow notification does not block updates to another id`() = runBlocking {
    withTimeout(5000) {
      post("slow")
      post("fast")
      val release = CompletableDeferred<Unit>()
      beforeRender = { id -> if (id == "slow") release.await() }
      val slow = async(start = CoroutineStart.UNDISPATCHED) { increment("slow") }

      assertTrue(increment("fast"))
      assertEquals(1, count("fast"))
      assertFalse(slow.isCompleted)
      release.complete(Unit)
      assertTrue(slow.await())
    }
  }

  @Test
  fun `a new post can complete before a cancelled render finishes`() = runBlocking {
    withTimeout(5000) {
      post()
      val release = CompletableDeferred<Unit>()
      beforeRender = { withContext(NonCancellable) { release.await() } }
      val stale = async(start = CoroutineStart.UNDISPATCHED) { runCatching { increment() } }

      notifications.cancel(context, "Orders", "order-1")
      beforeRender = {}
      post(count = 10)

      assertEquals(10, count())
      assertFalse(stale.isCompleted)
      release.complete(Unit)
      assertTrue(stale.await().exceptionOrNull() is CustomNotificationCancelledException)
      assertEquals(10, count())
      assertTrue(increment())
      assertEquals(11, count())
    }
  }

  @Test
  fun `cancelling a queued coroutine preserves serialization for later updates`() = runBlocking {
    withTimeout(5000) {
      post()
      val release = CompletableDeferred<Unit>()
      var renders = 0
      beforeRender = {
        renders++
        release.await()
      }
      val first = async(start = CoroutineStart.UNDISPATCHED) { increment() }
      val abandoned = async(start = CoroutineStart.UNDISPATCHED) { increment() }
      val next = async(start = CoroutineStart.UNDISPATCHED) { increment() }

      abandoned.cancelAndJoin()

      assertEquals(1, renders)
      assertFalse(next.isCompleted)
      release.complete(Unit)
      assertTrue(first.await())
      assertTrue(next.await())
      assertEquals(2, count())
    }
  }

  @Test
  fun `failed composition lets the next update proceed without changing active props`() = runBlocking {
    withTimeout(5000) {
      post()
      beforeRender = { error("composition failed") }

      assertTrue(runCatching { increment() }.isFailure)
      assertEquals(0, count())

      beforeRender = {}
      assertTrue(increment())
      assertEquals(1, count())
    }
  }
}
