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
import com.typesafe.sbt.SbtGit
import com.typesafe.sbt.SbtGit.GitKeys
import sbt.ScopeFilter.ProjectFilter
import sbt._
import Keys._
import plugins.JvmPlugin
import java.util.jar.Attributes

import sbtunidoc.Plugin.UnidocKeys
import sbtunidoc.{Plugin => SbtUnidoc}

import scala.util.Try


object CodesPlugin extends AutoPlugin {


  override val trigger: PluginTrigger = allRequirements

  override val requires: Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Def.Setting[_]] = codesSettings

  override lazy val buildSettings: Seq[Def.Setting[_]] = codesBuildSettings

  sealed trait Keys {
    self: Import =>
    
    private def notModified = "This setting should not be modified."

    val codesBuildNumber = settingKey[Option[String]](s"Build number extracted from the CI environment. $notModified" )

    val codesTeamcity = settingKey[Boolean](s"'true' if current build is running under TeamCity. $notModified")

    val codesImplementationVersion = settingKey[String](s"Implementation version computed from the current build." +
      s" $notModified")

    val codesProfile = settingKey[BuildProfile]("'BuildProfile' used to determine build specific settings.")

    val codesRelease = settingKey[Boolean](s"'true' if current build is a stable release. $notModified")

    val codesVersionMessage = taskKey[Unit]("Updates the current TeamCity build number with the project " +
      "implementation version.")

    val codesDevelopers = settingKey[Option[Seq[Developer]]]("The developers of the current build project.")

    val codesGithubRepo = settingKey[GithubRepo]("The remote GitHub repository for the current build project." +
      " Defaults to following Reactive Codes' naming conventions, and 'reactivecodes' github organisation.")

    val codesDocFooter = settingKey[Option[String]]("The footer displayed at the bottom of generated API " +
      "documentation. If not set, a default is composed from the project build information.")

    val codesRootDoc = settingKey[Option[String]]("The location of the 'rootdoc' API documentation file for the " +
      "'root' package.")

    def vcsRevision = GitKeys.gitHeadCommit

    def vcsBranch = GitKeys.gitCurrentBranch
  }

  sealed trait Import {

    case class GithubRepo(owner: String, repo: String) {
      def browseUrl = s"https://github.com/$owner/$repo"
      def connection = s"scm:git:$browseUrl.git"
      def developerConnection = s"scm:git:git@github.com:$owner/$repo.git"
      def scmInfo = ScmInfo(url(browseUrl), connection, Some(developerConnection))

      override def toString: String = browseUrl
    }

    case class Developer(name: String, githubUsername: String)

    sealed trait BuildProfile

    case object ReleaseProfile extends BuildProfile

    case object MilestoneProfile extends BuildProfile

    case object IntegrationProfile extends BuildProfile

    case object DevelopmentProfile extends BuildProfile

    object Codes {

      lazy val deps = new {

        val scalazCore: (String) => ModuleID = "org.scalaz" %% "scalaz-core" % _

        val scalaTest: (String) => ModuleID = "org.scalatest" %% "scalatest" % _ % Test

        val specs2: (String) => ModuleID  = "org.specs2" %% "specs2" % _ %  Test

        val mockitoCore: (String) => ModuleID = "org.mockito" % "mockito-core" % _ % Test
      }

    }

  }

  object autoImport extends Import with Keys {

    def publishOSS(ref: Reference): Setting[Option[Resolver]] =
      publishTo in ref <<= codesRelease((r) => if (r) Some(sonatypeOSSStaging) else
        Some(Resolver.sonatypeRepo("snapshots")))

    def publishOSS: Setting[Option[Resolver]] = publishOSS(ThisProject)

    def apacheLicensed: Setting[Seq[(String, URL)]] = apacheLicensed(ThisProject)

    def apacheLicensed(ref: Reference): Setting[Seq[(String, URL)]] =
      licenses in ref := Seq("Apache 2.0 License" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

    def codesCompileOpts: Setting[Task[Seq[String]]] = scalacOptions in(Compile, compile) := {
      val opts = (scalacOptions in(Compile, compile)).value ++ Seq(Opts.compile.deprecation, "-feature")
      val devOpts = opts ++ Seq(Opts.compile.unchecked, "–Xlint", "-Xcheckinit")
      codesProfile.value match {
        case DevelopmentProfile => devOpts
        case _ => opts
      }
    }

    def codesDocOpts: Seq[Setting[_]] = inConfig(Compile)(inTask(doc)(codesBaseDocOpts))

    def codesUnidocOpts: Seq[Setting[_]] = SbtUnidoc.unidocSettings ++
      inConfig(SbtUnidoc.ScalaUnidoc)(inTask(SbtUnidoc.UnidocKeys.unidoc)(codesBaseDocOpts)) ++ Seq(
      scalacOptions in ThisBuild ++= Seq("-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath),
      apiMappings in ThisBuild +=
        (scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"))
    )

    def codesUnidocOpts(filter: ProjectReference*): Seq[Setting[_]] = codesUnidocOpts :+ filterUnidoc(filter: _*)
    
    def filterUnidoc(ref: ProjectReference*): Setting[ProjectFilter] =
      SbtUnidoc.UnidocKeys.unidocProjectFilter in (SbtUnidoc.ScalaUnidoc, UnidocKeys.unidoc) :=
        inAnyProject -- inProjects(ref: _*)

    implicit val codesProjectOps: Project => CodesProjectOps = new CodesProjectOps(_)

    final class CodesProjectOps(val u: Project) extends AnyVal {
      def compileOpts: Project = u.settings(codesCompileOpts)
      def docOpts: Project = u.settings(autoImport.codesDocOpts: _*)
      def unidocOpts: Project = u.settings(codesUnidocOpts: _*)
      def unidocOpts(filter: ProjectReference*): Project = u.settings(codesUnidocOpts(filter: _*): _*)
      def apacheLicensed: Project = u.settings(autoImport.apacheLicensed)
      def publishOSS: Project = u.settings(autoImport.publishOSS)
    }
  }

  import autoImport._

  def pluginSettings = Seq(
    codesImplementationVersion := implementationVer(codesRelease.value, codesProfile.value, codesTeamcity.value,
      version.value, codesBuildNumber.value, vcsRevision.value),
    codesDevelopers <<= codesDevelopers ?? None,
    codesRootDoc <<= codesRootDoc ?? None,
    codesDocFooter <<= codesDocFooter ?? None,
      codesRelease := {
      def `release/milestone` = codesProfile.value match {
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
      if (codesDevelopers.value.isEmpty) pomExtra.value else pomExtra.value ++
        developersToXml(codesDevelopers.value.get: _*)
    },
    isSnapshot := {
      def snapshotMatch(s: String) = s.contains("SNAPSHOT") || s.contains("snapshot")
      val isSnapshot = snapshotMatch(version.value)
      val snapshotDeps = libraryDependencies.value.filter((dep) => snapshotMatch(dep.revision) ||
        snapshotMatch(dep.name))
      val containsSnapshotDeps = snapshotDeps.isEmpty
      if (!isSnapshot && containsSnapshotDeps) true else isSnapshot
    },
    scmInfo <<= codesGithubRepo(r => Some(ScmInfo(url(r.browseUrl), r.connection, Some(r.developerConnection))))
  )

  def codesBaseDocOpts = Seq(
    autoAPIMappings := true,
    scalacOptions <++= (name, version, vcsRevision, vcsBranch, scalaBinaryVersion, codesGithubRepo,
      codesRootDoc, codesDocFooter, baseDirectory in LocalRootProject) map {
      (n, v, r, b, s, repo, rootDoc, footer, base) =>
        Seq(
          "-doc-source-url", s"${repo.browseUrl}/blob/${r map (_ take 7) getOrElse b}€{FILE_PATH}.scala",
          "-doc-root-content", rootDoc getOrElse s"${base.getPath}/rootdoc.txt",
          s"-doc-title", n,
          s"-doc-version", v,
          s"-doc-footer", footer.getOrElse(
            s"$n $v (Rev. ${r.map(_.take(7)).getOrElse("UNKNOWN")}) Scala $s API Documentation."),
          "-implicits",
          "-diagrams")
    }
  )

  def artefactSettings = Aether.aetherSignedSettings ++ Seq(
    publish <<= Aether.deploy,
    publishLocal <<= PgpKeys.publishLocalSigned,
    packageOptions in(Compile, packageBin) +=
      Package.ManifestAttributes(
        Attributes.Name.SPECIFICATION_VERSION -> version.value,
        Attributes.Name.IMPLEMENTATION_VERSION -> codesImplementationVersion.value
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

  private def codesBuildSettings = Seq(
    codesTeamcity := sys.env.get("TEAMCITY_VERSION").nonEmpty,
    codesProfile <<= codesProfile ?? guessProfile,
    codesBuildNumber := tcBuildNumber(codesTeamcity.value),
    codesGithubRepo := GithubRepo("reactivecodes", (normalizedName in LocalRootProject).value),
    codesVersionMessage in LocalRootProject := tcBuildMetaTask(codesTeamcity.value, (codesImplementationVersion in LocalRootProject).value)
  )

  // Used to formalize project name for projects declared with the syntax 'val fooProject = project ...'
  def formalize(name: String): String = name.split("-|(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")
    .map(_.capitalize).mkString(" ")

  // Append relevent build implementation information to the version/revision
  private def implementationVer(release: Boolean, profile: BuildProfile, teamcity: Boolean, version: String, buildNumber: Option[String], buildVCSNumber: Option[String]) =
      s"$version+${implementationMeta(release, profile, teamcity, buildNumber, buildVCSNumber).mkString(".")}"

  // Build the build implementation meta information
  private def implementationMeta(release: Boolean, profile: BuildProfile, teamcity: Boolean, buildNumber: Option[String], buildVCSNumber: Option[String]) = {
    def vcsNo = buildVCSNumber.map(_.take(7))
    def published: Boolean = release || { profile match {
        case ReleaseProfile | MilestoneProfile | IntegrationProfile => true;
        case _ => false
      }}
    def buildNo = buildNumber.map(n => if (published) s"b$n" else s"DEV-b$n")
    Seq(buildNo, vcsNo).filter(_.nonEmpty).map(_.get)
  }

  // Obtain the build number if running in TeamCity
  private def tcBuildNumber(teamcity: Boolean) = sys.env.get("BUILD_NUMBER")

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

  // Guess the profile. *Should* normally be set via 'set profile := ...' in a CI or release build
  private def guessProfile = sys.props.getOrElse("profile", "development") match {
    case "development" => DevelopmentProfile
    case "integration" => IntegrationProfile
    case "milestone" => MilestoneProfile
    case "release" => ReleaseProfile
  }
}
