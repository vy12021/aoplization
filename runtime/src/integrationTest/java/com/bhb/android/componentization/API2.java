package com.forceai.android.aoplization;

import com.forceai.android.aoplization.annotation.Api;

import java.io.Serializable;

@Api
public interface API2<T extends Serializable, N extends Number> extends API {

  <V extends Serializable> T get(V input, N number);

}
