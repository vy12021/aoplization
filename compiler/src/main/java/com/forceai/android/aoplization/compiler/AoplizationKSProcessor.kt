@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.forceai.android.aoplization.compiler

import com.forceai.android.aoplization.annotation.MainProxyHandler
import com.forceai.android.aoplization.annotation.ProxyEntry
import com.forceai.android.aoplization.annotation.ProxyHostMeta
import com.forceai.android.aoplization.annotation.ProxyHostMethodMeta
import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.*
import kotlin.reflect.full.declaredMemberFunctions
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

  private val ProxyEntryClassDeclaration by lazy {
    resolver.getClassDeclarationByName(resolver.getKSNameFromString(ProxyEntryClass.qualifiedName!!))!!
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

  private val ProxyInvokeClassName by lazy {
    ClassName.bestGuess("$AOP_PACKAGE.ProxyInvoke")
  }

  private val declaredHandlers = mutableMapOf<String, KSClassDeclaration>()

  private val entriesInTargets = mutableMapOf<KSClassDeclaration, MutableList<KSFunctionDeclaration>>()

  override fun process(resolver: Resolver): List<KSAnnotated> {
    println("================= start process ========================")
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
      check(!annotatedElement.isLocal() && !annotatedElement.isExpect && !annotatedElement.isActual)
      logger.warn("Found ProxyEntry specific the " +
              "ProxyHandler{Key=$handlerKey, isDefault=$isDefaultHandler}", annotatedElement)
      val targetDeclaration = annotatedElement.parentDeclaration
      check(targetDeclaration is KSClassDeclaration)
      val targetEntries = entriesInTargets[targetDeclaration] ?: mutableListOf<KSFunctionDeclaration>().also {
        entriesInTargets[targetDeclaration] = it
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
  private fun generateProxyAccompanyClass(target: KSClassDeclaration,
                                          entries: List<KSFunctionDeclaration>) {
    FileSpec.builder(
      target.packageName.asString(), "${target.simpleName.getShortName()}$SUFFIX_ACCOMPANY_CLASS"
    ).also { fileBuilder ->
      fileBuilder.addFileComment(ACCOMPANY_FILE_COMMENT)
      fileBuilder.addType(TypeSpec.classBuilder(fileBuilder.name).also { accompanyBuilder ->
        accompanyBuilder.addAnnotation(AnnotationSpec.get(ProxyHostMeta(target.qualifiedName?.getQualifier()!!)))
        accompanyBuilder.primaryConstructor(
          PropertySpec.builder(TARGET_FEILD_NAME, target.toClassName(), listOf(KModifier.PRIVATE)).build()
        )
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
      funcBuilder.addCode(CodeBlock.of(
        "${entryFunc.returnType?.let { "return " } ?: ""}%1T().invoke(%2T(\n" +
            "  ${TARGET_FEILD_NAME}.javaClass.getDeclaredMethod(%3S)\n" +
            "), object: %4T {\n" +
            "  override fun resume(returnValue: Any?): Any? {\n" +
            "    %[return %5L(%6L).let {\n${entryFunc.}%]" +
            "      returnValue ?: it\n" +
            "    }\n" +
            "  }\n" +
            "})",
        declaredHandlers[entryFunc.getAnnotationsByType(ProxyEntryClass).first().handlerKey]?.toClassName(),
        ProxyContextClassName,
        entryFunc.simpleName.getShortName(),
        ProxyContinuationClassName,
        "${entryFunc.simpleName.getShortName()}$SUFFIX_ACCOMPANY_FUNCTION",
        entryFunc.invokeArgList()
      ))
    }

  private fun buildEntryProxyFunction(entryFunc: KSFunctionDeclaration) =
    buildEntryStub(entryFunc,
      "${entryFunc.simpleName.getShortName()}$SUFFIX_ACCOMPANY_FUNCTION").also { funcBuilder ->
      funcBuilder.addCode(buildEntryProxyCodeBlock(entryFunc))
    }

  @OptIn(KotlinPoetKspPreview::class)
  private fun testReflection(entryFunc: KSFunctionDeclaration) {
    entryFunc.javaClass.kotlin.declaredMemberFunctions
    val hostMethod = AoplizationKSProvider::class.declaredMemberFunctions.find {
      it.name == entryFunc.simpleName.getShortName() && it.parameters.let { kParameters ->
        if (kParameters.size != entryFunc.parameters.size) return@let false
        if (kParameters.isEmpty()) return@let true
        val entryParameterTypeNames = arrayOf(
          "", ""
        )
        kParameters.forEachIndexed { index, kParameter ->
          if (kParameter.type.javaType.toString() != entryParameterTypeNames[index]) return@let false
        }
        return@let true
      }
    }
    hostMethod?.call("target", "p1", "p2", "p3")
  }

  @OptIn(KotlinPoetKspPreview::class)
  private fun buildEntryProxyCodeBlock(entryFunc: KSFunctionDeclaration) =
    if ((entryFunc.isPublic() || entryFunc.isJavaPackagePrivate()
          || entryFunc.isInternal()) && !entryFunc.isProtected()) {
      CodeBlock.of(
        "${entryFunc.returnType?.let { "return " } ?: ""}${TARGET_FEILD_NAME}.%L(%L)",
        entryFunc.simpleName.getShortName(),
        entryFunc.invokeArgList()
      )
    } else {
      CodeBlock.builder().apply {
        addStatement(
          "val hostMethod = %1L::class.%2M.find {\n" +
              "  it.name == %3S && it.parameters.let { kParameters ->\n" +
              "    if (kParameters.size != %4L) return@let false\n" +
              "    if (kParameters.isEmpty()) return@let true\n" +
              "    val entryParameterTypeNames = arrayOf(%5L)\n" +
              "    kParameters.forEachIndexed { index, kParameter ->\n" +
              "      if (kParameter.type.%6M.typeName != entryParameterTypeNames[index]) {\n" +
              "        return@let false\n" +
              "      }\n" +
              "    }\n" +
              "    return@let true\n" +
              "  }\n" +
              "}",
          TARGET_FEILD_NAME,
          MemberName("kotlin.reflect.full", "declaredMemberFunctions"),
          entryFunc.simpleName.getShortName(),
          entryFunc.parameters.size,
          entryFunc.parameters.let {
            val namesBuilder = StringBuilder()
            it.forEach {
              namesBuilder.append(
                "\"${it.type.resolve().toTypeName()}\"").append(", ")
            }
            return@let namesBuilder.toString()
          },
          MemberName("kotlin.reflect.jvm", "javaType")
        )
        addStatement(
          "${entryFunc.returnType?.let { "return " } ?: ""}hostMethod?.call(%L)",
          entryFunc.invokeArgList(false).let {
            if (isEmpty()) TARGET_FEILD_NAME else "$TARGET_FEILD_NAME, $it"
          }
        )
      }.build()
    }

  @OptIn(KotlinPoetKspPreview::class)
  private fun buildEntryStub(entryFunc: KSFunctionDeclaration,
                             name: String = entryFunc.simpleName.getShortName()) =
    FunSpec.builder(name).also { funcBuilder ->
      funcBuilder.addAnnotations(entryFunc.annotations.filter {
        it.shortName.getShortName().startsWith("Proxy").not()
      }.map { it.toAnnotationSpec() }.toList())
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