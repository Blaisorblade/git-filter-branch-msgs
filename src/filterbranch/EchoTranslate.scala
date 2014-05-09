package filterbranch

import language.implicitConversions
import scala.io.Source
import scala.util._
import scala.sys.process._
//Only for Eclipse
import scala.sys.process.stringToProcess

import java.io.File
import java.io.PrintWriter
import java.io.FileWriter
import java.io.FileNotFoundException

import PathConvs._

object EchoTranslate {
  //Minimal length of hex string for it to be considered an hash.
  val minLength = 5
  // XXX: this can be configured for git-filter-branch, we should allow the same.
  val tmpDir = ".git-rewrite"
  // XXX: Should match on whole words.
  val filterCommitId = s"[0-9a-f]{${minLength},40}".r

  val tmpDirF = new File(tmpDir)
  val errLogFile = tmpDirF / "echo-translate.log"
  val errLogger = new DynamicVariable[PrintWriter](null)

  def canonicalizeHash(partialHash: String) =
    Try[String] {
      val outBuf = new StringBuffer
      val cmd = s"git rev-parse $partialHash"
      //val log = BasicIO(false, outBuf, Some(ProcessLogger(errLine => errLogger println errLine)))

      //Should be cmd ! log, but that method is hidden inside ProcessBuilder.
      //val exit = cmd.run(log).exitValue

      //outBuf.toString()
      (cmd !! ProcessLogger(errLine => errLogger.value println errLine)).trim
    }

  //Implements map from git-filter-branch.
  def mapHash(hash: String) =
    Try[String] {
      val output = tmpDirF / "map" / hash
      if (output.exists())
        Source.fromFile(output).mkString.trim()
      else {
        errLogger.value println ("'" + output + "'")
        throw new FileNotFoundException(output.getCanonicalPath())
      }
    }

  def filter(inpLine: String): String =
    filterCommitId.replaceAllIn(inpLine,
      aMatch => {
        val possibleHash = aMatch.matched
        (for {
          completed <- canonicalizeHash(possibleHash)
          mapped <- mapHash(completed)
        } yield mapped.trim()) getOrElse possibleHash
      })

  def main(args: Array[String]) {
    //This must be reopened each time main is called.
    val newErr = new PrintWriter(new FileWriter(errLogFile, /* append = */ true), /* autoFlush = */ true)
    try {
      errLogger.withValue(newErr) {
        val outWriter = new PrintWriter(System.out, /*autoFlush = */ true)
        for (inpLine <- Source.stdin.getLines) {
          outWriter println filter(inpLine)
        }
      }
    } finally {
      newErr.close()
    }
  }
}
