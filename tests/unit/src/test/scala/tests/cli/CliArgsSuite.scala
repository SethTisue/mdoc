package tests.cli

import java.nio.file.Files
import org.scalatest.FunSuite
import scala.meta.internal.io.PathIO
import scala.meta.testkit.DiffAssertions
import mdoc.internal.cli.Settings
import mdoc.internal.io.ConsoleReporter

class CliArgsSuite extends FunSuite with DiffAssertions {
  private val reporter = ConsoleReporter.default
  private val tmp = Files.createTempDirectory("mdoc")
  Files.delete(tmp)
  private val base = Settings.default(PathIO.workingDirectory)

  def checkOk(args: List[String], isExpected: Settings => Boolean): Unit = {
    test(args.mkString(" ")) {
      val obtained = Settings.fromCliArgs(args, base).get
      assert(isExpected(obtained))
    }
  }

  def checkError(args: List[String], expected: String): Unit = {
    test(args.mkString(" ")) {
      Settings.fromCliArgs(args, base).andThen(_.validate(reporter)).toEither match {
        case Left(obtained) =>
          assertNoDiff(obtained.toString(), expected)
        case Right(ok) =>
          fail(s"Expected error. Obtained $ok")
      }
    }
  }

  checkError(
    "--in" :: tmp.toString :: Nil,
    s"File $tmp does not exist."
  )

  checkOk(
    "--site.VERSION" :: "1.0" :: Nil,
    _.site == Map("VERSION" -> "1.0")
  )

}
