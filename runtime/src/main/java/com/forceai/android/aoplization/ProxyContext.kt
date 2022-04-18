package com.forceai.android.aoplization

data class ProxyContext(
  val annotations: Array<Annotation>
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as ProxyContext
    if (!annotations.contentEquals(other.annotations)) return false
    return true
  }

  override fun hashCode(): Int {
    return annotations.contentHashCode()
  }
}