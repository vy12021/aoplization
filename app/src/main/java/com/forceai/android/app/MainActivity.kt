package com.forceai.android.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.aoplization.annotation.ProxyHandlerMark
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity: AppCompatActivity() {

  private val proxyAccompany by lazy {
    MainActivity_ProxyAccompany(this)
  }

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

  /**
   * For normal function
   */
  @ProxyEntry
  @Tag(TAG_LOGIN)
  private fun click2LikeItem(item: Item): Any? {
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item
  }

  /**
   * ********** 使用插件将实现转换为调用伴生类同方法 ***********
   * ************************ ↓ **************************
   * ************************ ↓ **************************
   */
  private fun _click2LikeItem(item: Item): Any? {
    return proxyAccompany.click2LikeItem(item)
  }

  /**
   * For coroutine function
   */
  @ProxyEntry
  @Tag(TAG_LOGIN)
  private suspend fun click2LikeItemCoroutine(item: Item): Any? {
    delay(1000)
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item
  }

  /**
   * ********** 使用插件将实现转换为调用伴生类同方法 ***********
   * ************************ ↓ **************************
   * ************************ ↓ **************************
   */
  private suspend fun _click2LikeItemCoroutine(item: Item): Any? {
    return proxyAccompany.click2LikeItemCoroutine(item)
  }

  /**
   * For normal function
   * 使用插件生成的两个代理方法，方法内容为原调用的copy，为public方法，供伴生类反向调用
   */
  fun click2LikeItemProxy(item: Item): Any? {
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item.name
  }

  /**
   * For coroutine function
   * 使用插件生成的两个代理方法，方法内容为原调用的copy，为public方法，供伴生类反向调用
   */
  suspend fun click2LikeItemProxyCoroutine(item: Item) = coroutineScope<Any?> {
    Toast.makeText(this@MainActivity, item.name, Toast.LENGTH_SHORT).show()
    delay(1000)
    return@coroutineScope item.name
  }

}

/**
 * 此为使用注解处理器生成的伴生辅助调用类，目的是简化字节码修改复杂度，并且提前通过元注解标明一些调用点信息
 */
@ProxyHostMeta("com.forceai.android.app.MainActivity")
class MainActivity_ProxyAccompany(val target: MainActivity) {

  // For normal function
  fun click2LikeItem(item: Item): Any? {
    return DefaultHandler().invoke(ProxyContext(
      target.javaClass.getDeclaredMethod("click2LikeItem",
        Class.forName("com.forceai.android.app.Item")
      )
    ), object : ProxyContinuation {
      override fun resume(returnValue: Any?): Any? {
        return click2LikeItemProxy(item).let {
          returnValue ?: it
        }
      }
    })
  }

  // For coroutine function
  suspend fun click2LikeItemCoroutine(item: Item) = coroutineScope {
    return@coroutineScope suspendCoroutine<Any?> { continuation ->
      DefaultHandler().invoke(ProxyContext(
        target.javaClass.getDeclaredMethod("click2LikeItem",
          Class.forName("com.forceai.android.app.Item")
        )
      ), object : ProxyContinuation {
        override fun resume(returnValue: Any?): Any? {
          this@coroutineScope.launch {
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
  }

  @ProxyHostMethodMeta("click2LikeItem", ["com.forceai.android.app.Item"])
  private fun click2LikeItemProxy(item: Item): Any? {
    // TODO inject associated host invocation in phase of transforming
    // return target.click2LikeItemProxy(item)
    return null
  }

  @ProxyHostMethodMeta("click2LikeItem", ["com.forceai.android.app.Item"])
  private suspend fun click2LikeItemProxyCoroutine(item: Item) = coroutineScope<Any?> {
    // TODO inject associated host invocation in phase of transforming
    target.click2LikeItemProxyCoroutine(item)
    // return null
  }

}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ProxyHostMeta(
  // com.forceai.android.app.MainActivity
  val clazz: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ProxyHostMethodMeta(
  // click2LikeItem
  val name: String,
  // [com.forceai.android.app.Item]
  val params: Array<String>
)

/**
 * 关于是否有可能产生的内存泄露处理，还未相当好办法，调用点可能被外部持有造成泄露
 */
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