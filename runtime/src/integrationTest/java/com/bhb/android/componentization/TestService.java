package com.forceai.android.aoplization;

import java.io.Serializable;

class TestService implements TestAPI {

  @Override
  public <T extends Serializable> T doSomething(T p) {
    return null;
  }

  @Override
  public <V extends Serializable> Boolean get(V input, Integer number) {
    return true;
  }

}
