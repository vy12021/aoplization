package com.forceai.android.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.ProxyHandler
import com.forceai.android.aoplization.annotation.MainProxyHandler
import com.forceai.android.aoplization.annotation.ProxyEntry
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity: AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    findViewById<View>(android.R.id.content).setOnClickListener {
      // For normal function
      Toast.makeText(this, "模拟方法权限，会在3秒后自动resume", Toast.LENGTH_SHORT).show()
      click2LikeItem(Item("test")) {
        Toast.makeText(this, "Hello Android", Toast.LENGTH_SHORT).show()
        it.name
      }
      // For coroutine function
//      GlobalScope.launch {
//        click2LikeItemCoroutine(Item("test"))
//      }
    }
  }

  /**
   * For normal function
   */
  @ProxyEntry("DefaultHandler2")
  @Tag(TAG_LOGIN, tags = ["aaaaaaaaaaa", "bbbbbbbbbb", "ccccccccccccc", "ddddddddddddd", "eeeeeeeeeee"])
  @Mark(TAG_LOGIN, marks = ["aaaaaaaaaaa", "bbbbbbbbbb", "ccccccccccccc", "ddddddddddddd", "eeeeeeeeeee"])
  private fun click2LikeItem(item: Item, @StringRes id: Int = 0, array: Array<Item> = arrayOf(), vararg arg: String = arrayOf(), block: (Item) -> String): Any? {
    return block.invoke(item)
  }

  /**
   * ********** 使用插件将实现转换为调用伴生类同方法 加前缀_作为在演示时做区分 ***********
   * ********************************** ↓ **************************************
   * ********************************** ↓ **************************************
   */
//  private fun _click2LikeItem(item: Item, @StringRes id: Int = 0, array: Array<Item> = arrayOf(), vararg arg: String = arrayOf(), block: (Item) -> String): Any? {
//    return proxyAccompany.click2LikeItem(item, id, array, p3 = arg, block)
//  }

  /**
   * For coroutine function
   */
  // @ProxyEntry
  @Tag(TAG_LOGIN)
  private suspend fun click2LikeItemCoroutine(item: Item): Any? {
    delay(1000)
    Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show()
    return item
  }

  /**
   * ********** 使用插件将实现转换为调用伴生类同方法 加前缀_作为在演示时做区分 ***********
   * ********************************** ↓ **************************************
   * ********************************** ↓ **************************************
   */
//  private suspend fun _click2LikeItemCoroutine(item: Item): Any? {
//    return proxyAccompany.click2LikeItemCoroutine(item)
//  }

  /**
   * For normal function
   * 使用插件生成的两个代理方法，方法内容为原调用的copy，为public方法，供伴生类反向调用
   */
  fun click2LikeItemProxy(item: Item, @StringRes id: Int = 0, array: Array<Item> = arrayOf(), vararg arg: String = arrayOf(), block: (Item) -> String): Any? {
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
class _MainActivity_ProxyAccompany(val target: MainActivity) {

  // For normal function
  fun click2LikeItem(item: Item): Any? {
    return MainDefaultHandler().invoke(ProxyContext(
      MainActivity::click2LikeItemProxy.annotations.toTypedArray()
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
      MainDefaultHandler().invoke(ProxyContext(
        MainActivity::click2LikeItemProxy.annotations.toTypedArray()
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

@MainProxyHandler("DefaultHandler2")
class DefaultHandler2: ProxyHandler() {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return when (context.annotations.firstOrNull()) {
      is Tag -> {
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

//@MainProxyHandler
class MainDefaultHandler: ProxyHandler() {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation): Any? {
    return when (context.annotations.firstOrNull()) {
      is Tag -> {
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

const val TAG_LOGIN = "login"

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Tag(
  val value: String,
  val tags: Array<String> = []
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Mark(
  val value: String,
  val marks: Array<String> = []
)

private fun checkIfLogin(callback: (Boolean) -> Any): Any? {
  GlobalScope.launch {
    delay(3000)
    withContext(Dispatchers.Main.immediate) {
      callback(true)
    }
  }
  return null
}

data class Item(val name: String)