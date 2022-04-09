package com.forceai.android.plugin.aoplization

import com.android.build.api.transform.*
import javassist.ClassPath
import javassist.ClassPool
import javassist.CtClass
import javassist.bytecode.AnnotationDefaultAttribute
import javassist.bytecode.annotation.BooleanMemberValue
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
class MainTransformer(private val project: Project): Transform() {

  companion object {
    private const val PACKAGE_RUNTIME = "com.forceai.android.aoplization"
    private const val PACKAGE_ANNOTATION = "${PACKAGE_RUNTIME}.annotation"
    private const val RUNTIME = "${PACKAGE_RUNTIME}.Aoplization"
    private const val ANNOTATION_AUTOWIRED = "${PACKAGE_ANNOTATION}.AutoWired"
    // 此包比较特殊
    private const val ANNOTATION_META = "${PACKAGE_RUNTIME}.Meta"

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
    if (null != INCLUDE_ENTRY.find { classEntryName.startsWith(it) }) {
      return true
    }
    if (config.includePackages.isNotEmpty()) {
      return null != includePackages.find { classEntryName.startsWith(it) }
    }
    return null == excludePackages.find { classEntryName.startsWith(it) }
  }

  /**
   * 注册类收集
   */
  private val registers = mutableSetOf<CtClass>()

  private lateinit var outputProvider: TransformOutputProvider
  private lateinit var transformInputs: Collection<TransformInput>
  private lateinit var referenceInputs: Collection<TransformInput>
  private lateinit var secondaryInputs: Collection<SecondaryInput>

  override fun getName() = "ComponentScanner"

  override fun getInputTypes(): MutableSet<QualifiedContent.ContentType>
          = mutableSetOf(QualifiedContent.DefaultContentType.CLASSES)

  /**
   * 返回需要被处理的Project项目资源来源
   */
  override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
    return mutableSetOf<QualifiedContent.Scope>().apply {
      add(QualifiedContent.Scope.PROJECT)
      if (!config.incremental) {
        add(QualifiedContent.Scope.SUB_PROJECTS)
        add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
      }
    }
  }

  /**
   * 返回可能使用到但是不会被处理的lib/aar资源来源
   */
  override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
    return mutableSetOf<QualifiedContent.Scope>().apply {
      if (config.incremental) {
        add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
        add(QualifiedContent.Scope.SUB_PROJECTS)
      }
    }
  }

  override fun isIncremental() = config.incremental

  override fun transform(transformInvocation: TransformInvocation) {
    println(">>>>>>>>>>>>>>>>>>>>>>启动Transformer<<<<<<<<<<<<<<<<<<<<<<<")
    println("插件配置：$config")
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
    val runtimeJarInput =
            collectInputs(classPool, effectInputs, classPaths)
            ?: throw RuntimeException("没有查找到工具：$RUNTIME")
    // 收集注册信息，并转换相关类
    transformClasses(classPool, effectInputs)
    // 验证注册信息正确性
    checkIfValid(classPool)
    if (!config.incremental) {
      // 注入自动化注册逻辑，并重新打包
      runtimeJarInput.apply {
        repackageJar(classPool, this, getOutput(this),
                listOf(transformComponentizationJar(classPool, this)))
      }
    }
    // 释放类资源
    freeClassPoll(classPool, classPaths)
    println(">>>>>>>>>>>>>>>>>>Transformer总共耗时：" +
            "${(System.currentTimeMillis() - startTime) / 1000f}秒<<<<<<<<<<<<<<<<<")
  }

  private fun getOutput(content: QualifiedContent) =
          outputProvider.getContentLocation(content.name, content.contentTypes, content.scopes,
          if (content is JarInput) Format.JAR else Format.DIRECTORY)

  /**
   * 收集输入的一些上下文信息
   */
  private fun collectInputs(classPool: ClassPool,
                            transInputs: MutableList<QualifiedContent>,
                            classPaths: MutableList<ClassPath>): JarInput? {
    var runtimeJarInput: JarInput? = null
    val scanInputs = fun (inputs: Collection<TransformInput>, readOnly: Boolean) {
      inputs.forEach input@{ input ->
        input.jarInputs.forEach jarInput@{ jarInput ->
          if (jarInput.status == Status.REMOVED) {
            return@jarInput
          }
          classPaths.add(classPool.appendClassPath(jarInput.file.absolutePath))
          if (null == runtimeJarInput && null != classPool.getOrNull(RUNTIME)) {
            runtimeJarInput = jarInput
          }
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
    return runtimeJarInput
  }

  /**
   * 转换处理所有输入的classpath文件
   * @return 被修改和加载过的class记录，用于最后手动资源释放
   */
  private fun transformClasses(classPool: ClassPool, inputs: List<QualifiedContent>)
          : MutableList<CtClass> {
    val classes = mutableListOf<CtClass>()
    inputs.forEach input@{input ->
      if (DEBUG) println("找到资源：${input.file.absolutePath}")
      if (input is JarInput) {
        val jarOutput = getOutput(input)
        // 子模块的类包名称
        if (checkInputJar(input.file)) {
          transformComponentsFromJar(classPool, input).apply {
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
          transformComponentsFromDir(classPool, input).apply {
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
  private fun transformComponentsFromJar(classPool: ClassPool,
                                         jarInput: JarInput): List<CtClass> {
    println("collectComponentsFromJar: ${jarInput.file.absolutePath}")
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
  private fun transformComponentsFromDir(classPool: ClassPool,
                                         dirInput: DirectoryInput): List<CtClass> {
    println("transformComponentsFromDir: ${dirInput.file.absolutePath}")
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
  private fun transformFromEntryName(classPool: ClassPool, classEntryName: String): CtClass? {
    if (classEntryName.startsWith("META-INF")
            || !classEntryName.endsWith(".class")
            || !checkClassEntry(classEntryName)) {
      return null
    }
    if (DEBUG) println("\tclass file: $classEntryName")
    collectComponentRegister(classPool, classEntryName)
    return transformComponentInject(classPool, classEntryName)
  }

  /**
   * 从类资源路径转换为CtClass
   */
  private fun getCtClassFromClassEntry(classPool: ClassPool, classEntryName: String): CtClass {
    val index = classEntryName.indexOf(".class")
    if (index >= 0) {
      return classPool.get(classEntryName.substring(0, index).replace("/", "."))
    }
    return classPool.get("java.lang.Object")
  }

  /**
   * 收集组件注册器
   */
  private fun collectComponentRegister(classPool: ClassPool, classEntryName: String) {
    if (!classEntryName.replace("/", ".").startsWith(PACKAGE_RUNTIME)) {
      return
    }
    val ctClass = getCtClassFromClassEntry(classPool, classEntryName)
    if (ctClass.packageName != PACKAGE_RUNTIME) {
      return
    }
    if (!ctClass.simpleName.endsWith("_Register")) {
      return
    }
    registers.add(ctClass)
  }

  /**
   * 处理组件注入
   * @return 返回被修改过的类
   */
  private fun transformComponentInject(classPool: ClassPool, classEntryName: String): CtClass? {
    val ctClass = getCtClassFromClassEntry(classPool, classEntryName)
    if (ctClass.isFrozen) {
      return null
    }
    val defaultMode = classPool.get(ANNOTATION_AUTOWIRED).getDeclaredMethod("lazy")
        .methodInfo.let {methodInfo ->
          ((methodInfo.getAttribute(AnnotationDefaultAttribute.tag)
              as AnnotationDefaultAttribute).defaultValue as BooleanMemberValue).value
    }
    var hasChanged = false
    ctClass.declaredFields.filter { it.hasAnnotation(ANNOTATION_AUTOWIRED) }.forEach {field ->
      hasChanged = true
    }
    if (hasChanged) {
      ctClass.freeze()
    }
    return if (hasChanged) ctClass else null
  }

  /**
   * 转换Componentization所属jar资源
   */
  private fun transformComponentizationJar(classPool: ClassPool, jarInput: JarInput): CtClass {
    println("transformComponentizationJar: ${jarInput.file.absolutePath}")
    val Componentization = classPool.get(RUNTIME)
    val registerBody = StringBuilder("{\n")
    registers.forEach {
      registerBody.append("register(${it.name}.class);\n")
      println("\tinsert ComponentRegister: ${it.name}.class")
    }
    registerBody.append("}")
    (Componentization.classInitializer?: Componentization.makeClassInitializer())
        .insertAfter(registerBody.toString())
    return Componentization
  }

  /**
   * 重新打jar
   * @param jarInput  输入的jar包
   * @param transformedClasses jar包中被转换过的class
   */
  private fun repackageJar(classPool: ClassPool,
                           jarInput: JarInput, jarOutput: File,
                           transformedClasses: List<CtClass>) {
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