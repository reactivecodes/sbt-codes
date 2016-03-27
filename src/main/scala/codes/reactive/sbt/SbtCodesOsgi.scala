/**********************************************************************
 * See the NOTICE file distributed with this work for additional      *
 *  information regarding Copyright ownership.  The author/authors    *
 *  license this file to you under the terms of the Apache License,   *
 *  Version 2.0 (the "License");  you may not use this file except    *
 *  in compliance with the License.  You may obtain a copy of the     *
 *  License at:                                                       *
 *                                                                    *
 *      http://www.apache.org/licenses/LICENSE-2.0                    *
 *                                                                    *
 *  Unless required by applicable law or agreed to in writing,        *
 *  software distributed under the License is distributed on an       *
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,      *
 *  either express or implied.  See the License for the specific      *
 *  language governing permissions and limitations under the          *
 *  License.                                                          *
 **********************************************************************/

package codes.reactive.sbt

import com.typesafe.sbt.osgi.{OsgiKeys, SbtOsgi}
import sbt.Keys._
import sbt._


object SbtCodesOsgi extends AutoPlugin {

  object autoImport {
    implicit val osgiProjectOpts: Project => OsgiProjectOps = new OsgiProjectOps(_)
    def codesOsgiSettings: Seq[Setting[_]] = osgiSettings
  }

  final class OsgiProjectOps(val u: Project) extends AnyVal {
    def codesOsgi = u.enablePlugins(SbtCodesOsgi)
  }

  override val requires: Plugins = SbtOsgi && SbtCodes

  override val trigger: PluginTrigger = noTrigger

  override lazy val projectSettings: Seq[Def.Setting[_]] = osgiSettings

  def osgiSettings: Seq[Setting[_]] = Seq(
    packagedArtifact in (Compile, packageBin) <<= (artifact in (Compile, packageBin), OsgiKeys.bundle).identityMap,
    OsgiKeys.bundleSymbolicName := symName(Keys.organization.value, Keys.normalizedName.value),
    OsgiKeys.privatePackage := Seq(s"${OsgiKeys.bundleSymbolicName.value}*"),
    OsgiKeys.importPackage := Seq(
      "scala*;version=\"%s\"".format(osgiVersionRange(scalaVersion.value)),
      "*"),
    OsgiKeys.exportPackage := Seq(
      s"!${OsgiKeys.bundleSymbolicName.value}*impl",
      s"${OsgiKeys.bundleSymbolicName.value}*"),
    OsgiKeys.additionalHeaders := Map(
      "Implementation-Version" -> SbtCodes.autoImport.codesImplementationVersion.value,
      "-removeheaders" -> "Include-Resource,Private-Package")
  )

  /** Create an OSGi version range for standard Scala / Typesafe versioning
    * schemes that describes binary compatible versions. Copied from Slick Build.scala. */
  def osgiVersionRange(version: String, requireMicro: Boolean = false): String =
    if(version contains '-') "${@}" // M, RC or SNAPSHOT -> exact version
    else if(requireMicro) "$<range;[===,=+)>" // At least the same micro version
    else "${range;[==,=+)}" // Any binary compatible version

  private def symName(org: String, name: String) = {
    val so = org.split('.').last
    if (name.startsWith(s"$so-")) s"$org.${name.stripPrefix(s"$so-")}" else s"$org.$name"
  }

}
