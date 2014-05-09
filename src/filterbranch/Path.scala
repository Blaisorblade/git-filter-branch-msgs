package filterbranch

import java.io.File
import language.implicitConversions

//Copied from SBT
final class RichFile(val asFile: File) {
  def /(component: String): File = if (component == ".") asFile else new File(asFile, component)
  /** True if and only if the wrapped file exists.*/
  def exists = asFile.exists
  /** True if and only if the wrapped file is a directory.*/
  def isDirectory = asFile.isDirectory
  /** The last modified time of the wrapped file.*/
  def lastModified = asFile.lastModified
  /** The wrapped file converted to a <code>URL</code>.*/
  def asURL = asFile.toURI.toURL
  def absolutePath: String = asFile.getAbsolutePath

  /** The last component of this path.*/
  def name = asFile.getName
  /** The extension part of the name of this path.  This is the part of the name after the last period, or the empty string if there is no period.*/
  def ext = baseAndExt._2
  /** The base of the name of this path.  This is the part of the name before the last period, or the full name if there is no period.*/
  def base = baseAndExt._1
  def baseAndExt: (String, String) =
    {
      val nme = name
      val dot = nme.lastIndexOf('.')
      if (dot < 0) (nme, "") else (nme.substring(0, dot), nme.substring(dot + 1))
    }

}

object PathConvs {
  implicit def richFile(file: File): RichFile = new RichFile(file)
}
