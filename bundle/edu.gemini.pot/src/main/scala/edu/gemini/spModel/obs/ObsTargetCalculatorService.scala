package edu.gemini.spModel.obs

import edu.gemini.pot.sp.{SPComponentType, ISPObservation}
import edu.gemini.spModel.core.Site
import edu.gemini.spModel.obs.plannedtime.PlannedTimeCalculator
import edu.gemini.spModel.rich.pot.sp.obsWrapper
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.target.obsComp.TargetObsComp
import edu.gemini.util.skycalc.SiderealTarget
import edu.gemini.util.skycalc.calc.{Interval, TargetCalculator}

import jsky.coords.WorldCoords
import edu.gemini.skycalc.{TimeUtils, Coordinates}

/**
 * Created with IntelliJ IDEA.
 * User: sraaphor
 * Date: 3/23/14
 * Time: 1:44 AM
 * To change this template use File | Settings | File Templates.
 */
object ObsTargetCalculatorService {
  private def create(obs: ISPObservation): Option[TargetCalculator] = {
    // First, determine the Site at which the instrument is located based on
    // the instrument used, e.g., GMOS-N or S.  If no instrument or a
    // multi-site instrument, None.
    def site = obs.sites.toList match {
      case List(s) => Some(s)
      case _       => None
    }

    def block = obs.spObservation.flatMap(_.getSchedulingBlock.asScalaOpt)

    // Now, based on the SchedulingBlock determine if a TargetCalc should be created.
    // Get the TargetEnvironment if it exists, and from there, extract the RA and Dec.
    def coords = obs.findObsComponentByType(SPComponentType.TELESCOPE_TARGETENV).map {
      _.getDataObject.asInstanceOf[TargetObsComp].getTargetEnvironment.getBase.getTarget.getSkycalcCoordinates
    }

    def calc(s: Site, b: SchedulingBlock, c: Coordinates): TargetCalculator = {
      val st = SiderealTarget(new WorldCoords(c.getRaDeg, c.getDecDeg))
      val plannedTime = PlannedTimeCalculator.instance.calc(obs)

      // Andy says:
      // duration is equivalent to science time, if specific explicitly
      // science time is plannedTime.totalTime - plannedTime.setup.time
      // Ideally, if you hover over duration box in GUI, should say acquisition + science time OR not.

      // Since we need start < end explicitly, if the duration is 0, we cannot use it.
      val duration = if (b.duration > 0) b.duration else plannedTime.totalTime
      val end      = b.start + duration

      // If the duration is going to be smaller than the default step size of 30 seconds used by the
      // target calc, we will have divide by 0 issues, so take this into account.
      val stepSize = if (duration >= TimeUtils.seconds(30)) TimeUtils.seconds(30) else duration
      TargetCalculator(s, st, Interval(b.start, end), stepSize)
    }

    for {
      s <- site
      b <- block
      c <- coords
    } yield calc(s, b, c)
  }

  private def lookupOrCreate(obs: ISPObservation): Option[TargetCalculator] =
    (for {
      tcOpt <- Option(SPObsCache.getTargetCalculator(obs))
      tc    <- tcOpt.asScalaOpt
    } yield tc).orElse(create(obs))

  def targetCalculation(obs: ISPObservation): Option[TargetCalculator] = {
    val res = lookupOrCreate(obs)
    SPObsCache.setTargetCalculator(obs, res.asGeminiOpt)
    res
  }

  def targetCalculationForJava(obs: ISPObservation): edu.gemini.shared.util.immutable.Option[TargetCalculator] = {
    targetCalculation(obs).asGeminiOpt
  }
}
