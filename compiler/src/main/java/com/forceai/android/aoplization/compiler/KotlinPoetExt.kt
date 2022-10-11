@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.forceai.android.aoplization.compiler

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName

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

fun addValueToBlock(value: Any, member: CodeBlock.Builder) {
  when (value) {
    is List<*> -> {
      // Array type
      member.add("arrayOf(⇥⇥")
      value.forEachIndexed { index, innerValue ->
        if (index > 0) member.add(", ")
        addValueToBlock(innerValue!!, member)
      }
      member.add("⇤⇤)")
    }
    is KSType -> {
      val unwrapped = value.unwrapTypeAlias()
      val isEnum = (unwrapped.declaration as KSClassDeclaration).classKind == ClassKind.ENUM_ENTRY
      if (isEnum) {
        val parent = unwrapped.declaration.parentDeclaration as KSClassDeclaration
        val entry = unwrapped.declaration.simpleName.getShortName()
        member.add("%T.%L", parent.toClassName(), entry)
      } else {
        member.add("%T::class", unwrapped.toClassName())
      }
    }
    is KSName ->
      member.add(
        "%T.%L", ClassName.bestGuess(value.getQualifier()),
        value.getShortName()
      )
    is KSAnnotation -> member.add("%L", value.toAnnotationSpec())
    else -> member.add(memberForValue(value))
  }
}

/**
 * Creates a [CodeBlock] with parameter `format` depending on the given `value` object.
 * Handles a number of special cases, such as appending "f" to `Float` values, and uses
 * `%L` for other types.
 */
internal fun memberForValue(value: Any) = when (value) {
  is Class<*> -> CodeBlock.of("%T::class", value)
  is Enum<*> -> CodeBlock.of("%T.%L", value.javaClass, value.name)
  is String -> CodeBlock.of("%S", value)
  is Float -> CodeBlock.of("%Lf", value)
  is Double -> CodeBlock.of("%L", value)
  is Char -> CodeBlock.of("$value.toChar()")
  is Byte -> CodeBlock.of("$value.toByte()")
  is Short -> CodeBlock.of("$value.toShort()")
  // Int or Boolean
  else -> CodeBlock.of("%L", value)
}

internal fun KSType.unwrapTypeAlias(): KSType {
  return if (this.declaration is KSTypeAlias) {
    (this.declaration as KSTypeAlias).type.resolve()
  } else {
    this
  }
}

internal fun KSFunctionDeclaration.invokeArgList(
  explicit: Boolean = true
) = if (parameters.isNotEmpty()) parameters.iterator().let {
    val argsList = StringBuilder()
    while (it.hasNext()) {
      val parameterName = it.next().name?.getShortName()
      if (explicit) {
        argsList.append("$parameterName=$parameterName")
      } else {
        argsList.append(parameterName)
      }
      if (it.hasNext()) argsList.append(", ")
    }
    argsList.toString()
  } else ""