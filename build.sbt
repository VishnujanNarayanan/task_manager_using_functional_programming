import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

enablePlugins(ScalaJSPlugin)

name := "functional-task-manager"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.3.3"

// Scoped to Compile deliberately. Scala.js links the test sources with a *test*
// module initializer, and declaring a main initializer for the same configuration
// fails the link step -- so an unscoped `true` here is fine until the day the first
// test arrives, and then breaks the build.
Compile / scalaJSUseMainModuleInitializer := true

libraryDependencies += "com.raquo" %%% "laminar" % "17.0.0"
libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test

// sbt discovers frameworks from the classpath on the JVM, but the Scala.js test
// adapter needs the framework named explicitly or it reports "no tests found".
testFrameworks += new TestFramework("munit.Framework")
