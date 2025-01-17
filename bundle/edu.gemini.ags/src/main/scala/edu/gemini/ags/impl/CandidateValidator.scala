package edu.gemini.ags.impl

import edu.gemini.ags.api.AgsMagnitude.MagnitudeTable
import edu.gemini.spModel.core.Coordinates
import edu.gemini.spModel.core.SiderealTarget
import edu.gemini.spModel.guide.GuideStarValidation
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.target.SPTarget
import edu.gemini.ags.api.AgsMagnitude
import edu.gemini.catalog.api.MagnitudeConstraints
import edu.gemini.pot.ModelConverters._
import edu.gemini.spModel.target.system.HmsDegTarget
import edu.gemini.shared.util.immutable.ScalaConverters._

import scalaz._
import Scalaz._

/**
 * Math on a list of candidates with a given set of constraints.  The idea is
 * that one set of candidates and constraints can be applied to differing
 * observation contexts (different position angles, guide speeds, etc.)
 */
protected case class CandidateValidator(params: SingleProbeStrategyParams, mt: MagnitudeTable, candidates: List[SiderealTarget]) {
  /**
   * Produces a predicate for testing whether a candidate is valid in an
   * established context. Returns constant `false` if base coordinates are unknown.
   */
  private def isValid(ctx: ObsContext): (SiderealTarget) => Boolean =
    ctx.getBaseCoordinates.asScalaOpt.fold((_: SiderealTarget) => false) { base =>
    val magLimits:Option[MagnitudeConstraints] = params.magnitudeCalc(ctx, mt).flatMap(AgsMagnitude.autoSearchConstraints(_, ctx.getConditions))

    (st: SiderealTarget) => {
      // Do not use any candidates that are too close to science target / base
      // position (i.e. don't use science target as guide star)
      def farEnough =
        params.minDistance.forall { min =>
          val soCoords = st.coordinates
          val diff = Coordinates.difference(base.toNewModel, soCoords)
          diff.distance >= min
        }

      // Only keep candidates that fall within the magnitude limits.
      def brightnessOk = (magLimits |@| params.referenceMagnitude(st))(_ contains _) | false

      // Only keep those that are in range of the guide probe.
      def inProbeRange = params.validator(ctx).validate(new SPTarget(HmsDegTarget.fromSkyObject(st.toOldModel)), ctx) == GuideStarValidation.VALID

      farEnough && brightnessOk && inProbeRange
    }
  }

  def filter(ctx: ObsContext): List[SiderealTarget]   = candidates.filter(isValid(ctx))

  def exists(ctx: ObsContext): Boolean                = candidates.exists(isValid(ctx))

  def select(ctx: ObsContext): Option[SiderealTarget] = params.brightest(filter(ctx))(identity)
}
