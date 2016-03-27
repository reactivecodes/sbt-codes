name := "sbt-codes"

version := "0.4.0-SNAPSHOT"

organization := "codes.reactive.sbt"

description := "SBT Plugin for consistent configuration of reactive.codes SBT Projects."

startYear := Some(2014)

sbtPlugin := true

publishMavenStyle := true

scmInfo := Some(ScmInfo(url("https://github.com/reactivecodes/sbt-codes"),
  "scm:git:https://github.com/reactivecodes/sbt-codes.git"))

homepage := Some(url("https://github.com/reactivecodes/sbt-codes"))

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := (_ => false)

pomExtra <<= pomExtra(_ ++ devs)

scalacOptions in Compile += Opts.compile.deprecation

publish <<= PgpKeys.publishSigned

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.16")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.8.0" )

addSbtPlugin("org.jetbrains.teamcity.plugins" % "sbt-teamcity-logger" % "0.3.0")

// Impl
def devs = {
  val developers = Map(
    "arashi01" → "Ali Salim Rashid"
  )
  <developers>
    {developers map { m ⇒
    <developer>
      <id>{m._1}</id>
      <name>{m._2}</name>
      <url>http://github.com/{m._1}</url>
    </developer>
  }}
  </developers>
}
