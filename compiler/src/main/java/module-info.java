module aoplization.compiler {
  requires java.base;
  requires jdk.compiler;
  requires kotlin.stdlib;
  requires com.squareup.javapoet;
  requires com.squareup.kotlinpoet;
  requires com.squareup.kotlinpoet.ksp;
  requires com.google.auto.common;
  requires com.google.auto.service;
  requires aoplizatioin.annotation;
  requires net.ltgt.gradle.incap;
  requires org.jetbrains.annotations;
  requires symbol.processing.api;
  requires kotlin.reflect;
}