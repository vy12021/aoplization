package com.forceai.android.aoplization

/**
 * 关于组件辅助描述信息注解，便于非运行时理解关系，比如插件处理过程
 * Created by Tesla on 2022/04/09.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
internal annotation class Meta(
)