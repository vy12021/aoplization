package com.forceai.android.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.aoplization.annotation.ProxyHandlerMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity: AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    findViewById<View>(android.R.id.content).setOnClickListener {
      // For normal function
      click2LikeItem(Item("test"))
      // For coroutine function
      GlobalScope.launch {
        click2LikeItemCoroutine(Item("test"))
      }
    }
  }

  @ProxyEntry
  @Tag(TAG_LOGIN)
  private fun click2LikeItem(item: Item): Any? {
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item
  }

  @ProxyEntry
  @Tag(TAG_LOGIN)
  private suspend fun click2LikeItemCoroutine(item: Item): Any? {
    delay(1000)
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item
  }

  // For normal function
  private fun _click2LikeItem(item: Item): Any? {
    return DefaultHandler().invoke(ProxyContext(
      MainActivity::class.java.getDeclaredMethod("click2LikeItem", Item::class.java)
    ), object : ProxyContinuation {
      override fun resume(returnValue: Any?): Any? {
        return click2LikeItemProxy(item).let {
          returnValue ?: it
        }
      }
    })
  }

  // For coroutine function
  private suspend fun _click2LikeItemCoroutine(item: Item) = suspendCoroutine<Any?> { continuation ->
    DefaultHandler().invoke(ProxyContext(
      MainActivity::class.java.getDeclaredMethod("click2LikeItem", Item::class.java)
    ), object : ProxyContinuation {
      override fun resume(returnValue: Any?): Any? {
        CoroutineScope(continuation.context).launch {
          click2LikeItemProxyCoroutine(item).let {
            returnValue ?: it
          }.also {
            continuation.resume(it)
          }
        }
        return returnValue
      }
    })
  }

  private fun click2LikeItemProxy(item: Item): Any? {
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return null
  }

  private suspend fun click2LikeItemProxyCoroutine(item: Item) = suspendCoroutine<Any?> { continuation ->
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    CoroutineScope(continuation.context).launch {
      delay(1000)
      continuation.resume(item)
    }
  }

}

class ProxyInvoke(function: () -> Any?): Callable<Any?> {

  private val funcRef = WeakReference(function)

  override fun call(): Any? {
    return funcRef.get()?.invoke()
  }

}

const val TAG_LOGIN = "login"

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Tag(
  val value: String
)

abstract class ProxyHandler {

  abstract fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any?

}

@ProxyHandlerMark
class DefaultHandler: ProxyHandler() {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return when (context.method.getAnnotation(Tag::class.java)?.value) {
      TAG_LOGIN -> {
        checkIfLogin { logged ->
          if (logged) {
            continuation.resume(null)
          }
        }
      }
      else -> null
    }
  }
}

private fun checkIfLogin(callback: (Boolean) -> Unit) {
  callback(true)
}

data class Item(val name: String)