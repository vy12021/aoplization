package com.forceai.android.app;

import com.forceai.android.aoplization.ProxyContext;
import com.forceai.android.aoplization.ProxyContinuation;
import com.forceai.android.aoplization.ProxyHandler;
import com.forceai.android.aoplization.annotation.MainProxyHandler;
import com.forceai.android.aoplization.annotation.ProxyEntry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClass {

  @Nullable
  @ProxyEntry(handlerKey = "3")
  private Object memberFun(String[] args) {
    return "";
  }

  @Nullable
  @ProxyEntry(handlerKey = "3")
  private static Object staticFuc(String arg) {
    return null;
  }

  @MainProxyHandler(mark = "3")
  public static class DefaultHandler extends ProxyHandler {

    @Nullable
    @Override
    public Object invoke(@NotNull ProxyContext context, @NotNull ProxyContinuation continuation) {
      return continuation.resume(null);
    }
  }

}
