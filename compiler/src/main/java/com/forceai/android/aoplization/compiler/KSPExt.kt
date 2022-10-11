@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.forceai.android.aoplization.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.jvmErasure

fun KSFunctionDeclaration.hasReturnType(
) = this.returnType?.resolve()?.declaration?.qualifiedName?.asString() != Unit::class.qualifiedName

@KspExperimental
fun <T : Annotation> KSAnnotated.getClassFieldsByType(
  annotationKClass: KClass<T>
): Sequence<Map<KCallable<*>, Any>> {
  return this.annotations.filter {
    it.shortName.getShortName() == annotationKClass.simpleName
        && it.annotationType.resolve().declaration.qualifiedName?.asString()
      .equals(annotationKClass.qualifiedName)
  }.map { ksAnnotation ->
    mutableMapOf<KCallable<*>, Any>().also { result ->
      ksAnnotation.arguments.forEach { arg ->
        annotationKClass.declaredMemberProperties.find {
          arg.name?.asString() == it.name
        }?.also { property ->
          if (arg.value is KSType) {
            result[property] = (arg.value as KSType).declaration.qualifiedName!!.asString()
          } else if (arg.value is List<*>
            && property.returnType.arguments.first().type?.jvmErasure == KClass::class
          ) {
            (arg.value as List<*>).filterIsInstance<KSType>().map {
              it.declaration.qualifiedName!!.asString()
            }.also {
              result[property] = it
            }
          }
        }
      }
    }
  }
}