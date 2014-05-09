scalaVersion := "2.11.0"

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

javaSource in Compile := baseDirectory.value / "src"

javaSource in Test := baseDirectory.value / "test-src"

libraryDependencies ++= Seq("com.martiansoftware" % "nailgun-server" % "0.9.1")
