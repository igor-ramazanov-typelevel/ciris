/*
 * Copyright 2017-2025 Viktor Rudebeck
 *
 * SPDX-License-Identifier: MIT
 */

package ciris

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Path, Paths}
import org.scalacheck.{Arbitrary, Gen}

private[ciris] trait GeneratorsRuntimePlatform {
  val pathGen: Gen[Path] =
    Gen.lzy {
      Gen.alphaNumStr.map(Paths.get(_)).flatMap { path =>
        Gen.sized { size =>
          Gen.frequency(
            1 -> Gen.const(path),
            size -> pathGen.map(path.resolve)
          )
        }
      }
    }

  implicit val pathArbitrary: Arbitrary[Path] =
    Arbitrary(pathGen)

  val charsetGen: Gen[Charset] =
    Gen.oneOf(
      StandardCharsets.ISO_8859_1,
      StandardCharsets.US_ASCII,
      StandardCharsets.UTF_8,
      StandardCharsets.UTF_16,
      StandardCharsets.UTF_16BE,
      StandardCharsets.UTF_16LE
    )

  implicit val charsetArbitrary: Arbitrary[Charset] =
    Arbitrary(charsetGen)
}
