/*******************************************************************
 * See the NOTICE file distributed with this work for additional   *
 * information regarding Copyright ownership.  The author/authors  *
 * license this file to you under the terms of the Apache License, *
 * Version 2.0 (the "License");  you may not use this file except  *
 * in compliance with the License.  You may obtain a copy of the   *
 * License at:                                                     *
 *                                                                 *
 *     http://www.apache.org/licenses/LICENSE-2.0                  *
 *                                                                 *
 * Unless required by applicable law or agreed to in writing,      *
 * software distributed under the License is distributed on an     *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
 * either express or implied.  See the License for the specific    *
 * language governing permissions and limitations under the        *
 * License.                                                        *
 *******************************************************************/

package codes.reactive.sbt


import aether.Aether
import com.typesafe.sbt.pgp.PgpKeys
import sbt._
import Keys._
import plugins.JvmPlugin
import java.util.jar.Attributes

import scala.util.Try


object CodesPlugin extends AutoPlugin {


  override val trigger: PluginTrigger = allRequirements

  override val requires: Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Def.Setting[_]] = codesSettings

  sealed trait Keys {
    self: Import =>

    val buildNumber =
      SettingKey[String]("codesBuildNumber",
        "Build number computed from the CI environment. This setting should not be modified.")

    val buildVCSNumber =
      SettingKey[String]("codesBuildVCSNumber",
        "VCS revision number computed from the CI environment. This setting should not be modified.")

    val teamcity =
      SettingKey[Boolean]("codesTeamcity",
        "'true' if current build is running under TeamCity. This setting should not be modified.")

    val implementationVersion =
      SettingKey[String]("codesImplementationVersion",
        "Implementation version computed from the current build. This setting should not be modified.")

    val profile =
      SettingKey[BuildProfile]("codesBuildProfile", "'BuildProfile' used to determine build specific settings.")

    val release =
      SettingKey[Boolean]("codesRelease",
        "'true' if current build is a stable release. This setting should not be modified.")

    val versionMessage =
      TaskKey[Unit]("codesVersionMessage",
        "Updates the current TeamCity build number with the project implementation version.")

    val developers = SettingKey[Option[Seq[Developer]]]("codesDevelopers", "The developers of the current build project.")
  }

  sealed trait Import {

    case class Developer(name: String, githubUsername: String)

    sealed trait BuildProfile

    case object ReleaseProfile extends BuildProfile

    case object MilestoneProfile extends BuildProfile

    case object IntegrationProfile extends BuildProfile

    case object DevelopmentProfile extends BuildProfile

    lazy val publishOSS: Setting[Option[Resolver]] = CodesPlugin.publishOSS

    def publishOSS(reference: Reference) = CodesPlugin.publishOSS(reference)

    lazy val apacheLicensed: Setting[Seq[(String, URL)]] = CodesPlugin.apacheLicensed

    def apacheLicensed(reference: Reference) = CodesPlugin.apacheLicensed(reference)

    lazy val codesCompileOpts = CodesPlugin.compileOpts

    def codesCompileOpts(reference: Reference) = CodesPlugin.compileOpts(reference)
  }

  object AutoImport extends Import with Keys

  val autoImport = AutoImport

  import autoImport._

  def pluginSettings = Seq(
    teamcity := sys.env.get("TEAMCITY_VERSION").nonEmpty,
    profile <<= profile ?? guessProfile,
    buildNumber := tcBuildNumber(teamcity.value),
    buildVCSNumber := buildVcsNumber(teamcity.value, baseDirectory.value),
    implementationVersion := implementationVer(profile.value, teamcity.value, version.value, buildNumber.value, buildVCSNumber.value),
    versionMessage := tcBuildMetaTask(teamcity.value, implementationVersion.value),
    developers <<= developers ??  None,
    release := {
      def `release/milestone` = profile.value match {
        case ReleaseProfile | MilestoneProfile => true
        case _ => false
      }
      !isSnapshot.value && `release/milestone`
    }
  )

  def baseProjectSettings = Seq(
    name ~= formalize,
    pomIncludeRepository := (_ => false),
    pomExtra := {
      if (developers.value.isEmpty) pomExtra.value else pomExtra.value ++ developersToXml(developers.value.get: _*)
    },
    isSnapshot := {
      def snapshotMatch(s: String) = s.contains("SNAPSHOT") || s.contains("snapshot")
      val isSnapshot = snapshotMatch(version.value)
      val snapshotDeps = libraryDependencies.value.filter((dep) => snapshotMatch(dep.revision) || snapshotMatch(dep.name))
      val containsSnapshotDeps = snapshotDeps.isEmpty
      if (!isSnapshot && containsSnapshotDeps) true else isSnapshot
    }
  )

  def artefactSettings = Aether.aetherSignedSettings ++ Seq(
    publish <<= Aether.deploy,
    publishLocal <<= PgpKeys.publishLocalSigned,
    packageOptions in(Compile, packageBin) +=
      Package.ManifestAttributes(
        Attributes.Name.SPECIFICATION_VERSION -> version.value,
        Attributes.Name.IMPLEMENTATION_VERSION -> implementationVersion.value
      ),
    packageOptions in(Compile, packageBin) += Package.ManifestAttributes(
      "Built-By" -> System.getProperty("java.version"),
      "Built-Time" -> java.util.Calendar.getInstance.getTimeInMillis.toString),
    mappings in(Compile, packageBin) <+= baseDirectory map {
      (base: File) => (base / "LICENSE") -> "META-INF/LICENSE"
    },
    mappings in(Compile, packageSrc) <+= baseDirectory map {
      (base: File) => (base / "LICENSE") -> "META-INF/LICENSE"
    },
    mappings in(Compile, packageBin) <+= baseDirectory map {
      (base: File) => (base / "NOTICE") -> "META-INF/NOTICE"
    },
    mappings in(Compile, packageSrc) <+= baseDirectory map {
      (base: File) => (base / "NOTICE") -> "META-INF/NOTICE"
    }
  )

  def codesSettings: Seq[Setting[_]] = pluginSettings ++ artefactSettings ++ baseProjectSettings

  // Used to formalize project name for projects declared with the syntax 'val fooProject = project ...'
  def formalize(name: String): String = name.replaceFirst("sbs", "SBS")
    .split("-|(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")
    .map(_.capitalize).mkString(" ")

  def publishOSS(ref: Reference): Setting[Option[Resolver]] = publishTo in ref <<= release((r) => if (r) Some(sonatypeOSSStaging) else Some(Resolver.sonatypeRepo("snapshots")))

  def publishOSS: Setting[Option[Resolver]] = publishOSS(ThisProject)

  def apacheLicensed(ref: Reference): Setting[Seq[(String, URL)]] = licenses in ref := Seq("Apache 2.0 License" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

  def apacheLicensed: Setting[Seq[(String, URL)]] = apacheLicensed(ThisProject)

  def compileOpts(ref: Reference): Seq[Setting[Task[Seq[String]]]] = Seq(
    scalacOptions in(ref, Compile, compile) := {
      val opts = (scalacOptions in(Compile, compile)).value ++ Seq(Opts.compile.deprecation, "-feature")
      val devOpts = opts ++ Seq(Opts.compile.unchecked, "–Xlint", "-Xcheckinit")
      profile.value match {
        case DevelopmentProfile => devOpts
        case _ => opts
      }
    },
    scalacOptions in(ref, Compile, doc) ++= Seq(
      "-doc-root-content", s"${(scalaSource in(Compile, compile)).value.getPath}/rootdoc.txt",
      s"-doc-title", name.value,
      s"-doc-version", version.value,
      "-implicits",
      s"-doc-external-doc:${scalaInstance.value.libraryJar}#http://www.scala-lang.org/api/${scalaVersion.value}/",
      "-diagrams"
    )
  )

  def compileOpts: Seq[Setting[Task[Seq[String]]]] = compileOpts(ThisProject)

  // Append relevent build implementation information to the version/revision
  private def implementationVer(profile: BuildProfile, teamcity: Boolean, version: String, buildNumber: String, buildVCSNumber: String) =
    if (!version.endsWith(implementationMeta(profile, teamcity, buildNumber, buildVCSNumber)) && !version.contains("+"))
      s"$version+${implementationMeta(profile, teamcity, buildNumber, buildVCSNumber)}"
    else version

  // Build the build implementation meta information
  private def implementationMeta(profile: BuildProfile, teamcity: Boolean, buildNumber: String, buildVCSNumber: String) = {
    def vcsNo = buildVCSNumber.take(7)
    def published: Boolean = profile match {
      case ReleaseProfile | MilestoneProfile | IntegrationProfile => true;
      case _ => false
    }
    def build = if (!teamcity) "" else if (published) s"b$buildNumber." else s"dev-b$buildNumber."

    s"$build$vcsNo"
  }

  // Obtain the VCS Revision, and truncate it
  private def buildVcsNumber(teamcity: Boolean, baseDir: File): String = {
    def isGitRepo(dir: File): Boolean = if (dir.listFiles().map(_.getName).contains(".git")) true
    else {
      val parent = dir.getParentFile
      if (parent == null) false else isGitRepo(parent)
    }

    def vcsNo = (teamcity, isGitRepo(baseDir)) match {
      case (true, _) => System.getenv("BUILD_VCS_NUMBER")
      case (false, true) => Try(Process("git rev-parse HEAD").lines.head).getOrElse("UNKNOWN")
      case _ => "UNKNOWN"
    }
    vcsNo.take(7)
  }

  // Obtain the build number if running in TeamCity
  private def tcBuildNumber(teamcity: Boolean): String = sys.env.getOrElse("BUILD_NUMBER", "UNKNOWN")

  // Update TeamCity build number with implementation meta info
  private def tcBuildMetaTask(teamcity: Boolean, implementationVersion: String) =
    if (teamcity) println(s"##teamcity[buildNumber '$implementationVersion']")

  // Produce developer POM information
  private def developersToXml(devs: Developer*) =
    <developers>
      {devs.map((developer) => {
      <developer>
        <id>
          {developer.githubUsername}
        </id>
        <name>
          {developer.name}
        </name>
        <url>https://github.com/
          {developer.githubUsername}
        </url>
      </developer>
    })}
    </developers>

  // Sonatype OSS Staging Repo
  private def sonatypeOSSStaging: MavenRepository = s"Sonatype OSS Staging Repository" at
    s"https://oss.sonatype.org/service/local/staging/deploy/maven2"

  // Guess the profile *Should* normally be set via 'set codesProfile := ...' in a CI or release build
  private def guessProfile = sys.props.getOrElse("profile", "development") match {
    case "development" => DevelopmentProfile
    case "integration" => IntegrationProfile
    case "milestone" => MilestoneProfile
    case "release" => ReleaseProfile
  }
}

