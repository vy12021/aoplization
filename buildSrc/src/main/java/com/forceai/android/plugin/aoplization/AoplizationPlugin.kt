package com.forceai.android.plugin.aoplization

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.forceai.android.plugin.aoplization.AoplizationConfig.Companion.PROPERTY_MODULE
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import java.util.*

/**
 * 组件收集和自动化注入插件
 * Created by Tesla on 2020/09/30.
 */
class AoplizationPlugin: Plugin<Project> {

  companion object {
    private const val OPTION_DEBUG_MODE = "option.debug.enable"
  }

  private lateinit var config: AoplizationConfig

  private val properties by lazy {
    Properties().apply {
      Thread.currentThread().contextClassLoader.getResourceAsStream("artifact.properties")?.run {
        load(this)
      }
    }
  }

  private val Group by lazy { properties.getProperty("implementation-group", "") }
  private val Version by lazy { properties.getProperty("implementation-version", "") }
  private val annotationConfig = "${Group}:compiler:${Version}"
  private val runtimeConfig = "${Group}:runtime:${Version}"
  private val KotlinReflection = "org.jetbrains.kotlin:kotlin-reflect:1.6.21"

  override fun apply(project: Project) {
    println(">>>>>>>>>>>>>>>>>>>>>>注册插件Aoplization[${project.name}]<<<<<<<<<<<<<<<<<<<<<<")
    config = project.getConfiguration()

    if (project.isRootProject()) {
      project.afterEvaluate {
        project.effectSubModules { subProject ->
          if (config.incremental || subProject.isApplicationModule()) {
            subProject.plugins.apply(AoplizationPlugin::class.java)
          }
        }
      }
      return
    }

    project.afterEvaluate {
      require(it.isAndroid()) {
        "Project [${it.name}] is not an android module"
      }

      it.requireAndroidExt().apply {
        println("Project[${it.name}].registerTransform(${config})")
        registerTransform(TransformWithJavassit(it))
      }
      it.requireAndroidComponentsExt().apply {
        println("Project[${it.name}].registerTransform(${config})")
        TransformWithASM.registerASMTransformer(this)
      }

      if (!it.isApplicationModule()) {
        return@afterEvaluate
      }

      injectDependency(it)
      injectCompileOptions(it)

      it.effectSubModules { subProject ->
        project.addDependency("implementation", subProject)
        subProject.afterEvaluate {
          injectDependency(subProject)
          injectCompileOptions(subProject)
        }
      }

      it.gradle.taskGraph.whenReady { _ ->
        // 注册合并java资源任务
        it.getBuildNames().forEach { buildName ->
          it.tasks.findByName("merge${buildName}JavaResource")?.apply {
          }
        }
      }
    }
  }

  private fun injectDependency(project: Project) {
    project.addDependency("implementation", KotlinReflection)
    /*project.addDependency("implementation", runtimeConfig)
    project.addProcessor(annotationConfig)*/
  }

  /**
   * 注入编译选项，如果是入口Project则必须在evaluated之前配置
   */
  private fun injectCompileOptions(project: Project) {
    val options = mapOf(
            OPTION_DEBUG_MODE to config.debugMode.toString()
    )
    if (config.debugMode) {
      println("Project[${project.name}].injectCompileOptions--->${options}")
    }
    project.requireAndroidExt().defaultConfig.javaCompileOptions {
      annotationProcessorOptions {
        arguments.putAll(options)
      }
    }
    if (project.hasKaptPlugin()) {
      project.extensions.findByType(KaptExtension::class.java)?.arguments {
        options.forEach { (option, value) ->
          arg(option, value)
        }
      }
    }
  }

  private fun matchProject(includeModules: List<String>, subProject: Project): Boolean {
    if (subProject.hasProperty(PROPERTY_MODULE)) {
      (subProject.findProperty(PROPERTY_MODULE) as? String)?.toBoolean()?.let {
        if (it) return true
      }
    }
    return includeModules.find { moduleName ->
      subProject.name == moduleName || subProject.name.matches(moduleName.toRegex())
    } != null
  }

  private fun Project.isModule() = isApplicationModule() || matchProject(config.includeModules, this)

  private fun Project.effectSubModules(iterator: (subProject: Project) -> Unit) {
    subProjects {
      it.isApplicationModule() || matchProject(config.includeModules, it)
    }.forEach(iterator)
  }

  private fun Project.isApplicationModule() = isApplication() || config.applicationModule == name

}

internal fun Task.invalidate() {
  onlyIf { true }
  outputs.upToDateWhen { false }
}

internal fun Project.invalidateCache(vararg taskNames: String) {
  gradle.taskGraph.whenReady {
    tasks.forEach { task ->
      if (taskNames.isNullOrEmpty()) {
        task.invalidate()
      } else {
        taskNames.find { it == task.name || it.toRegex().matches(task.name) }?.let {
          task.invalidate()
        }
      }
    }
  }
}

internal fun Project.getBuildNames(): Set<String> {
  return (requireApplicationProject().requireAndroidExt() as AppExtension).let {androidExt ->
    if (androidExt.applicationVariants.isNotEmpty()) {
      androidExt.applicationVariants.map { it.name }
    } else {
      androidExt.buildTypes.map { it.name }
    }
  }.toSet()
}

internal fun Project.requireApplicationProject(): Project {
  if (this.isApplication()) return this
  rootProject.subprojects.forEach {
    if (it.isApplication()) {
      return@requireApplicationProject it
    }
  }
  throw IllegalStateException()
}

internal fun Project.requireAndroidExt(): BaseExtension {
  require(isAndroid()) {
    "Project [$name] is not an android module"
  }
  return if (isApplication()) {
    project.extensions.findByType(AppExtension::class.java)
  } else {
    project.extensions.findByType(LibraryExtension::class.java)
  } as BaseExtension
}

internal fun Project.requireAndroidComponentsExt(
): AndroidComponentsExtension<*,*,*> {
  require(isAndroid()) {
    "Project [$name] is not an android module"
  }
  return extensions.findByType(AndroidComponentsExtension::class.java)!!
}

internal fun Project.isRootProject() = this == rootProject

internal fun Project.isApplication() = this.pluginManager.hasPlugin("com.android.application")

internal fun Project.isLibrary() = this.pluginManager.hasPlugin("com.android.library")

internal fun Project.isAndroid() = isApplication() || isLibrary()

internal fun Project.hasKaptPlugin() = this.pluginManager.hasPlugin("kotlin-kapt")

internal fun Project.getConfiguration(): AoplizationConfig {
  return try {
    // 先查找，如果没有找到再创建，如果创建失败
    rootProject.extensions.let {
      it.findByType(AoplizationConfig::class.java)
              ?: it.create(AoplizationConfig.EXTENSION_ALIAS, AoplizationConfig::class.java)
    }
  } catch (e: Exception) {
    e.printStackTrace()
    AoplizationConfig()
  }
}

internal fun Project.subProjects(filter: (subProject: Project) -> Boolean): Collection<Project> {
  val subProjects = mutableListOf<Project>()
  rootProject.subprojects.forEach { subProject ->
    if (subProject.name == name || !filter(subProject)) {
      return@forEach
    }
    subProjects.add(subProject)
  }
  return subProjects
}

internal fun Project.addDependency(configuration: String, vararg dependencies: Any) {
  dependencies.forEach {
    this.dependencies.add(configuration, it)
  }
}

internal fun Project.addProcessor(vararg dependencies: Any) {
  dependencies.forEach {
    if (hasKaptPlugin()) {
      this.dependencies.add("kapt", it)
    }
    this.dependencies.add("annotationProcessor", it)
  }
}
