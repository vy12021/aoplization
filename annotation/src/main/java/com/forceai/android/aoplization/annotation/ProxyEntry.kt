package com.forceai.android.aoplization.annotation

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@ProxyUse
annotation class ProxyEntry(
  val clazz: KClass<*> = None::class,
)

interface None