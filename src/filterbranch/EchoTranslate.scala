package filterbranch

import java.io.{File, FileNotFoundException, FileWriter, PrintWriter}
import java.net.InetAddress

import scala.io.Source
import scala.language.implicitConversions
import scala.sys.process.{Process, ProcessLogger}
import scala.util.{DynamicVariable, Try}

import com.martiansoftware.nailgun.{Alias, NGConstants, NGContext, NGServer}

import PathConvs._

object EchoTranslate {
  //Minimal length of hex string for it to be considered an hash.
  val minLength = 5
  // XXX: this can be configured for git-filter-branch, we should allow the same.
  val tmpDir = ".git-rewrite"
  val filterCommitId = raw"""\b[0-9a-f]{${minLength},40}\b""".r

  def tmpDirF = cwd.value / tmpDir
  val errLogName = "echo-translate.log"
  val errLogger = new DynamicVariable[PrintWriter](null)
  val cwd = new DynamicVariable[File](new File("."))

  def canonicalizeHash(partialHash: String) =
    Try[String] {
      // Run cmd, log lines on standard error, return the standard output, or fail if the exit status is nonzero.
      (Process(s"git rev-parse $partialHash", cwd.value) !! ProcessLogger(errLine => errLogger.value println errLine)).trim
    }

  //Implements map from git-filter-branch.
  def mapHash(hash: String) =
    Try[String] {
      val output = tmpDirF / "map" / hash
      if (output.exists())
        Source.fromFile(output).mkString.trim()
      else {
        val errMsg = s"${output.getCanonicalPath} does not exist!"
        errLogger.value println errMsg
        throw new FileNotFoundException(errMsg)
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
    val newErr = new PrintWriter(new FileWriter(cwd.value / errLogName, /* append = */ true), /* autoFlush = */ true)
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
    server.getAliasManager().addAlias(new Alias("git-rev-translate", "Foo", Class.forName(EchoTranslate.getClass().getName stripSuffix "$")))
    new Thread(server).start()
  }
}
