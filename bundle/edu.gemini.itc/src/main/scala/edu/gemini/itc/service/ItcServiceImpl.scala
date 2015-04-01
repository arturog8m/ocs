package edu.gemini.itc.service

import edu.gemini.itc.gmos.GmosRecipe
import edu.gemini.itc.operation.ImagingS2NMethodACalculation
import edu.gemini.itc.shared._

/**
 * The ITC service implementation.
 * A very first, very crude approximation of a future implementation... to be continued...
 * For now only GMOS imaging is supported, trying to execute this code with anything else will fail horribly.
 */
class ItcServiceImpl extends ItcService {

  import ItcService._

  def calculate(source: SourceDefinition, obs: ObservationDetails, cond: ObservingConditions, tele: TelescopeDetails, ins: InstrumentDetails): Result = try {
    ins match {
      case i: GmosParameters  => calculateGmos(source, obs, cond, tele, i)
      case _                  => throw new NotImplementedError // TODO: no other instruments are implemented yet
    }
  } catch {
    // TODO: for now in most cases where a validation problem should be reported to the user the ITC code throws an exception instead
    case e: Throwable =>
      ItcResult.forException(e)
  }

  private def calculateGmos(source: SourceDefinition, obs: ObservationDetails, cond: ObservingConditions, tele: TelescopeDetails, ins: GmosParameters): Result = {
    val recipe = new GmosRecipe(source, obs, cond, ins, tele)
    // TODO: we can simplify this once all recipes have a calculate method (instead of writeOutput())
    val results: Array[ItcCalcResult] = obs.getMethod match {
      case m: Imaging =>
          // Repack the result in an immutable and simplified Scala case class
          // (We don't want to leak out any of the internal ITC craziness here and it is also a good way
          // to keep the service independent from the actual implementation.)
          recipe.calculateImaging().map {
            case r: ImagingResult => r.is2nCalc match {
              case i: ImagingS2NMethodACalculation  => ItcImagingResult(i.singleSNRatio(), i.totalSNRatio(), r.peakPixelCount)
              case _                                => throw new NotImplementedError()
            }
          }

      case s: Spectroscopy =>
        throw new NotImplementedError()
    }
    ItcResult.forCcds(results)
  }

}
