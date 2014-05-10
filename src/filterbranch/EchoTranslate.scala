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
import syntax.id._
import syntax.monad._
import syntax.std.option._
import syntax.std.boolean._

import com.martiansoftware.nailgun.{Alias, NGConstants, NGContext, NGServer}

import PathConvs._

object EchoTranslate {
  //Minimal length of hex string for it to be considered an hash.
  val minLength = 5
  // XXX: this can be configured for git-filter-branch, we should allow the same.
  val tmpDir = ".git-rewrite"
  //((?<= )|^) means that there should be, before the match, either a space or the beginning of the line.
  //Neither is counted toward the total.
  val filterCommitId = raw"""((?<= )|^)\b[0-9a-f]{${minLength},40}\b""".r

  //XXX: git-filter-branch-msgs gets called inside .git-rewrite/t, so we need to compensate for it.
  //However, I think we need something more robust — like finding enclosing .git-rewrite folders.
  def tmpDirF = cwd.value / ".." / ".." / tmpDir
  val errLogName = "echo-translate.log"
  val errLogger = new DynamicVariable[PrintWriter](null)
  val errLog = (errLine: String) => errLogger.value println errLine

  val cwd = new DynamicVariable[File](new File("."))

  type Error[T] = \/[String, T]
  def tryErr[T](t: => T): Error[T] = Try(t) match {
    case Success(t) => t.right
    case Failure(e) => e.getMessage().left
  }

  //Warning: the command is split by spaces, without respecting quotes!
  def getOutput(cmd: String) = tryErr[String] {
    (Process(cmd, cwd.value) !! ProcessLogger(errLog))
  }

  def canonicalizeHash(partialHash: String) =
    // Run cmd, log lines on standard error, return the standard output, or fail if the exit status is nonzero.
    //-q will give errors in case of serious problems, but will just exit with a non-zero code if the commit does not exist.
    getOutput(s"git rev-parse -q --verify $partialHash^{commit} --") map (_.trim) leftMap (_ => "")

  private val debug = true
  private val doPrettify = true

  def prettify(hash: String): String =
    (if (doPrettify)
      getOutput(s"""git --no-pager log --pretty=%h:"%s" -n1 ${hash}""")
    else "".left) | hash

  //Implements map from git-filter-branch.
  def mapHash(hash: String): Error[String] = {
    val output = tmpDirF / "map" / hash
    if (output.exists()) {
      for {
        content <- tryErr(Source.fromFile(output).mkString.trim())
        mapped <-
          if (!content.isEmpty() && content.split("\n").size == 1) {
            if (debug)
              errLog(s"Debug: mapped $hash to $content")
            content.right
          } else {
            s"$hash maps to zero or multiple hashes, skipping it".left
          }
      } yield mapped
    } else {
      s"mapping for ${prettify(hash)} not found: ${output.getCanonicalPath} does not exist.".left
    }
  }

  def filter(inpLine: String): String =
    filterCommitId.replaceAllIn(inpLine,
      aMatch => {
        val possibleHash = aMatch.matched
        (canonicalizeHash(possibleHash) >>= mapHash) valueOr { err =>
          if (!err.isEmpty()) errLog(err)
          possibleHash
        }
      })

  val debugCwd = false
  def main(args: Array[String]) {
    //This must be reopened each time main is called.
    val newErr = new PrintWriter(new FileWriter(errLogName, /* append = */ true), /* autoFlush = */ true)
    if (debugCwd)
      newErr.println(s"Starting in ${cwd.value.getCanonicalPath()}")
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

  def nailMain(context: NGContext) {
    context.assertLocalClient()
    cwd.value = new File(context.getWorkingDirectory())
    main(context.getArgs())
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
