package com.forceai.android.plugin.aoplization

import com.android.build.api.instrumentation.*
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.PrintWriter

class TransformWithASM(
  private val androidComponents: AndroidComponentsExtension<*, *, *>,
) {

  companion object {

    fun registerASMTransformer(androidComponents: AndroidComponentsExtension<*, *, *>) {
      androidComponents.onVariants { variant ->
        variant.transformClassesWith(
          ClassVisitorFactory::class.java,
          InstrumentationScope.ALL) { params ->
          params.writeToStdout.set(true)
        }
      }
    }

  }

}

interface TransformParams : InstrumentationParameters {
  @get:Input
  val writeToStdout: Property<Boolean>
}

abstract class ClassVisitorFactory :
  AsmClassVisitorFactory<TransformParams> {

  override fun createClassVisitor(
    classContext: ClassContext,
    nextClassVisitor: ClassVisitor
  ): ClassVisitor {
    return if (parameters.get().writeToStdout.get()) {
      TraceClassVisitor(nextClassVisitor, PrintWriter(System.out))
    } else {
      TraceClassVisitor(nextClassVisitor, PrintWriter(File("trace_out")))
    }
  }

  override fun isInstrumentable(classData: ClassData): Boolean {
    return classData.className.let {
      it.startsWith("com.forceai.android.app")
          && it.substringAfterLast(".").let {
        it.startsWith("R") || it.startsWith("R$")
      }.not()
    }
  }
}