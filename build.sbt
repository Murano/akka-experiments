lazy val root = (project in file(".")).
  settings(
    name := "akka_experiments",
    version := "0.1.3",
    scalaVersion := "2.11.7"
  )

libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.2",
  "com.typesafe.slick" %% "slick" % "3.1.1",
  "org.postgresql" % "postgresql" % "9.4.1207",
  "com.typesafe.slick" % "slick-hikaricp_2.11" % "3.1.1",
  "ch.qos.logback" % "logback-classic" % "1.1.6"
)


