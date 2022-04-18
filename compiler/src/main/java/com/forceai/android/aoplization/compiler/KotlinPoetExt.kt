@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.forceai.android.aoplization.compiler

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder {
  val propertySpecs = properties.map { p -> p.toBuilder().initializer(p.name).build() }
  val parameters = propertySpecs.map { ParameterSpec.builder(it.name, it.type).build() }
  val constructor = FunSpec.constructorBuilder()
    .addParameters(parameters)
    .build()

  return this
    .primaryConstructor(constructor)
    .addProperties(propertySpecs)
}

internal fun KSFunctionDeclaration.invokeArgList(
  explicit: Boolean = true
) = if (parameters.isNotEmpty()) parameters.iterator().let {
    val argsList = StringBuilder()
    while (it.hasNext()) {
      val parameterName = it.next().name?.getShortName()
      if (explicit) {
        argsList.append("$parameterName = $parameterName")
      } else {
        argsList.append(parameterName)
      }
      if (it.hasNext()) argsList.append(", ")
    }
    argsList.also { it.trimToSize() }.toString()
  } else ""