import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

enablePlugins(ScalaJSPlugin)

name := "functional-task-manager"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.3.3"

scalaJSUseMainModuleInitializer := true

libraryDependencies += "com.raquo" %%% "laminar" % "17.0.0"
