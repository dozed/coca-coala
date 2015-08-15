import org.http4s.MediaType
import org.http4s.headers.`Content-Type`
import org.http4s.server.Server
import org.pegdown.PegDownProcessor
import sbt.Keys._
import sbt._
import sbtdocker.DockerKeys._
import sbtdocker.DockerPlugin
import sbtdocker.mutable.Dockerfile

object CocaCoalaBuild extends Build {

  val go = taskKey[Seq[File]]("compiles all sources from the source directory (md -> html, scss -> css)")
  val from = settingKey[File]("directory which contains the web sources")
  val to = settingKey[File]("directory where compiled web resources are written to")

  val start = taskKey[Unit]("starts HTTP server for static resources")
  val stop = taskKey[Unit]("stops HTTP server")

  // task definition
  // - compiles *.md from  to "where" (target/stage)
  // - yields a Seq[File] of the generated files
  val compileMarkdown = Def.task[Seq[File]] {
    val markdownFiles = (from.value ** GlobFilter("*.md"))

    val pegDownProcessor = new PegDownProcessor()

    def modExt(file: File, newExt: String): File = {
      val dir = file.getPath.split(Path.sep).dropRight(1).mkString(Path.sep.toString)
      new File(f"$dir/${file.base}.$newExt")
    }

    markdownFiles pair (f => IO.relativizeFile(from.value, f)) map { case (markdownFile, relativeFile) =>

      // read markdown file to String
      val markdownTxt = markdownFile.cat.lines.mkString("\n")

      // markdown -> html
      val htmlTxt = pegDownProcessor.markdownToHtml(markdownTxt)

      // generate file name: src/main/public/roof/sky.md -> /target/stage/roof/sky.html
      val f = to.value / modExt(relativeFile, "html").getPath

      IO.write(f, htmlTxt)
      f
    }
  }


  import org.http4s.Response
  import org.http4s.dsl._
  import org.http4s.server.HttpService
  import org.http4s.server.blaze.BlazeBuilder

  import scalaz.concurrent.Task
  import scalaz.stream.Process
  import scalaz.stream.io.chunkR


  var server: Option[Server] = None

  val stopWebServer = Def.task[Unit] {
    server.foreach(_.shutdownNow)
  }
  
  val startWebServer = Def.task[Unit] {

    def staticResource(file: String): Task[Response] = {
      val bytes = Process.constant(8*1024)
        .toSource
        .through(chunkR(new java.io.FileInputStream(to.value / file)))
        .runLog
        .run
        .map(_.toArray)
        .toArray
        .flatten

      val mime = {
        val parts = file.split('.')
        if (parts.length > 0) MediaType.forExtension(parts.last)
          .getOrElse(MediaType.`application/octet-stream`)
        else MediaType.`application/octet-stream`
      }

      Ok(bytes).putHeaders(`Content-Type`(mime))
    }

    val service: HttpService = HttpService {

      case r @ GET -> Root / name => staticResource(r.pathInfo)

    }
    
    val s = BlazeBuilder
      .bindHttp(8080, "localhost")
      .mountService(service)
      .run

    server = Some(s)

  }
  
  val mySettings = Seq(
    name := "Coca Coala",
    organization := "no.org",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.7",

    resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",

    from := (sourceDirectory in Compile).value / "public",
    to := (target in Compile).value / "stage",
    go <<= compileMarkdown,
    watchSources += from.value,

    start <<= startWebServer.dependsOn(stop),
    stop <<= stopWebServer,

    docker <<= docker.dependsOn(go),
    dockerfile in docker := {

      val nginxConf = (sourceDirectory in Compile).value / "docker" / "nginx"

      new Dockerfile {

        from("ubuntu:14.04")

        runRaw("apt-get update")
        runRaw("apt-get install -y vim curl wget nginx")

        runRaw("rm -rf /etc/nginx/conf.d")
        runRaw("rm -rf /etc/nginx/sites-enabled/*")

        add(nginxConf, "/etc/nginx/conf.d")

        add(to.value, "/app/public")

        runRaw("""echo "daemon off;" >> /etc/nginx/nginx.conf""")

        expose(80)

        workDir("/app")

        cmdRaw("service nginx start")

      }

    }
  )

  lazy val cocaCoala = Project(
    id = "coca-coala",
    base = file(".")
  ).settings(mySettings: _*)
  .enablePlugins(DockerPlugin)

}
