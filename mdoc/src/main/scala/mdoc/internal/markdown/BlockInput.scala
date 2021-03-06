package mdoc.internal.markdown

import com.vladsch.flexmark.ast.FencedCodeBlock
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import mdoc.internal.cli.Context
import mdoc.internal.markdown.Modifier.Str
import mdoc.internal.markdown.Modifier.Default
import mdoc.internal.markdown.Modifier.Post

case class StringBlockInput(block: FencedCodeBlock, input: Input, mod: Str)
case class ScalaBlockInput(block: FencedCodeBlock, input: Input, mod: Modifier)

class BlockInput(ctx: Context, baseInput: Input) {
  def getModifier(block: FencedCodeBlock): Option[Modifier] = {
    val string = block.getInfo.toString
    if (!string.startsWith("scala mdoc")) None
    else {
      if (!string.contains(':')) Some(Modifier.Default())
      else {
        val mode = string.stripPrefix("scala mdoc:")
        Modifier(mode)
          .orElse {
            val (name, info) = mode.split(":", 2) match {
              case Array(a) => (a, "")
              case Array(a, b) => (a, b)
            }
            ctx.settings.stringModifiers
              .collectFirst[Modifier] {
                case mod if mod.name == name =>
                  Str(mod, info)
              }
              .orElse {
                ctx.settings.postModifiers.collectFirst {
                  case mod if mod.name == name =>
                    Post(mod, info)
                }
              }
          }
          .orElse {
            invalid(block, s"Invalid mode '$mode'")
            None
          }
      }
    }
  }

  private def invalid(block: FencedCodeBlock, message: String): Unit = {
    val offset = "scala mdoc:".length
    val start = block.getInfo.getStartOffset + offset
    val end = block.getInfo.getEndOffset
    val pos = Position.Range(baseInput, start, end)
    ctx.reporter.error(pos, message)
  }
  private def invalidCombination(block: FencedCodeBlock, mod1: String, mod2: String): Boolean = {
    invalid(block, s"invalid combination of modifiers '$mod1' and '$mod2' are ")
    false
  }

  private def isValid(block: FencedCodeBlock, mod: Modifier): Boolean = {
    if (mod.isFail && mod.isCrash) {
      invalidCombination(block, "crash", "fail")
    } else if (mod.isSilent && mod.isInvisible) {
      invalidCombination(block, "silent", "invisible")
    } else {
      true
    }
  }
  def unapply(block: FencedCodeBlock): Option[ScalaBlockInput] = {
    getModifier(block) match {
      case Some(mod) =>
        if (isValid(block, mod)) {
          val child = block.getFirstChild
          val start = child.getStartOffset
          val end = child.getEndOffset
          val isNewline = baseInput.chars(end - 1) == '\n'
          val cutoff = if (isNewline) 1 else 0
          val input = Input.Slice(baseInput, start, end - cutoff)
          Some(ScalaBlockInput(block, input, mod))
        } else {
          None
        }
      case _ => None
    }
  }
}
