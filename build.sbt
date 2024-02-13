ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "Gestion de stock",
    libraryDependencies ++= Seq(
      "org.openjfx" % "javafx-controls" % "17",
      "org.openjfx" % "javafx-fxml" % "17",
      "org.openjfx" % "javafx-graphics" % "17",
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.4.0",
      "org.controlsfx" % "controlsfx" % "11.1.0" // Replace with the latest version

    )
  )

