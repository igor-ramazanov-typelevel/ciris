/*
 * Copyright 2017-2025 Viktor Rudebeck
 *
 * SPDX-License-Identifier: MIT
 */

package enumeratum.internal

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

private[internal] trait TypeNameRuntimePlatform {
  implicit final def typeName[A]: TypeName[A] =
    macro TypeNameRuntimePlatform.typeName[A]
}

private[internal] object TypeNameRuntimePlatform {
  final def typeName[A](c: Context): c.Expr[TypeName[A]] = {
    import c.universe._
    val TypeApply(_, List(typeTree)) = c.macroApplication: @unchecked
    c.Expr(q"_root_.enumeratum.internal.TypeName(${typeTree.toString})")
  }
}
