open module com.bhb.android.componentization {
  requires auto.common;
  requires java.base;
  requires com.bhb.android.componentization.annotation;
  requires jdk.compiler;
  requires com.bhb.android.componentization.compiler;
  requires compile.testing;
  requires junit;
  requires truth;
}