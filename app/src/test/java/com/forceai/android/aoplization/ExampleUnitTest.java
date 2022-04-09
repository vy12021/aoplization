package com.forceai.android.aoplization;

import com.forceai.android.app.FunAAPI;

import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
  @Test
  public void addition_isCorrect() {
    LazyDelegateImpl<FunAAPI> lazyDelegate = new LazyDelegateImpl<FunAAPI>() {};
    lazyDelegate.create();
    lazyDelegate.get();
  }
}