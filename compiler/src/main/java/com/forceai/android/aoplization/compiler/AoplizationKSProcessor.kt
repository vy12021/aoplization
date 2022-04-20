@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.forceai.android.aoplization.compiler

import com.forceai.android.aoplization.annotation.MainProxyHandler
import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.aoplization.annotation.ProxyHostMeta
import com.forceai.android.aoplization.annotation.ProxyHostMethodMeta
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

@OptIn(KspExperimental::class)
class AoplizationKSProcessor(
  private val generator: CodeGenerator,
  private val version: KotlinVersion,
  private val logger: KSPLogger,
  private val options: Map<String, String>
): SymbolProcessor {

  private val AOP_PACKAGE = "com.forceai.android.aoplization"

  private val SUFFIX_ACCOMPANY_CLASS = "_ProxyAccompany"

  private val TARGET_FEILD_NAME = "target"

  private val SUFFIX_ACCOMPANY_FUNCTION = "Proxy"

  private val ACCOMPANY_FILE_COMMENT = "This file is auto generated for AOP helper, so do NOT modify and use it"

  private lateinit var resolver: Resolver

  private val builtIns by lazy { resolver.builtIns }

  private val ProxyEntryClass by lazy { ProxyEntry::class }

  private val MainProxyHandlerClass by lazy { MainProxyHandler::class }

  private val ProxyHostMetaClass by lazy { ProxyHostMeta::class }

  private val ProxyHostMethodMetaClass by lazy { ProxyHostMethodMeta::class }

  private val ProxyEntryDeclaration by lazy {
    resolver.getClassDeclarationByName(resolver.getKSNameFromString(ProxyEntryClass.qualifiedName!!))!!
  }

  private val ProxyHostMetaDeclaration by lazy {
    resolver.getClassDeclarationByName(resolver.getKSNameFromString(ProxyHostMetaClass.qualifiedName!!))!!
  }

  private val ProxyHostMethodMetaDeclaration by lazy {
    resolver.getClassDeclarationByName(resolver.getKSNameFromString(ProxyHostMethodMetaClass.qualifiedName!!))!!
  }

  private val MainProxyHandlerDeclaration by lazy {
    resolver.getClassDeclarationByName(resolver.getKSNameFromString(MainProxyHandlerClass.qualifiedName!!))!!
  }

  private val ProxyContextClassName by lazy {
    ClassName.bestGuess("$AOP_PACKAGE.ProxyContext")
  }

  private val ProxyContinuationClassName by lazy {
    ClassName.bestGuess("$AOP_PACKAGE.ProxyContinuation")
  }

  private val ProxyHandlerClassName by lazy {
    ClassName.bestGuess("$AOP_PACKAGE.ProxyHandler")
  }

  private fun KSAnnotation.isInternalUse() =
    annotationType.resolve().declaration.qualifiedName?.asString()?.let {
      return@let it == ProxyEntryDeclaration.qualifiedName?.asString()
          || it == ProxyHostMetaDeclaration.qualifiedName?.asString()
          || it == ProxyHostMethodMetaDeclaration.qualifiedName?.asString()
          || it == MainProxyHandlerDeclaration.qualifiedName?.asString()
    } == true

  private val declaredHandlers = mutableMapOf<String, KSClassDeclaration>()

  private val entriesInTargets = mutableMapOf<KSDeclarationContainer, MutableList<KSFunctionDeclaration>>()

  override fun process(resolver: Resolver): List<KSAnnotated> {
    logger.warn("================= start process ========================")
    this.resolver = resolver
    val unhandledAnnotations = mutableListOf<KSAnnotated>()

    resolver.getSymbolsWithAnnotation(MainProxyHandlerClass.qualifiedName!!).forEach { annotatedElement ->
      val mainProxyHandler = annotatedElement.getAnnotationsByType(MainProxyHandlerClass).first()
      val handlerKey = mainProxyHandler.mark
      val isDefaultHandler = handlerKey.isEmpty()
      check(annotatedElement is KSClassDeclaration)
      logger.warn("Found a ProxyHandler {Key=$handlerKey, isDefault=$isDefaultHandler}", annotatedElement)
      val previousHandler = declaredHandlers[handlerKey]
      if (previousHandler != null) {
        logger.error("The ${annotatedElement.qualifiedName?.asString()} has the same mark " +
            "with ${previousHandler.qualifiedName?.asString()}", annotatedElement)
      }
      if (annotatedElement.primaryConstructor?.parameters?.isNullOrEmpty() == false) {
        logger.error("The ProxyHandler of ${annotatedElement.qualifiedName?.asString()}" +
                " must has default constructor", annotatedElement)
      }
      if (annotatedElement.isPrivate() || annotatedElement.primaryConstructor?.isPrivate() == true) {
        logger.error("The ProxyHandler of ${annotatedElement.qualifiedName?.asString()}" +
                " and its default constructor must not private", annotatedElement)
      }
      declaredHandlers[handlerKey] = annotatedElement
    }

    resolver.getSymbolsWithAnnotation(ProxyEntryClass.qualifiedName!!).toList().forEach { annotatedElement ->
      val proxyEntry = annotatedElement.getAnnotationsByType(ProxyEntryClass).first()
      val handlerKey = proxyEntry.handlerKey
      val isDefaultHandler = handlerKey.isEmpty()
      check(annotatedElement is KSFunctionDeclaration)
      check(!annotatedElement.isExpect && !annotatedElement.isActual)
      logger.warn(
        "Found ProxyEntry specific the ProxyHandler{Key=$handlerKey, isDefault=$isDefaultHandler}",
        annotatedElement
      )
      val container = annotatedElement.container
      val targetEntries = entriesInTargets[container] ?: mutableListOf<KSFunctionDeclaration>().also {
        entriesInTargets[container] = it
      }
      targetEntries.add(annotatedElement)
    }

    entriesInTargets.entries.flatMap { it.value }.forEach { entry ->
      val handlerKey = entry.getAnnotationsByType(ProxyEntryClass).first().handlerKey
      val handlerNeeded = declaredHandlers[handlerKey]
      if (handlerNeeded == null) {
        logger.error("The handler specified[${handlerKey}] is not exist", entry)
      }
    }

    return unhandledAnnotations
  }

  @OptIn(KotlinPoetKspPreview::class, DelicateKotlinPoetApi::class)
  private fun generateProxyAccompanyClass(container: KSDeclarationContainer,
                                          entries: List<KSFunctionDeclaration>) {
    val targetClassName = container.targetClassName
    FileSpec.builder(
      targetClassName.packageName,
      "${targetClassName.canonicalName
        .substringAfter(targetClassName.packageName + '.').replace('.', '_')
      }$SUFFIX_ACCOMPANY_CLASS"
    ).also { fileBuilder ->
      fileBuilder.addFileComment(ACCOMPANY_FILE_COMMENT)
      fileBuilder.addType(TypeSpec.classBuilder(fileBuilder.name).also { accompanyBuilder ->
        accompanyBuilder.addAnnotation(
          AnnotationSpec.get(ProxyHostMeta(targetClassName.reflectionName()))
        )
        if (container is KSClassDeclaration) {
          accompanyBuilder.primaryConstructor(
            PropertySpec.builder(
              TARGET_FEILD_NAME, targetClassName, listOf(KModifier.PRIVATE)
            ).build()
          )
        }
        entries.forEach { entryFunc ->
          accompanyBuilder.addFunction(buildEntryAccompanyFunction(entryFunc).build())
          accompanyBuilder.addFunction(buildEntryProxyFunction(entryFunc).build())
        }
      }.build())
    }.build().writeTo(generator, false)
  }

  @OptIn(KotlinPoetKspPreview::class)
  private fun buildEntryAccompanyFunction(entryFunc: KSFunctionDeclaration) =
    buildEntryStub(entryFunc).also { funcBuilder ->
      funcBuilder.addModifiers(KModifier.INTERNAL)
      funcBuilder.addCode(CodeBlock.of("""
        |${entryFunc.returnType?.let { "return " } ?: ""}%1T().invoke(%2T(
        |  %3L
        |), object: %4T {
        |  override fun resume(returnValue: Any?): Any? {
        |    return %5L(%6L).let·{
        |      returnValue ?: it
        |    }
        |  }
        |})""".trimMargin(),
        declaredHandlers[entryFunc.getAnnotationsByType(ProxyEntryClass).first().handlerKey]?.toClassName(),
        ProxyContextClassName,
        entryFunc.annotations.filter {
          it.isInternalUse().not()
        }.takeIf { it.count() > 0 }?.iterator()?.let {
          val arrayOfCoder = CodeBlock.builder()
          arrayOfCoder.add("arrayOf(⇥⇥\n")
          while (it.hasNext()) {
            val ksAnnotation = it.next()
            val ksDeclaration = ksAnnotation.annotationType.resolve().declaration
            arrayOfCoder.add("%L(⇥\n", ksDeclaration.qualifiedName?.asString())
            ksAnnotation.arguments.iterator().also { argIterator ->
              while (argIterator.hasNext()) {
                val ksArg = argIterator.next()
                val valueBlock = CodeBlock.builder().apply {
                  addValueToBlock(ksArg.value!!, this)
                }
                arrayOfCoder.add(
                  "%1L = %2L%3L\n",
                  ksArg.name?.asString(),
                  valueBlock.build().toString(),
                  if (argIterator.hasNext()) "," else ""
                )
              }
            }
            arrayOfCoder.add("⇤)%L\n", if (it.hasNext()) "," else "")
          }
          arrayOfCoder.add("⇤)⇤").build()
        } ?: "arrayOf()",
        ProxyContinuationClassName,
        "${entryFunc.simpleName.getShortName()}$SUFFIX_ACCOMPANY_FUNCTION",
        entryFunc.invokeArgList()
      ))
    }

  private fun buildEntryProxyFunction(entryFunc: KSFunctionDeclaration) =
    buildEntryStub(entryFunc,
      "${entryFunc.simpleName.getShortName()}$SUFFIX_ACCOMPANY_FUNCTION"
    ).also { funcBuilder ->
      funcBuilder.addModifiers(KModifier.PRIVATE)
      funcBuilder.addCode(buildEntryProxyCodeBlock(entryFunc))
    }

  @OptIn(KotlinPoetKspPreview::class)
  private fun testReflection(entryFunc: KSFunctionDeclaration) {
    entryFunc.javaClass.kotlin.declaredMemberFunctions
    Class.forName("").kotlin
    val hostMethod = AoplizationKSProvider::class.declaredMemberFunctions.find {
      it.name == entryFunc.simpleName.getShortName() && it.parameters.let { kParameters ->
        if (kParameters.size != entryFunc.parameters.size) return@let false
        if (kParameters.isEmpty()) return@let true
        val entryParameterTypeNames = arrayOf(
          "", ""
        )
        kParameters.forEachIndexed { index, kParameter ->
          if (kParameter.type.javaType.toString() != entryParameterTypeNames[index]) {
            return@let false
          }
        }
        return@let true
      }
    }?.also { it.isAccessible = true }?.call("target", "p1", "p2", "p3")
  }

  @OptIn(KotlinPoetKspPreview::class)
  private fun buildEntryProxyCodeBlock(entryFunc: KSFunctionDeclaration) = let {
    CodeBlock.of("""
      |${entryFunc.returnType?.let { "return " } ?: ""}Class.forName(
      |  %1S
      |).kotlin.%2M.find·{
      |  it.name == %3S && it.parameters.let·{ kParameters ->
      |    if (kParameters.size != %4L) return@let false
      |    if (kParameters.isEmpty()) return@let true
      |    val entryParameterTypeNames = %5L
      |    kParameters.forEachIndexed { index, kParameter ->
      |      if (kParameter.type.%6M.typeName != entryParameterTypeNames[index]) {
      |        return@let false
      |      }
      |    }
      |    return@let true
      |  }
      |}?.also { it.%7M = true }?.call(%8L)""".trimMargin(),
      entryFunc.container.targetClassName.reflectionName(),
      MemberName("kotlin.reflect.full", "declaredMemberFunctions"),
      "${entryFunc.simpleName.getShortName()}$SUFFIX_ACCOMPANY_FUNCTION",
      entryFunc.parameters.size + if (entryFunc.isStatic()) 0 else 1,
      entryFunc.parameters.takeIf { it.isNotEmpty() }?.iterator()?.let { iterator ->
        val arrayOfCoder = CodeBlock.builder()
        arrayOfCoder.add("arrayOf(\n⇥⇥⇥")
        while (iterator.hasNext()) {
          arrayOfCoder.add("%1S%2L\n",
            iterator.next().type.resolve().toTypeName(),
            if (iterator.hasNext()) "," else ""
          )
        }
        arrayOfCoder.add("⇤)⇤⇤").build()
      } ?: "arrayOf<String>()",
      MemberName("kotlin.reflect.jvm", "javaType"),
      MemberName("kotlin.reflect.jvm", "isAccessible"),
      entryFunc.invokeArgList(false).let {
        when {
          entryFunc.isStatic() -> it
          else -> "$TARGET_FEILD_NAME${if (it.isEmpty()) "" else ", "}$it"
        }
      }
    )
  }

  @OptIn(KotlinPoetKspPreview::class, DelicateKotlinPoetApi::class)
  private fun buildEntryStub(entryFunc: KSFunctionDeclaration,
                             name: String = entryFunc.simpleName.getShortName()) =
    FunSpec.builder(name).also { funcBuilder ->
      funcBuilder.addAnnotation(AnnotationSpec.get(entryFunc.methodMeta))
      funcBuilder.addModifiers(KModifier.FINAL)
      entryFunc.parameters.forEachIndexed { index, parameter ->
        funcBuilder.addParameter(
          ParameterSpec.builder("${entryFunc.parameters[index].name?.getShortName()}",
            parameter.type.toTypeName()).apply {
            if (parameter.isVararg) addModifiers(KModifier.VARARG)
            if (parameter.isCrossInline) addModifiers(KModifier.CROSSINLINE)
            if (parameter.isNoInline) addModifiers(KModifier.NOINLINE)
            addAnnotations(parameter.annotations.map { it.toAnnotationSpec() }.toList())
          }.build()
        )
      }
      entryFunc.returnType?.toTypeName()?.also { returnType ->
        funcBuilder.returns(returnType)
      }
    }

  private fun KSFunctionDeclaration.isStatic() = let {
    functionKind == FunctionKind.STATIC || functionKind == FunctionKind.TOP_LEVEL
  }

  private val KSFunctionDeclaration.container: KSDeclarationContainer
    get() = let {
      when (functionKind) {
        FunctionKind.STATIC, FunctionKind.MEMBER -> it.closestClassDeclaration()!!
        FunctionKind.TOP_LEVEL -> it.containingFile!!
        else -> throw UnsupportedFunctionTypeException()
      }
    }

  @OptIn(KotlinPoetKspPreview::class)
  private val KSDeclarationContainer.targetClassName
    get() = when(this) {
      is KSFile -> {
        ClassName.bestGuess(
          "${packageName.asString()}.${fileName.substringBefore('.')}Kt"
        )
      }
      is KSClassDeclaration -> toClassName()
      else -> throw UnsupportedFunctionTypeException()
    }

  private val KSFunctionDeclaration.signature
    get() = let {
      it.simpleName.asString().plus(
        it.parameters.joinToString(separator = "/") {
          it.type.resolve().declaration.let {
            it.qualifiedName?.asString() ?: it.simpleName.asString()
          }
        }
      )
    }

  private val KSFunctionDeclaration.methodMeta
    get() = let { funcDeclaration ->
      ProxyHostMethodMeta(
        funcDeclaration.simpleName.asString(),
        funcDeclaration.parameters.map { parameter ->
          parameter.type.resolve().declaration.let {
            it.qualifiedName?.asString() ?: it.simpleName.asString()
          }
        }.toTypedArray()
      )
    }

  override fun finish() {
    entriesInTargets.entries.forEach { entry ->
      generateProxyAccompanyClass(entry.key, entry.value)
    }
  }

  override fun onError() {
    super.onError()
  }
}


private class ProxyEntryVisitor: KSDefaultVisitor<Unit, Unit>() {

  override fun defaultHandler(node: KSNode, data: Unit) {

  }

  override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
    super.visitFunctionDeclaration(function, data)
  }

}

class AoplizationKSProvider: SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return AoplizationKSProcessor(
      environment.codeGenerator,
      environment.kotlinVersion,
      environment.logger,
      environment.options
    )
  }

}

internal class UnsupportedFunctionTypeException(msg: String = ""): Exception(msg)