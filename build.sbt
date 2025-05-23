import scala.scalanative.build.Mode
import org.typelevel.sbt.gha.WorkflowStep.Run
import org.typelevel.sbt.gha.WorkflowStep.Sbt

ThisBuild / scalacOptions -= "-Xfatal-warnings"
ThisBuild / crossScalaVersions := List("3.3.6", "2.13.16")
ThisBuild / githubOwner := "igor-ramazanov-typelevel"
ThisBuild / githubRepository := "ciris"
ThisBuild / githubWorkflowPublishPreamble := List.empty
ThisBuild / githubWorkflowUseSbtThinClient := true
ThisBuild / githubWorkflowPublish := List(
  Run(
    commands = List("echo \"$PGP_SECRET\" | gpg --import"),
    id = None,
    name = Some("Import PGP key"),
    env = Map("PGP_SECRET" -> "${{ secrets.PGP_SECRET }}"),
    params = Map(),
    timeoutMinutes = None,
    workingDirectory = None
  ),
  Sbt(
    commands = List("+ publish"),
    id = None,
    name = Some("Publish"),
    cond = None,
    env = Map("GITHUB_TOKEN" -> "${{ secrets.GB_TOKEN }}"),
    params = Map.empty,
    timeoutMinutes = None,
    preamble = true
  )
)
ThisBuild / gpgWarnOnFailure := false

val catsEffectVersion = "3.7-4972921"
val circeVersion = "0.14.13"
val http4sVersion = "0.23.31-M1"
val http4sAwsVersion = "6.2.0-M1"
val scala213 = "2.13.16"
val scala3 = "3.3.6"

val scalaJsMajorMinorVersion =
  """"org.scala-js" % "sbt-scalajs" % "([^"]+)"""".r
    .findFirstMatchIn(IO.read(file("project/plugins.sbt")))
    .map(_.group(1))
    .flatMap(CrossVersion.partialVersion)
    .map { case (major, minor) => s"$major.$minor" }
    .getOrElse(throw new MessageOnlyException("Unable to determine Scala.js plugin version."))

val scalaNativeMajorMinorVersion =
  """"org.scala-native" % "sbt-scala-native" % "([^"]+)"""".r
    .findFirstMatchIn(IO.read(file("project/plugins.sbt")))
    .map(_.group(1))
    .flatMap(CrossVersion.partialVersion)
    .map { case (major, minor) => s"$major.$minor" }
    .getOrElse(throw new MessageOnlyException("Unable to determine Scala Native plugin version."))

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / doctestTestFramework := DoctestTestFramework.Munit
ThisBuild / nativeConfig ~= { _.withMode(Mode.debug) }
ThisBuild / version := "3.9.0-M1"

lazy val ciris = project
  .in(file("."))
  .settings(
    mimaSettings,
    noPublishSettings,
    console := (core.jvm / Compile / console).value,
    Test / console := (core.jvm / Test / console).value
  )
  .aggregate(
    core.js,
    core.jvm,
    core.native,
    circe.js,
    circe.jvm,
    circe.native,
    http4s.js,
    http4s.jvm,
    http4s.native,
    http4sAws.jvm
  )

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/core"))
  .settings(
    moduleName := "ciris",
    name := moduleName.value,
    dependencySettings ++ Seq(
      libraryDependencies += "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion
    ),
    publishSettings,
    mimaSettings,
    testSettings,
    headerSources / excludeFilter :=
      HiddenFileFilter ||
        "*GeneralDigest.scala" ||
        "*Pack.scala" ||
        "*SHA1Digest.scala",
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .jsSettings(sharedJsSettings)
  .nativeSettings(sharedNativeSettings)

lazy val circe = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/circe"))
  .settings(
    moduleName := "ciris-circe",
    name := moduleName.value,
    dependencySettings ++ Seq(
      libraryDependencies += "io.circe" %%% "circe-parser" % circeVersion
    ),
    publishSettings,
    mimaSettings,
    testSettings,
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .jsSettings(sharedJsSettings)
  .nativeSettings(sharedNativeSettings)
  .dependsOn(core)

lazy val http4s = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/http4s"))
  .settings(
    moduleName := "ciris-http4s",
    name := moduleName.value,
    dependencySettings ++ Seq(
      libraryDependencies += "org.http4s" %%% "http4s-core" % http4sVersion
    ),
    publishSettings,
    mimaSettings,
    testSettings,
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .jsSettings(
    sharedJsSettings,
    Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  )
  .nativeSettings(sharedNativeSettings)
  .dependsOn(core)

lazy val http4sAws = crossProject(JVMPlatform, NativePlatform)
  .in(file("modules/http4s-aws"))
  .settings(
    moduleName := "ciris-http4s-aws",
    name := moduleName.value,
    dependencySettings ++ Seq(
      libraryDependencies += "com.magine" %% "http4s-aws" % http4sAwsVersion
    ),
    publishSettings,
    mimaSettings,
    testSettings,
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .dependsOn(http4s)

lazy val dependencySettings = Seq(
  libraryDependencies ++= {
    if (scalaVersion.value.startsWith("3")) Nil
    else
      Seq(compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.3").cross(CrossVersion.full)))
  },
  libraryDependencies ++= Seq(
    "org.typelevel" %%% "munit-cats-effect" % "2.2.0-M1",
    "org.typelevel" %%% "scalacheck-effect-munit" % "2.1.0-M1",
    "org.typelevel" %%% "cats-effect-laws" % catsEffectVersion,
    "org.typelevel" %%% "cats-effect" % catsEffectVersion
  ).map(_ % Test),
  pomPostProcess := { (node: xml.Node) =>
    new xml.transform.RuleTransformer(new xml.transform.RewriteRule {
      def scopedDependency(e: xml.Elem): Boolean =
        e.label == "dependency" && e.child.exists(_.label == "scope")

      override def transform(node: xml.Node): xml.NodeSeq =
        node match {
          case e: xml.Elem if scopedDependency(e) => Nil
          case _                                  => Seq(node)
        }
    }).transform(node).head
  }
)

lazy val buildInfoSettings = Seq(
  buildInfoPackage := "ciris.build",
  buildInfoObject := "info",
  buildInfoKeys := Seq[BuildInfoKey](
    scalaVersion,
    scalacOptions,
    sourceDirectory,
    ThisBuild / latestVersion,
    // format: off
    BuildInfoKey.map(ThisBuild / version) { case (_, v) => "latestSnapshotVersion" -> v },
    BuildInfoKey.map(LocalRootProject / baseDirectory) { case (k, v) => "rootDirectory" -> v },
    BuildInfoKey.map(core.jvm / moduleName) { case (k, v) => "core" ++ k.capitalize -> v },
    BuildInfoKey.map(core.jvm / crossScalaVersions) { case (k, v) => "core" ++ k.capitalize -> v },
    BuildInfoKey.map(core.js / crossScalaVersions) { case (k, v) => "coreJs" ++ k.capitalize -> v },
    BuildInfoKey.map(core.native / crossScalaVersions) { case (k, v) => "coreNative" ++ k.capitalize -> v },
    BuildInfoKey.map(circe.jvm / moduleName) { case (k, v) => "circe" ++ k.capitalize -> v },
    BuildInfoKey.map(circe.jvm / crossScalaVersions) { case (k, v) => "circe" ++ k.capitalize -> v },
    BuildInfoKey.map(circe.js / crossScalaVersions) { case (k, v) => "circeJs" ++ k.capitalize -> v },
    BuildInfoKey.map(circe.native / crossScalaVersions) { case (k, v) => "circeNative" ++ k.capitalize -> v },
    BuildInfoKey.map(http4s.jvm / moduleName) { case (k, v) => "http4s" ++ k.capitalize -> v },
    BuildInfoKey.map(http4s.jvm / crossScalaVersions) { case (k, v) => "http4s" ++ k.capitalize -> v },
    BuildInfoKey.map(http4s.js / crossScalaVersions) { case (k, v) => "http4sJs" ++ k.capitalize -> v },
    BuildInfoKey.map(http4s.native / crossScalaVersions) { case (k, v) => "http4sNative" ++ k.capitalize -> v },
    BuildInfoKey.map(http4sAws.jvm / moduleName) { case (k, v) => "http4sAws" ++ k.capitalize -> v },
    BuildInfoKey.map(http4sAws.jvm / crossScalaVersions) { case (k, v) => "http4sAws" ++ k.capitalize -> v },
    LocalRootProject / organization,
    core.jvm / crossScalaVersions,
    BuildInfoKey("catsEffectVersion" -> catsEffectVersion),
    BuildInfoKey("circeVersion" -> circeVersion),
    BuildInfoKey("http4sVersion" -> http4sVersion),
    BuildInfoKey("http4sAwsVersion" -> http4sAwsVersion),
    BuildInfoKey("scalaJsMajorMinorVersion" -> scalaJsMajorMinorVersion),
    BuildInfoKey("scalaNativeMajorMinorVersion" -> scalaNativeMajorMinorVersion)
    // format: on
  )
)

lazy val metadataSettings = Seq(
  organization := "is.cir",
  organizationName := "Ciris",
  organizationHomepage := Some(url("https://cir.is"))
)

lazy val publishSettings =
  metadataSettings ++ Seq(
    Test / publishArtifact := false,
    pomIncludeRepository := (_ => false),
    homepage := Some(url("https://cir.is")),
    licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
    startYear := Some(2017),
    headerLicense := Some(
      de.heikoseeberger.sbtheader.License.MIT(
        s"${startYear.value.get}-${java.time.Year.now}",
        "Viktor Rudebeck",
        HeaderLicenseStyle.SpdxSyntax
      )
    ),
    headerSources / excludeFilter := HiddenFileFilter,
    developers := List(
      Developer(
        id = "vlovgr",
        name = "Viktor Rudebeck",
        email = "github@vlovgr.se",
        url = url("https://vlovgr.se")
      )
    )
  )

lazy val mimaSettings = Seq(
  mimaPreviousArtifacts := {
    val unpublishedModules = Set[String]("ciris-http4s-aws")
    if (publishArtifact.value && !unpublishedModules.contains(moduleName.value)) {
      Set(organization.value %% moduleName.value % (ThisBuild / previousStableVersion).value.get)
    } else Set()
  },
  mimaBinaryIssueFilters ++= {
    import com.typesafe.tools.mima.core._
    // format: off
    Seq(
      ProblemFilters.exclude[DirectMissingMethodProblem]("ciris.ConfigKey.file"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("ciris.package.file")
    )
    // format: on
  }
)

lazy val noPublishSettings =
  publishSettings ++ Seq(
    publish / skip := true,
    publishArtifact := false
  )

lazy val sharedJsSettings = Seq(
  doctestGenTests := Seq.empty
)

lazy val sharedNativeSettings = Seq(
)

lazy val scalaSettings = Seq(
  scalaVersion := scala213,
  javacOptions ++= Seq("--release", "8"),
  scalacOptions ++= {
    val commonScalacOptions =
      Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked"
      )

    val scala2ScalacOptions =
      if (scalaVersion.value.startsWith("2.")) {
        Seq(
          "-language:higherKinds",
          "-Xlint",
          "-Ywarn-dead-code",
          "-Ywarn-numeric-widen",
          "-Ywarn-value-discard",
          "-Ywarn-unused"
        )
      } else Seq()

    val scala3ScalacOptions =
      if (scalaVersion.value.startsWith("3")) {
        Seq("-Ykind-projector", "-Yretain-trees")
      } else Seq()

    commonScalacOptions ++
      scala2ScalacOptions ++
      scala3ScalacOptions
  },
  Compile / console / scalacOptions --= Seq("-Xlint", "-Ywarn-unused"),
  Test / console / scalacOptions := (Compile / console / scalacOptions).value
)

lazy val testSettings = Seq(
  Test / logBuffered := false,
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument("-oDF"),
  Test / scalacOptions --= Seq("-deprecation", "-Xfatal-warnings", "-Xlint", "-Ywarn-unused")
)

def scalaVersionOf(version: String): String = {
  if (version.contains("-")) version
  else {
    val (major, minor) =
      CrossVersion.partialVersion(version).get
    s"$major.$minor"
  }
}

val latestVersion = settingKey[String]("Latest stable released version")
ThisBuild / latestVersion := {
  val snapshot = (ThisBuild / isSnapshot).value
  val stable = (ThisBuild / isVersionStable).value

  if (!snapshot && stable) {
    (ThisBuild / version).value
  } else {
    (ThisBuild / previousStableVersion).value.get
  }
}

val updateSiteVariables = taskKey[Unit]("Update site variables")
ThisBuild / updateSiteVariables := {
  val file =
    (LocalRootProject / baseDirectory).value / "website" / "variables.js"

  val variables =
    Map[String, String](
      "organization" -> (LocalRootProject / organization).value,
      "coreModuleName" -> (core.jvm / moduleName).value,
      "latestVersion" -> (ThisBuild / latestVersion).value,
      "scalaPublishVersions" -> {
        val scalaVersions = (core.jvm / crossScalaVersions).value.map(scalaVersionOf)
        if (scalaVersions.size <= 2) scalaVersions.mkString(" and ")
        else scalaVersions.init.mkString(", ") ++ " and " ++ scalaVersions.last
      },
      "scalaJsMajorMinorVersion" -> scalaJsMajorMinorVersion,
      "scalaNativeMajorMinorVersion" -> scalaNativeMajorMinorVersion
    )

  val fileHeader =
    "// Generated by sbt. Do not edit directly."

  val fileContents =
    variables.toList
      .sortBy { case (key, _) => key }
      .map { case (key, value) => s"  $key: '$value'" }
      .mkString(s"$fileHeader\nmodule.exports = {\n", ",\n", "\n};\n")

  IO.write(file, fileContents)
}

def addCommandsAlias(name: String, values: List[String]) =
  addCommandAlias(name, values.mkString(";", ";", ""))

addCommandsAlias(
  "validate",
  List(
    "+clean",
    "+test",
    "+mimaReportBinaryIssues",
    "scalafmtCheckAll",
    "scalafmtSbtCheck",
    "headerCheckAll",
    "+doc",
    "docs/run"
  )
)
