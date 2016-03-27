/** *****************************************************************
  * See the NOTICE file distributed with this work for additional   *
  * information regarding Copyright ownership.  The author/authors  *
  * license this file to you under the terms of the Apache License, *
  * Version 2.0 (the "License");  you may not use this file except  *
  * in compliance with the License.  You may obtain a copy of the   *
  * License at:                                                     *
  * *
  * http://www.apache.org/licenses/LICENSE-2.0                  *
  * *
  * Unless required by applicable law or agreed to in writing,      *
  * software distributed under the License is distributed on an     *
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
  * either express or implied.  See the License for the specific    *
  * language governing permissions and limitations under the        *
  * License.                                                        *
  * ******************************************************************/

package codes.reactive.sbt


import java.util.jar.Attributes

import aether.SignedAetherPlugin
import com.typesafe.sbt.pgp.PgpKeys
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin


object SbtCodes extends AutoPlugin {


  override val trigger: PluginTrigger = allRequirements

  override val requires: Plugins = JvmPlugin && SignedAetherPlugin

  override lazy val projectSettings: Seq[Def.Setting[_]] = codesSettings

  override lazy val buildSettings: Seq[Def.Setting[_]] = codesBuildSettings


  override def globalSettings: Seq[Def.Setting[_]] = pluginGlobalSettings

  val autoImport = Import

  import autoImport._

  def pluginSettings = Seq(
    codesImplementationVersion := implementationVer(codesRelease.value, codesProfile.value, codesTeamcity.value,
      version.value, codesBuildNumber.value, vcsRevision.value),
    //    codesDevelopers <<= codesDevelopers ?? None,
    codesRootDoc <<= codesRootDoc ?? None,
    codesDocFooter <<= codesDocFooter ?? None
  )

  def pluginGlobalSettings = Seq(
    codesTeamcity := sys.env.get("TEAMCITY_VERSION").nonEmpty,
    codesProfile <<= codesProfile ?? {
      sys.props.getOrElse("profile", "development") match {
        case "development" => DevelopmentProfile
        case "integration" => IntegrationProfile
        case "release" => ReleaseProfile
      }
    },
    codesRelease := codesProfile.value == ReleaseProfile
  )

  def baseProjectSettings = Seq(
    name ~= formalize,
    pomIncludeRepository := (_ => false),
    //    pomExtra := { codesDevelopers.value map (d ⇒ pomExtra.value ++ developersToXml(d: _*)) getOrElse pomExtra.value },
    scmInfo <<= codesGithubRepo(r => Some(ScmInfo(url(r.browseUrl), r.connection, Some(r.developerConnection))))
  )

  def artefactSettings = {
    def licenceMappings(c: Configuration)(t: TaskKey[_]) = inConfig(c)(inTask(t)(
      Seq(mappings <++= baseDirectory map { (base: File) =>
        Seq((base / "LICENSE") -> "META-INF/LICENSE", (base / "NOTICE") -> "META-INF/NOTICE")
      })))
    val m = licenceMappings(Compile) _
    Seq(
      publish <<= aether.AetherKeys.aetherDeploy,
      publishLocal <<= PgpKeys.publishLocalSigned,
      packageOptions in(Compile, packageBin) +=
        Package.ManifestAttributes(
          Attributes.Name.SPECIFICATION_VERSION -> version.value,
          Attributes.Name.IMPLEMENTATION_VERSION -> codesImplementationVersion.value
        ),
      packageOptions in(Compile, packageBin) += Package.ManifestAttributes(
        "Built-By" -> System.getProperty("java.version"),
        "Built-Time" -> java.util.Calendar.getInstance.getTimeInMillis.toString)
    ) ++ m(packageBin) ++ m(packageSrc) ++ m(packageDoc)
  }

  def codesSettings: Seq[Setting[_]] = pluginSettings ++ artefactSettings ++ baseProjectSettings

  private def codesBuildSettings = Seq(
    codesBuildNumber := tcBuildNumber(codesTeamcity.value),
    codesGithubRepo := GithubRepo("reactivecodes", (normalizedName in LocalRootProject).value),
    codesVersionMessage in LocalRootProject := tcBuildMetaTask(codesTeamcity.value, (codesImplementationVersion in LocalRootProject).value)
  )

  // Used to formalize project name for projects declared with the syntax 'val fooProject = project ...'
  private def formalize(name: String): String = name.split("-|(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")
    .map(_.capitalize).mkString(" ")

  // Append relevant build implementation information to the version/revision
  private def implementationVer(release: Boolean, profile: BuildProfile, teamcity: Boolean, version: String, buildNumber: Option[String], buildVCSNumber: Option[String]) =
    s"$version+${implementationMeta(release, profile, teamcity, buildNumber, buildVCSNumber).mkString(".")}"

  // Build the build implementation meta information
  private def implementationMeta(release: Boolean, profile: BuildProfile, teamcity: Boolean, buildNumber: Option[String], buildVCSNumber: Option[String]): Seq[String] = {
    def vcsNo = buildVCSNumber.map(_.take(7))
    def published: Boolean = release || {
      profile match {
        case ReleaseProfile | IntegrationProfile => true;
        case _ => false
      }
    }
    def buildNo = buildNumber.map(n => if (published) s"b$n" else s"DEV-b$n")
    Seq(buildNo, vcsNo) collect { case v if v.nonEmpty ⇒ v.get }
  }

  // Obtain the build number if running in TeamCity
  private def tcBuildNumber(teamcity: Boolean) = sys.env.get("BUILD_NUMBER")

  // Update TeamCity build number with implementation meta info
  private def tcBuildMetaTask(teamcity: Boolean, implementationVersion: String) =
    if (teamcity) println(s"##teamcity[buildNumber '$implementationVersion']")
}
