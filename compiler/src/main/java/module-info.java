module aoplization.compiler {
  requires java.base;
  requires jdk.compiler;
  requires kotlin.stdlib;
  requires com.squareup.javapoet;
  requires com.squareup.kotlinpoet;
  requires com.squareup.kotlinpoet.ksp;
  requires aoplizatioin.annotation;
  requires symbol.processing.api;
  requires kotlin.reflect;
}