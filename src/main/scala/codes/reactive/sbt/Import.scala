package codes.reactive.sbt

import com.typesafe.sbt.SbtGit.GitKeys
import sbt.ScopeFilter.ProjectFilter
import sbt._
import Keys._
import sbtunidoc.Plugin.UnidocKeys
import sbtunidoc.{Plugin ⇒ SbtUnidoc}


object Import {

  private def notModified = "This setting should not be modified."

  val codesBuildNumber = settingKey[Option[String]](s"Build number extracted from the CI environment. $notModified")

  val codesTeamcity = settingKey[Boolean](s"'true' if current build is running under TeamCity. $notModified")

  val codesImplementationVersion = settingKey[String](s"Implementation version computed from the current build." +
    s" $notModified")

  val codesProfile = settingKey[BuildProfile]("'BuildProfile' used to determine build specific settings.")

  val codesRelease = settingKey[Boolean](s"'true' if current build is a stable release. $notModified")

  val codesVersionMessage = taskKey[Unit]("Updates the current TeamCity build number with the project " +
    "implementation version.")

  //    val codesDevelopers = settingKey[Option[Seq[Developer]]]("The developers of the current build project.")

  val codesGithubRepo = settingKey[GithubRepo]("The remote GitHub repository for the current build project." +
    " Defaults to following Reactive Codes' naming conventions, and 'reactivecodes' github organisation.")

  val codesDocFooter = settingKey[Option[String]]("The footer displayed at the bottom of generated API " +
    "documentation. If not set, a default is composed from the project build information.")

  val codesRootDoc = settingKey[Option[String]]("The location of the 'rootdoc' API documentation file for the " +
    "'root' package.")

  def vcsRevision: SettingKey[Option[String]] = GitKeys.gitHeadCommit

  def vcsBranch: SettingKey[String] = GitKeys.gitCurrentBranch

  case class GithubRepo(owner: String, repo: String) {
    def browseUrl = s"https://github.com/$owner/$repo"
    def connection = s"scm:git:$browseUrl.git"
    def developerConnection = s"scm:git:git@github.com:$owner/$repo.git"
    def scmInfo: ScmInfo = ScmInfo(url(browseUrl), connection, Some(developerConnection))
    override def toString: String = browseUrl
  }

  //    case class Developer(name: String, githubUsername: String)

  sealed trait BuildProfile

  case object ReleaseProfile extends BuildProfile

  case object IntegrationProfile extends BuildProfile

  case object DevelopmentProfile extends BuildProfile

  @deprecated object Deps {
    val scalaParserCombinators: (String) => ModuleID = "org.scala-lang.modules" %% "scala-parser-combinators" % _
    val scalaReflect: (String) => ModuleID = "org.scala-lang" % "scala-reflect" % _
    val scalaXml: (String) => ModuleID = "org.scala-lang.modules" %% "scala-xml" % _
    val scalazCore: (String) => ModuleID = "org.scalaz" %% "scalaz-core" % _
    val scalaTest: (String) => ModuleID = "org.scalatest" %% "scalatest" % _ % Test
    val specs2: (String) => ModuleID = "org.specs2" %% "specs2" % _ % Test
    val mockitoCore: (String) => ModuleID = "org.mockito" % "mockito-core" % _ % Test
  }

  def publishOSS(ref: Reference): Setting[Option[Resolver]] =
    publishTo in ref <<= codesRelease((r) => if (r) Some(sonatypeOSSStaging)
    else
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

  def codesBaseDocOpts: Seq[Setting[_]] = Seq(
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


  def codesDocOpts: Seq[Setting[_]] = inConfig(Compile)(inTask(doc)(codesBaseDocOpts))

  def addDevelopers(developers: (String, String, String)*) = Keys.developers ~= (_ ++ (developers map developer.tupled).toList)

  def developer = (id: String, name: String, email: String) ⇒ Developer(id, name, email, url(s"http://github.com/$id"))

  def codesUnidocOpts: Seq[Setting[_]] = SbtUnidoc.unidocSettings ++
    inConfig(SbtUnidoc.ScalaUnidoc)(inTask(SbtUnidoc.UnidocKeys.unidoc)(codesBaseDocOpts)) ++ Seq(
    scalacOptions in ThisBuild ++= Seq("-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath),
    apiMappings in ThisBuild +=
      (scalaInstance.value.libraryJar -> url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"))
  )

  def codesUnidocOpts(filter: ProjectReference*): Seq[Setting[_]] = codesUnidocOpts :+ filterUnidoc(filter: _*)

  def filterUnidoc(ref: ProjectReference*): Setting[ProjectFilter] =
    SbtUnidoc.UnidocKeys.unidocProjectFilter in(SbtUnidoc.ScalaUnidoc, UnidocKeys.unidoc) :=
      inAnyProject -- inProjects(ref: _*)

  implicit final class CodesProjectOps(val u: Project) extends AnyVal {
    def compileOpts: Project = u.settings(codesCompileOpts)

    def docOpts: Project = u.settings(Import.codesDocOpts: _*)

    def unidocOpts: Project = u.settings(codesUnidocOpts: _*)

    def unidocOpts(filter: ProjectReference*): Project = u.settings(codesUnidocOpts(filter: _*): _*)

    def apacheLicensed: Project = u.settings(Import.apacheLicensed)

    def publishOSS: Project = u.settings(Import.publishOSS)

    //      def sCoverageOpts: Project = u.settings(ScoverageSbtPlugin.instrumentSettings: _*)
  }

  // Sonatype OSS Staging Repo
  private def sonatypeOSSStaging: MavenRepository = s"Sonatype OSS Staging Repository" at
    s"https://oss.sonatype.org/service/local/staging/deploy/maven2"

}
