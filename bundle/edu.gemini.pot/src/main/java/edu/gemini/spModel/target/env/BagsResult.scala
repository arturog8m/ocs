package edu.gemini.spModel.target.env

import edu.gemini.shared.util.immutable.{Option => GOption}
import edu.gemini.spModel.pio.{Pio, PioFactory, ParamSet}
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.rich.shared.immutable._

import scala.collection.JavaConverters._

sealed trait BagsResult extends Cloneable {
  val id: String
  val target: Option[SPTarget] = None
  def targetAsJava: GOption[SPTarget] = target.asGeminiOpt
  override def clone: BagsResult = this

  def getParamSet(factory: PioFactory): ParamSet = {
    val paramSet = factory.createParamSet(BagsResult.BagsResultParamSetName)
    Pio.addParam(factory, paramSet, BagsResult.BagsResultParamIdName, id)
    target.foreach { t =>
      val bagsParamSet = factory.createParamSet(BagsResult.BagsTargetParamSetName)
      bagsParamSet.addParamSet(t.getParamSet(factory))
      paramSet.addParamSet(bagsParamSet)
    }
    paramSet
  }

  override def toString = id
}

object BagsResult {
  case object NoSearchPerformed extends BagsResult {
    override val id = "NoSearchPerformed"
  }

  case object NoTargetFound extends BagsResult {
    override val id = "NoTargetFound"
  }

  case class  WithTarget(tgt: SPTarget) extends BagsResult {
    override val id = WithTarget.id
    override val target = Some(tgt)
    override def clone = WithTarget(tgt.clone())
    override def toString = s"$id(${target.toString})"
  }
  object WithTarget {
    val id = "WithTarget"
  }


  val BagsResultParamSetName:  String = "bagsResult"
  val BagsResultParamIdName:   String = "bagsResultId"
  val BagsTargetParamSetName:  String = "bagsTarget"

  def fromParamSet(parent: ParamSet): BagsResult = {
    Option(parent.getParamSet(BagsResultParamSetName)).flatMap { ps =>
      // We get the param ID and then use that to construct the object.
      val paramId = Option(ps.getParam(BagsResultParamIdName)).map(_.getValue)
      val target = Option(ps.getParamSet(BagsTargetParamSetName)).flatMap(_.getParamSets.asScala.headOption).map(SPTarget.fromParamSet)

      paramId.collect {
        case NoTargetFound.id => NoTargetFound
        case WithTarget.id if target.isDefined => WithTarget(target.get)
      }
    }.getOrElse(NoSearchPerformed)
  }
}
