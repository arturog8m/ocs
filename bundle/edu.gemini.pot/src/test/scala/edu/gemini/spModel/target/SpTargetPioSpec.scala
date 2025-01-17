package edu.gemini.spModel.target

import edu.gemini.spModel.core.{SpectralDistribution, SpatialProfile, Redshift, Arbitraries}
import edu.gemini.spModel.pio.{Pio, ParamSet}
import edu.gemini.spModel.pio.xml.PioXmlFactory
import edu.gemini.spModel.target.system.{ConicTarget, HmsDegTarget, ITarget}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import squants.motion.KilometersPerSecond

import edu.gemini.shared.util.immutable.{ None => JNone }
import edu.gemini.shared.util.immutable.ScalaConverters._

/** Tests Pio input/output operations for SpTargets.
  * Currently this only tests that the source profile and distribution are stored and retrieved.
  * Feel free to expand on this; however, I guess this will all become obsolete once we switch to the new target model.
  */
object SpTargetPioSpec extends Specification with ScalaCheck with Arbitraries {

  {

    "SPTargetPio" should {
      "store source profile and distribution" !
        prop { (sd: Option[SpectralDistribution], sp: Option[SpatialProfile]) =>

          val factory = new PioXmlFactory()

          val spt = new SPTarget(10, 10)
          spt.setSpatialProfile(sp)
          spt.setSpectralDistribution(sd)

          val pset = SPTargetPio.getParamSet(spt, factory)
          val spt2 = SPTargetPio.fromParamSet(pset)

          assert(spt.getTarget.getSpatialProfile === spt2.getTarget.getSpatialProfile)
          assert(spt.getTarget.getSpectralDistribution === spt2.getTarget.getSpectralDistribution)
        }
      "store redshift" !
        prop { z: Redshift =>

          val factory = new PioXmlFactory()
          val t = new HmsDegTarget

          t.setRedshift(z)
          val spt = new SPTarget(t)

          val pset = SPTargetPio.getParamSet(spt, factory)
          val spt2 = SPTargetPio.fromParamSet(pset)

          (spt.getTarget, spt2.getTarget) match {
            case (t1: HmsDegTarget, t2: HmsDegTarget) => assert(t1.getRedshift === t2.getRedshift)
            case _                                    => failure("Cannot happen")
          }
        }
    }

  }

  val ParamRa  = "c1"
  val ParamDec = "c2"

  "SPTargetPio" should {
    def newTargetParamSet(t: ITarget): ParamSet = {
      val fact = new PioXmlFactory
      val spt  = new SPTarget(t)
      SPTargetPio.getParamSet(spt, fact)
    }

    def expect(ps: ParamSet, era: Double, edec: Double): Unit = {
      val spt = SPTargetPio.fromParamSet(ps)
      val ra  = spt.getTarget.getRaDegrees(JNone.instance[java.lang.Long]).asScalaOpt.map(_.doubleValue).get
      val dec = spt.getTarget.getDecDegrees(JNone.instance[java.lang.Long]).asScalaOpt.map(_.doubleValue).get

      ra  must beCloseTo(era,  0.000001)
      dec must beCloseTo(edec, 0.000001)
    }

    def fromDegrees(t: ITarget): Unit = {
      val ps   = newTargetParamSet(t)
      val pRa  = ps.getParam(ParamRa)
      val pDec = ps.getParam(ParamDec)

      pRa.setValue("180.0")
      pDec.setValue("10.0")

      expect(ps, 180.0, 10.0)
    }

    def fromHmsDms(t: ITarget): Unit = {
      val ps   = newTargetParamSet(t)
      val pRa  = ps.getParam(ParamRa)
      val pDec = ps.getParam(ParamDec)

      pRa.setValue("180.0")
      pDec.setValue("10.0")

      expect(ps, 180.0, 10.0)
    }

    "read RA and Dec specified as degrees for sidereal targets" in {
      fromDegrees(new HmsDegTarget)
    }

    "read RA and Dec specified as HMS/DMS for sidereal targets" in {
      fromHmsDms(new HmsDegTarget)
    }

    "read RA and Dec specified as degrees for non-sidereal targets" in {
      fromDegrees(new ConicTarget)
    }

    "read RA and Dec specified as HMS/DMS for non-sidereal targets" in {
      fromHmsDms(new ConicTarget)
    }

    "convert RV values to redshift" in {
      val fact = new PioXmlFactory
      val ps = SPTargetPio.getParamSet(new SPTarget(new HmsDegTarget), fact)
      // Simulate an old program without redshift but containing radial velocity
      Pio.addParam(fact, ps, "rv", "295000")
      ps.removeChild("z")
      val spt = SPTargetPio.fromParamSet(ps)
      spt.getTarget.asInstanceOf[HmsDegTarget].getRedshift must beEqualTo(Redshift.fromRadialVelocity(KilometersPerSecond(295000)))
    }
  }

}
