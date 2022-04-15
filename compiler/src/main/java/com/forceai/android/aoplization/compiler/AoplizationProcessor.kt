@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.forceai.android.aoplization.compiler

import com.forceai.android.aoplization.annotation.ProxyEntry
import com.google.auto.common.SuperficialValidation
import com.google.auto.service.AutoService
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.sun.source.util.Trees
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Types
import javax.tools.Diagnostic

private const val PACKAGE_SPACE = "com.forceai.android.aoplization"

/**
 * 组件相关注解处理
 * Created by Tesla on 2020/09/30.
 */
@AutoService(Processor::class)
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.DYNAMIC)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
class AoplizationProcessor : AbstractProcessor() {
  private lateinit var typeUtils: Types
  private lateinit var filer: Filer
  private var trees: Trees? = null
  private lateinit var logger: Messager
  private lateinit var options: Map<String, String>
  private var debugEnabled: Boolean = false

  @Synchronized
  override fun init(env: ProcessingEnvironment) {
    super.init(env)
    options = env.options
    typeUtils = env.typeUtils
    logger = env.messager
    filer = env.filer
    logger.printMessage(Diagnostic.Kind.NOTE, "options--->{debuggable: $debugEnabled}")
    trees = env.trees
  }

  override fun getSupportedOptions() = setOf(
    IncrementalAnnotationProcessorType.ISOLATING.processorOption
  )

  override fun getSupportedAnnotationTypes() = setOf(
    ProxyEntry::class.java.canonicalName
  )

  override fun process(annotations: Set<TypeElement>, env: RoundEnvironment): Boolean {
    for (element in env.getElementsAnnotatedWith(ProxyEntry::class.java)) {
      if (!SuperficialValidation.validateElement(element)) {
        logger.printMessage(
          Diagnostic.Kind.WARNING, "Invalid element：${element.simpleName}"
        )
        continue
      }
      try {

      } catch (e: Exception) {
        e.printStackTrace()
        logger.printMessage(Diagnostic.Kind.ERROR, e.message)
        return true
      }
    }
    return true
  }

  private fun getRawType(typeName: TypeName): TypeName {
    return if (typeName is ParameterizedTypeName) typeName.rawType else typeName
  }

}

private val ProcessingEnvironment.trees: Trees?
  get() = try {
    Trees.instance(this)
  } catch (ignored: IllegalArgumentException) {
    var trees: Trees? = null
    try {
      // Get original ProcessingEnvironment from Gradle-wrapped one or KAPT-wrapped one.
      for (field in this.javaClass.declaredFields) {
        if (field.name == "delegate" || field.name == "processingEnv") {
          field.isAccessible = true
          val javacEnv = field[this] as ProcessingEnvironment
          trees = Trees.instance(javacEnv)
        }
      }
    } catch (ignored2: Throwable) {}
    trees
  }