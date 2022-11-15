package com.forceai.android.plugin.aoplization

import com.android.build.api.transform.*
import javassist.*
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.annotation.StringMemberValue
import org.gradle.api.Project
import java.io.File
import java.io.IOException
import java.util.*
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Android插件提供的资源转换器
 * Created by Tesla on 2020/09/30.
 */
class TransformWithJavassit(private val project: Project): Transform() {

  companion object {
    private const val PACKAGE_RUNTIME = "com.forceai.android.aoplization"
    private const val PACKAGE_ANNOTATION = "${PACKAGE_RUNTIME}.annotation"
    private const val PROXY_ENTRY = "${PACKAGE_ANNOTATION}.ProxyEntry"
    private const val HOST_META = "${PACKAGE_ANNOTATION}.ProxyHostMeta"
    private const val HOST_META_CLAZZ = "clazz"
    private const val METHOD_META = "${PACKAGE_ANNOTATION}.ProxyHostMethodMeta"
    private const val METHOD_META_SIGN = "sign"
    private const val SUFFIX_ACCOMPANY = "_ProxyAccompany"

    /**
     * 需要指定包含的内部class包
     */
    private val INCLUDE_ENTRY = arrayOf(PACKAGE_RUNTIME)
    /**
     * 需要指定忽略的系统class包
     */
    private val IGNORE_ENTRY = arrayOf(
            "android/", "androidx/",
            "kotlin/", "kotlinx/",
            "org/intellij/", "org/jetbrains/")

    init {
      ClassPool.cacheOpenedJarFile = false
      ClassPool.doPruning = false
      ClassPool.releaseUnmodifiedClassFile = true
    }
  }

  private val config by lazy { project.getConfiguration() }

  private val androidExt by lazy { project.requireAndroidExt() }

  /**
   * 是否调试模式
   */
  private val DEBUG by lazy { config.debugMode }

  /**
   * android sdk所在路径
   */
  private val androidJar by lazy {
    "${androidExt.sdkDirectory.absolutePath}/platforms/${androidExt.compileSdkVersion}/android.jar"
  }

  private val includeJars by lazy {
    mutableListOf<String>().apply {
      // 对应其他子项目结果
      add("classes")
      // 对应其他transformer输出
      add("^\\d*$")
      if (!config.incremental) {
        addAll(config.includeModules.toList())
      }
      addAll(config.includeJars.toList())
    }
  }

  private val excludeJars by lazy {
    mutableListOf<String>().apply {
      add("R")
    }
  }

  private val includePackages by lazy {
    mutableListOf<String>().apply {
      addAll(INCLUDE_ENTRY)
      addAll(config.includePackages.toList())
    }.map { it.replace(".", "/") }
  }

  private val excludePackages by lazy {
    mutableListOf<String>().apply {
      addAll(IGNORE_ENTRY)
      addAll(config.excludePackages.toList())
    }.map { it.replace(".", "/") }
  }

  private fun checkInputJar(jarFile: File): Boolean {
    if (jarFile.extension != "jar") {
      return false
    }
    val jarName = jarFile.nameWithoutExtension
    val findJar = fun (includes: List<String>): String? {
      return includes.find {
        jarName == it || jarName.matches(it.toRegex())
      }
    }
    findJar(includeJars)?.let {
      return true
    }
    if (config.includeJars.isNotEmpty()) {
      return false
    }
    return false
  }

  /**
   * 检查扫描的class的文件节点路径是否在指定的范围
   */
  private fun checkClassEntry(classEntryName: String): Boolean {
    /*if (null != INCLUDE_ENTRY.find { classEntryName.startsWith(it) }) {
      return true
    }*/
    if (config.includePackages.isNotEmpty()) {
      return null != includePackages.find { classEntryName.startsWith(it) }
    }
    return null == excludePackages.find { classEntryName.startsWith(it) }
  }

  /**
   * 从类资源路径转换为CtClass
   */
  private fun getCtClassFromClassEntry(
    classPool: ClassPool, classEntryName: String
  ): CtClass {
    val index = classEntryName.indexOf(".class")
    if (index >= 0) {
      return classPool.get(classEntryName.substring(0, index).replace("/", "."))
    }
    return classPool.get("java.lang.Object")
  }

  private lateinit var outputProvider: TransformOutputProvider
  private lateinit var transformInputs: Collection<TransformInput>
  private lateinit var referenceInputs: Collection<TransformInput>
  private lateinit var secondaryInputs: Collection<SecondaryInput>

  override fun getName() = "ComponentScanner"

  override fun getInputTypes() = mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)

  /**
   * 返回需要被处理的Project项目资源来源
   */
  override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
    return mutableSetOf<QualifiedContent.Scope>().apply {
      add(QualifiedContent.Scope.PROJECT)
      if (!config.incremental) {
        add(QualifiedContent.Scope.SUB_PROJECTS)
      }
    }
  }

  /**
   * 返回可能使用到但是不会被处理的lib/aar资源来源
   */
  override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
    return mutableSetOf<QualifiedContent.Scope>().apply {
      add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
      if (config.incremental) {
        add(QualifiedContent.Scope.SUB_PROJECTS)
      }
    }
  }

  override fun isIncremental() = config.incremental

  override fun transform(transformInvocation: TransformInvocation) {
    println(">>>>>>>>>>>>>>>>>>>>>>Start Transform<<<<<<<<<<<<<<<<<<<<<<<")
    println("Configuration：$config")
    val startTime = System.currentTimeMillis()
    super.transform(transformInvocation)

    secondaryInputs = transformInvocation.secondaryInputs
    transformInputs = transformInvocation.inputs
    referenceInputs = transformInvocation.referencedInputs
    outputProvider = transformInvocation.outputProvider.apply {
      // 清理所有缓存文件
      if (!config.incremental) {
        deleteAll()
      }
    }
    val classPool = ClassPool(false)
    val classPaths = mutableListOf<ClassPath>()
    classPaths.add(classPool.appendSystemPath())
    classPaths.add(classPool.appendClassPath(androidJar))
    val effectInputs = mutableListOf<QualifiedContent>()
    // 收集必要的输入建立完成的classpath环境
    collectInputs(classPool, effectInputs, classPaths)
    // 收集注册信息，并转换相关类
    transformClasses(classPool, effectInputs)
    // 验证注册信息正确性
    checkIfValid(classPool)

    // 释放类资源
    freeClassPoll(classPool, classPaths)
    println(">>>>>>>>>>>>>>>>>>Transformer total cost：" +
            "${(System.currentTimeMillis() - startTime) / 1000f}秒<<<<<<<<<<<<<<<<<")
  }

  private fun getOutput(
    content: QualifiedContent
  ) = outputProvider.getContentLocation(
    content.name, content.contentTypes, content.scopes,
    if (content is JarInput) Format.JAR else Format.DIRECTORY
  )

  /**
   * 收集输入的一些上下文信息
   */
  private fun collectInputs(
    classPool: ClassPool,
    transInputs: MutableList<QualifiedContent>,
    classPaths: MutableList<ClassPath>
  ) {
    val scanInputs = fun (inputs: Collection<TransformInput>, readOnly: Boolean) {
      inputs.forEach input@{ input ->
        input.jarInputs.forEach jarInput@{ jarInput ->
          if (jarInput.status == Status.REMOVED) {
            return@jarInput
          }
          classPaths.add(classPool.appendClassPath(jarInput.file.absolutePath))
          if (!readOnly) {
            transInputs.add(jarInput)
          }
        }
        input.directoryInputs.forEach dirInput@{ dirInput ->
          classPaths.add(classPool.appendClassPath(dirInput.file.absolutePath))
          if (!readOnly) {
            transInputs.add(dirInput)
          }
        }
      }
    }
    scanInputs(referenceInputs, true)
    scanInputs(transformInputs, false)
  }

  /**
   * 转换处理所有输入的classpath文件
   * @return 被修改和加载过的class记录，用于最后手动资源释放
   */
  private fun transformClasses(
    classPool: ClassPool, inputs: List<QualifiedContent>
  ): MutableList<CtClass> {
    val classes = mutableListOf<CtClass>()
    inputs.forEach input@{input ->
      if (DEBUG) println("找到资源：${input.file.absolutePath}")
      if (input is JarInput) {
        val jarOutput = getOutput(input)
        // 子模块的类包名称
        if (checkInputJar(input.file)) {
          transformFromJar(classPool, input).apply {
            classes.addAll(this)
            if (isNotEmpty()) {
              repackageJar(classPool, input, jarOutput, this)
              return@input
            }
          }
        }
        input.file.copyTo(jarOutput, true)
      } else if (input is DirectoryInput) {
        val dirOutput = getOutput(input)
        // 目录先copy，然后覆盖被修改的类
        input.file.copyRecursively(dirOutput, true)
        // 兼容java的classes目录和kotlin的kotlin-classes目录，或者其他的Transform中传递路径路径name为数字
        if (input.file.name.toIntOrNull() != null
                || input.file.name == "classes"
                || input.file.parentFile.name == "kotlin-classes") {
          transformFromDir(classPool, input).apply {
            classes.addAll(this)
            forEach { clazz ->
              clazz.writeFile(dirOutput.absolutePath)
              println("\twrite class: ${clazz.name} -> ${dirOutput.absolutePath}")
            }
          }
        }
      }
    }
    return classes
  }

  /**
   * 从jar文件中处理，由依赖模块触发
   * @return 返回被修改过的类
   */
  @Throws(IOException::class, ClassNotFoundException::class)
  private fun transformFromJar(
    classPool: ClassPool,
    jarInput: JarInput
  ): List<CtClass> {
    println("transformFromJar: ${jarInput.file.absolutePath}")
    val transformedClasses = mutableListOf<CtClass>()
    JarFile(jarInput.file).use {
      it.entries().toList().forEach { entry ->
        transformFromEntryName(classPool, entry.name)?.let { transformedClass ->
          transformedClasses.add(transformedClass)
        }
      }
    }
    return transformedClasses
  }

  /**
   * 从class目录中处理，由当前模块触发
   * @return 返回被修改过的类
   */
  @Throws(IOException::class, ClassNotFoundException::class)
  private fun transformFromDir(
    classPool: ClassPool,
    dirInput: DirectoryInput
  ): List<CtClass> {
    println("transformFromDir: ${dirInput.file.absolutePath}")
    val transformedClasses = mutableListOf<CtClass>()
    getAllFiles(dirInput.file).forEach { classFile ->
      val classEntryName: String = classFile.absolutePath
              .substring(dirInput.file.absolutePath.length + 1)
              .replace("\\", "/")
      transformFromEntryName(classPool, classEntryName)?.let { transformedClass ->
        transformedClasses.add(transformedClass)
      }
    }
    return transformedClasses
  }

  /**
   * 从class索引节点转换
   */
  private fun transformFromEntryName(
    classPool: ClassPool, classEntryName: String
  ): CtClass? {
    if (classEntryName.startsWith("META-INF")
            || !classEntryName.endsWith(".class")
            /*|| !checkClassEntry(classEntryName)*/) {
      return null
    }
    if (DEBUG) println("\tclass file: $classEntryName")
    return transformInject(classPool, classEntryName)?.also {
      if (DEBUG) println("\tinject class: ${it.name}")
    }
  }

  /**
   * 处理组件注入
   * @return 返回被修改过的类
   */
  private fun transformInject(
    classPool: ClassPool, classEntryName: String
  ): CtClass? {
    if (classEntryName.contains(SUFFIX_ACCOMPANY)) {
      return null
    }
    val hostClass = getCtClassFromClassEntry(classPool, classEntryName)
    val accompanyClass = hostClass.name.replace('$', '_').plus(SUFFIX_ACCOMPANY).let {
      try { classPool[it] } catch (ignored: NotFoundException) {
        null
      }
    } ?: return null
    check(accompanyClass.hasAnnotation(HOST_META))
    val hostClassName = (accompanyClass.classFile2.getAttribute(
      AnnotationsAttribute.visibleTag
    ) as? AnnotationsAttribute)?.let {
      (it.getAnnotation(HOST_META).getMemberValue(HOST_META_CLAZZ) as StringMemberValue).value
    }
    check(hostClass.name == hostClassName)
    val injectAccompanyField = fun (static: Boolean): String {
      val fieldName = if (static) {
        "s${accompanyClass.simpleName}"
      } else accompanyClass.simpleName.replaceFirstChar { it.lowercaseChar() }
      try {
        hostClass.getField(fieldName)
        return fieldName
      } catch (ignored: NotFoundException) {}
      val hasDefaultConstructor = try {
        accompanyClass.getDeclaredConstructor(null) != null
      } catch (e: NotFoundException) { false }
      hostClass.addField(
        CtField(accompanyClass, fieldName, hostClass).apply {
          modifiers = Modifier.PRIVATE or Modifier.FINAL or (if (static) Modifier.STATIC else 0)
        },
        CtField.Initializer.byExpr(
          "new ${accompanyClass.name}(" +
              (if (static) {if (hasDefaultConstructor) "" else "null"} else "this") +
              ")"
        )
      )
      return fieldName
    }
    hostClass.declaredMethods.filter { it.hasAnnotation(PROXY_ENTRY) }.forEach { method ->
      val staticMethod = (method.modifiers and Modifier.STATIC) != 0
      if (staticMethod && method.hasAnnotation(JvmStatic::class.java)) {
        // abnormal case, appear when the kotlin function of jvmStatic annotated
        // in object class or companion object, it's a grammar sugar, so just skip
        return@forEach
      }
      val fieldName = injectAccompanyField(staticMethod)
      hostClass.addMethod(
        CtNewMethod.copy(method, hostClass, null).apply { name = "_${name}Proxy_" }
      )
      method.setBody(
        "{${if (method.returnType != null) "return " else ""}$fieldName.${method.name}($$);}")
    }
    return hostClass.also { it.freeze() }
  }

  /**
   * 重新打jar
   * @param jarInput  输入的jar包
   * @param transformedClasses jar包中被转换过的class
   */
  private fun repackageJar(
    classPool: ClassPool,
    jarInput: JarInput, jarOutput: File,
    transformedClasses: List<CtClass>
  ) {
    println("repackageJar: \n${jarInput.file.absolutePath} \n--> ${jarOutput.absolutePath}")
    JarFile(jarInput.file).use {jarFile ->
      JarOutputStream(jarOutput.outputStream()).use {jarOs ->
        jarFile.entries().toList().forEach {entry ->
          jarFile.getInputStream(entry).use {entryIs ->
            val zipEntry = ZipEntry(entry.name)
            val clazz = getCtClassFromClassEntry(classPool, entry.name)
            if (transformedClasses.contains(clazz)) {
              println("\twrite class: ${clazz.name} -> ${jarOutput.absolutePath}")
              jarOs.putNextEntry(zipEntry)
              jarOs.write(clazz.toBytecode())
            } else {
              jarOs.putNextEntry(zipEntry)
              jarOs.write(entryIs.readBytes())
            }
            jarOs.closeEntry()
          }
        }
      }
    }
  }

  /**
   * 检查注册器和组件的正确性，例如：一个api接口不能同时有两个service实现
   */
  private fun checkIfValid(classPool: ClassPool) {
  }

  private fun freeClassPoll(classPool: ClassPool, classPaths: List<ClassPath>) {
    // 释放classpath资源，关闭打开的io，清理导入缓存
    getAllClasses(classPool).forEach { clazz ->
      try {
        clazz.detach()
      } catch (ignored: Exception) {
      }
    }
    /*val ClassPoolTail = ClassPool::class.java.getDeclaredField("source")
        .apply { isAccessible = true }.get(classPool)
    val ClassPathList = ClassPoolTail.javaClass.getDeclaredField("pathList")
        .apply { isAccessible = true }.get(ClassPoolTail)*/
    classPaths.forEach { classpath ->
      classPool.removeClassPath(classpath)
    }
    classPool.clearImportedPackages()
  }

  /**
   * 获取目录下所有子文件
   */
  private fun getAllFiles(rootDir: File): List<File> {
    val files = rootDir.listFiles()
    if (null == files || files.isEmpty()) {
      return emptyList()
    }
    val results = mutableListOf<File>()
    for (child in files) {
      if (child.isFile) {
        results.add(child)
      } else {
        results.addAll(getAllFiles(child))
      }
    }
    return results
  }

  /**
   * 获取所有被载入的class
   */
  private fun getAllClasses(classPool: ClassPool): List<CtClass> {
    val clazzes = mutableListOf<CtClass>()
    val classes = ClassPool::class.java.getDeclaredField("classes")
            .apply { isAccessible = true }.get(classPool) as Hashtable<*, *>
    val parent = ClassPool::class.java.getDeclaredField("parent")
            .apply { isAccessible = true }.get(classPool) as ClassPool?
    classes.forEach {
      val clazz = (it.value as CtClass)
      clazzes.add(clazz)
    }
    if (null != parent) {
      clazzes.addAll(getAllClasses(parent))
    }
    return clazzes
  }

}