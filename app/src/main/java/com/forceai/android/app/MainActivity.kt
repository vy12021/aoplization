package com.forceai.android.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.forceai.android.aoplization.ProxyContext
import com.forceai.android.aoplization.ProxyContinuation
import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.aoplization.annotation.ProxyHandlerMark

class MainActivity: AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    findViewById<View>(android.R.id.content).setOnClickListener {
      click2LikeItem(Item("test"))
    }
  }

  @ProxyEntry
  @Tag(TAG_LOGIN)
  private fun click2LikeItem(item: Item) {
    Toast.makeText(this, "me", Toast.LENGTH_SHORT).show()
  }

  private fun _click2LikeItem(item: Item) {
    DefaultHandler().invoke(ProxyContext(
      MainActivity::class.java.getDeclaredMethod("click2LikeItem", Item::class.java)
    ), object : ProxyContinuation {
      override fun resume() {
        click2LikeItemProxy(item)
      }
    })
  }

  private fun click2LikeItemProxy(item: Item) {
    Toast.makeText(this, "me", Toast.LENGTH_SHORT).show()
  }

}

const val TAG_LOGIN = "login"

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Tag(
  val value: String
)

abstract class ProxyHandler {

  abstract fun invoke(context: ProxyContext, continuation: ProxyContinuation)

}

@ProxyHandlerMark
class DefaultHandler: ProxyHandler() {
  override fun invoke(context: ProxyContext, continuation: ProxyContinuation) {
    when (context.method.getAnnotation(Tag::class.java)?.value) {
      TAG_LOGIN -> {
        checkIfLogin { logged ->
          if (logged) {
            continuation.resume()
          }
        }
      }
    }
  }
}

private fun checkIfLogin(callback: (Boolean) -> Unit) {
  callback(true)
}

data class Item(val name: String)