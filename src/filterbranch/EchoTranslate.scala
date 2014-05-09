package filterbranch

import language.implicitConversions

import scala.io.Source
import java.io.File

import PathConvs._

object EchoTranslate {
  val minLength = 5
  val tmpDir = ".git-rewrite"
  val filterCommitId = s"[0-9a-f]{${minLength},40}".r

  def mapHash(hash: String): String = {
    val output = new File(tmpDir) / "map" / hash
    if (output.exists())
      Source.fromFile(output).mkString.trim()
    else
      hash
  }
  def filter(inpLine: String): String =
    filterCommitId.replaceAllIn(inpLine, aMatch => mapHash(aMatch.matched))

  def main(args: Array[String]) {
    for (inpLine <- Source.stdin.getLines)
      Console.out.println(filter(inpLine))
  }
}
