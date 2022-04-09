package com.forceai.android.plugin.aoplization

/**
 * 组件扫描配置
 * 通过"aoplization"扩展dsl来注入配置
 * Created by Tesla on 2020/09/30.
 */
class AoplizationConfig {
  /**
   * 是否调试模式，会打印一些详细日志
   */
  var debugMode = false

  /**
   * 是否增量模式
   */
  var incremental = false

  /**
   * 应用模块名称，预设置项，方面某些前置配置
   */
  var applicationModule = "app"

  /**
   * 支持的导入module名称(正则)
   */
  var includeModules = ArrayList<String>()

  /**
   * 支持扫描的jar包
   */
  var includeJars = ArrayList<String>()

  /**
   * 扫描包名列表，长度不为空表示启用白名单
   */
  var includePackages = ArrayList<String>()

  /**
   * 忽略扫描包列表，如果白名单命中，则跳过黑名单
   */
  var excludePackages = ArrayList<String>()

  companion object {
    /**
     * 扩展别名
     */
    const val EXTENSION_ALIAS = "aoplization"
    /**
     * 表示支持扫描的模块属性文件中定义, value为[Boolean]
     */
    const val PROPERTY_MODULE = "aoplization.enable"
  }
}