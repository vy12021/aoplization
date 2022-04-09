package com.forceai.android.aoplization;

import com.forceai.android.aoplization.annotation.Api;

import java.io.Serializable;

@Api
public interface TestAPI extends API2<Boolean, Integer> {

  <T extends Serializable> T doSomething(T p);

}