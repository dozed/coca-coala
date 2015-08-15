
resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies += "org.pegdown" % "pegdown" % "1.5.0"

libraryDependencies += "org.http4s" %% "http4s-blazeserver" % "0.8.4"

libraryDependencies += "org.http4s" %% "http4s-dsl" % "0.8.4"

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.2.0")
