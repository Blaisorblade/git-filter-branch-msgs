import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

name := "git-filter-branch-msgs"

scalaVersion := "2.11.2"

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

javaSource in Compile := baseDirectory.value / "src"

javaSource in Test := baseDirectory.value / "test-src"

libraryDependencies ++= Seq("com.martiansoftware" % "nailgun-server" % "0.9.1",
    "org.scalaz" %% "scalaz-core" % "7.0.6")

mainClass := Some("filterbranch.Server")

mainClass in Compile := mainClass.value

packageArchetype.java_application

bashScriptConfigLocation := Some("${app_home}/../conf/jvmopts")
