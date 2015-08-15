import org.pegdown.PegDownProcessor
import sbt.Keys._
import sbt._

object CocaCoalaBuild extends Build {

  val go = taskKey[Seq[File]]("compiles all sources from the source directory (md -> html, scss -> css)")
  val from = settingKey[File]("directory which contains the web sources")
  val to = settingKey[File]("directory where compiled web resources are written to")

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
  
  val mySettings = Seq(
    name := "Coca Coala",
    organization := "no.org",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.7",
    from := (sourceDirectory in Compile).value / "public",
    to := (target in Compile).value / "stage",
    go <<= compileMarkdown
  )

  lazy val cocaCoala = Project(
    id = "coca-coala",
    base = file(".")
  ).settings(mySettings: _*)

}
