name := "sbt-codes"

version := "0.1.0-SNAPSHOT"

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

pomExtra <<= pomExtra(_ ++ developers)

scalacOptions in Compile += Opts.compile.deprecation

publish <<= PgpKeys.publishSigned

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")

addSbtPlugin("no.arktekk.sbt" % "aether-deploy" % "0.12.1")

// Impl
def developers = {
  val developers = Map(
    "arashi01" -> "Ali Salim Rashid"
  )
  <developers>
    {developers map { m =>
    <developer>
      <id>{m._1}</id>
      <name>{m._2}</name>
      <url>http://github.com/{m._1}</url>
    </developer>
  }}
  </developers>
}
