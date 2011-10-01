name := "jdub-miit"

version := "1.0"

organization := "ru.miit"

scalaVersion := "2.9.1"

resolvers += "Codahale Repo" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
    "com.yammer.metrics" %% "metrics-scala" % "2.0.0-BETA16",
    "com.codahale" %% "logula" % "2.1.3",
    "org.apache.tomcat" % "dbcp" % "6.0.33",
    "com.codahale" %% "simplespec" % "0.5.1" % "test",
    "org.hsqldb" % "hsqldb" % "2.2.4" % "test"
)