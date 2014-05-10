package filterbranch

import java.io.{File, FileWriter, PrintWriter}
import java.net.InetAddress

import scala.io.Source
import scala.language.implicitConversions
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{DynamicVariable, Failure, Success, Try}

import scalaz._
import \/._
import std.boolean._
import std.option._
import syntax.id._
import syntax.monad._
import syntax.std.option._
import syntax.std.boolean._

import com.martiansoftware.nailgun.{Alias, NGConstants, NGContext, NGServer}

import PathConvs._

object EchoTranslate {
  //Configuration settings.
  private val debug = true
  private val doPrettify = true
  private val debugCwd = false

  //Minimal length of hex string for it to be considered an hash.
  val minLength = 5
  // XXX: this can be configured for git-filter-branch, we should allow the same.
  val tmpDir = ".git-rewrite"
  //((?<= )|^) means that there should be, before the match, either a space or the beginning of the line.
  //Neither is counted toward the total.
  val filterCommitId = raw"""((?<= )|^)\b[0-9a-f]{${minLength},40}\b""".r

  val errLogName = "echo-translate.log"

  // End configuration.

  //XXX: git-filter-branch-msgs gets called inside .git-rewrite/t, so we need to compensate for it.
  //However, I think we need something more robust — like finding enclosing .git-rewrite folders.
  def tmpDirVar = cwdVar.value / ".." / ".." / tmpDir
  val errLogger = new DynamicVariable[PrintWriter](null)
  val errLog = (errLine: String) => errLogger.value println errLine

  val cwdVar = new DynamicVariable[File](new File("."))

  type Error[T] = \/[Option[String], T]

  def fail[T](err: String): Error[T] = err.some.left
  def fail[T](): Error[T] = none.left

  def tryErr[T](t: => T): Error[T] = Try(t) match {
    case Success(t) => t.right
    case Failure(e) => fail(e.getMessage())
  }

  //Warning: the command is split by spaces, without respecting quotes!
  def getOutput(cmd: String) = tryErr[String] {
    (Process(cmd, cwdVar.value) !! ProcessLogger(errLog))
  }

  def canonicalizeHash(partialHash: String): Error[String] =
    // Run cmd, log lines on standard error, return the standard output, or fail if the exit status is nonzero.
    //-q will give errors in case of serious problems, but will just exit with a non-zero code if the commit does not exist.
    (getOutput(s"git rev-parse -q --verify $partialHash^{commit} --") map (_.trim)) ||| fail()

  def prettify(hash: String): String =
    (if (doPrettify)
      getOutput(s"""git --no-pager log --pretty=%h:"%s" -n1 ${hash}""")
    else "".left) | hash

  //Implements map from git-filter-branch.
  def mapHash(hash: String): Error[String] = {
    val output = tmpDirVar / "map" / hash
    if (output.exists()) {
      for {
        content <- tryErr(Source.fromFile(output).mkString.trim())
        mapped <-
          if (!content.isEmpty() && content.split("\n").size == 1) {
            if (debug)
              errLog(s"Debug: mapped $hash to $content")
            content.right
          } else {
            fail(s"$hash maps to zero or multiple hashes, skipping it")
          }
      } yield mapped
    } else {
      fail(s"mapping for ${prettify(hash)} not found: ${output.getCanonicalPath} does not exist.")
    }
  }

  def filter(inpLine: String): String =
    filterCommitId.replaceAllIn(inpLine,
      aMatch => {
        val possibleHash = aMatch.matched
        (canonicalizeHash(possibleHash) >>= mapHash) valueOr { err =>
          err map (errLog)
          possibleHash
        }
      })

  def doMain(args: Array[String]) {
    //This must be reopened each time main is called.
    val newErr = new PrintWriter(new FileWriter(errLogName, /* append = */ true), /* autoFlush = */ true)
    if (debugCwd)
      newErr.println(s"Starting in ${cwdVar.value.getCanonicalPath()}")
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

  def main(args: Array[String]) {
    doMain(args)
  }

  def nailMain(context: NGContext) {
    context.assertLocalClient()
    cwdVar.value = new File(context.getWorkingDirectory())
    doMain(context.getArgs())
  }
}

object Server {
  def main(args: Array[String]) {
    val server = new NGServer(InetAddress.getLoopbackAddress(), NGConstants.DEFAULT_PORT)
    server.getAliasManager().addAlias(new Alias("git-filter-branch-msgs",
        "Replaces commit hashes by their rewritten version while running git-filter-branch",
        Class.forName(EchoTranslate.getClass().getName stripSuffix "$")))
    new Thread(server).start()
    System.out.println("git-filter-branch-msgs: Nailgun Server "
                + NGConstants.VERSION
                + " started on port "
                + NGConstants.DEFAULT_PORT
                + ".");
  }
}
