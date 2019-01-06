package mdoc.internal.markdown

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import scala.meta._
import scala.meta.inputs.Position
import Instrumenter.position
import mdoc.internal.markdown.Instrumenter.Binders

class Instrumenter(sections: List[SectionInput]) {
  def instrument(): String = {
    printAsScript()
    out.toString
  }
  private val out = new ByteArrayOutputStream()
  private val sb = new PrintStream(out)
  private var lastNest = ""
  val gensym = new Gensym()
  private def printAsScript(): Unit = {
    sections.zipWithIndex.foreach {
      case (section, i) =>
        if (section.mod.isReset) {
          val nextApp = gensym.fresh("App")
          sb.print(s"$nextApp\n}\nobject $nextApp {\n")
        }
        if (section.mod.isNest) {
          lastNest = gensym.fresh("nest")
          sb.print(s"$lastNest\nobject $lastNest {\n")
        }
        sb.println("\n$doc.startSection();")
        if (section.mod.isFail) {
          sb.println(s"$$doc.startStatement(${position(section.source.pos)});")
          val out = new FailInstrumenter(sections, i).instrument()
          val literal = Instrumenter.stringLiteral(out)
          val binder = gensym.fresh("res")
          sb.append("val ")
            .append(binder)
            .append(" = _root_.mdoc.internal.document.FailSection(")
            .append(literal)
            .append(", ")
            .append(position(section.source.pos))
            .append(");")
          printBinder(binder, section.source.pos)
          sb.println("\n$doc.endStatement();")
        } else {
          section.source.stats.foreach { stat =>
            sb.println(s"$$doc.startStatement(${position(stat.pos)});")
            printStatement(stat, section.mod, sb)
            sb.println("\n$doc.endStatement();")
          }
        }
        sb.println("$doc.endSection();")
        if (section.mod.isNest) {
          sb.print(s"\n}\nimport $lastNest._;\n")
        }
    }
  }

  private def printBinder(name: String, pos: Position): Unit = {
    sb.print(s"; $$doc.binder($name, ${position(pos)})")
  }
  private def printStatement(stat: Stat, m: Modifier, sb: PrintStream): Unit = {
    if (m.isDefault || m.isPassthrough || m.isInvisible ||
      m.isSilent || m.isReset || m.isPost || m.isNest) {
      val binders = stat match {
        case Binders(names) =>
          names.map(name => name -> name.pos)
        case _ =>
          val fresh = gensym.fresh("res")
          sb.print(s"val $fresh = ")
          List(Name(fresh) -> stat.pos)
      }
      sb.print(stat.pos.text)
      binders.foreach {
        case (name, pos) =>
          printBinder(name.syntax, pos)
      }
    } else if (m.isCrash) {
      sb.append("$doc.crash(")
        .append(position(stat.pos))
        .append(") {\n")
        .append(stat.pos.text)
        .append("\n}")
    } else {
      throw new IllegalArgumentException(m.toString)
    }
  }
}
object Instrumenter {
  def instrument(sections: List[SectionInput]): String = {
    val body = new Instrumenter(sections).instrument()
    wrapBody(body)
  }

  def position(pos: Position): String = {
    s"${pos.startLine}, ${pos.startColumn}, ${pos.endLine}, ${pos.endColumn}"
  }

  def stringLiteral(string: String): String = {
    import scala.meta.internal.prettyprinters._
    enquote(string, DoubleQuotes)
  }

  def wrapBody(body: String): String = {
    val wrapped = new StringBuilder()
      .append("package repl\n")
      .append("object Session extends _root_.mdoc.internal.document.DocumentBuilder {\n")
      .append("  def app(): Unit = {val _ = App}\n")
      .append("  object App {\n")
      .append(body)
      .append("  }\n")
      .append("}\n")
      .toString()
    wrapped
  }
  object Binders {
    def binders(pat: Pat): List[Name] =
      pat.collect { case m: Member => m.name }
    def unapply(tree: Tree): Option[List[Name]] = tree match {
      case Defn.Val(_, pats, _, _) => Some(pats.flatMap(binders))
      case Defn.Var(_, pats, _, _) => Some(pats.flatMap(binders))
      case _: Defn => Some(Nil)
      case _: Import => Some(Nil)
      case _ => None
    }
  }

}
