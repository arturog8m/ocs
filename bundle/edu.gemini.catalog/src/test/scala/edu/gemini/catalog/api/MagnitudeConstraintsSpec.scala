package edu.gemini.catalog.api

import edu.gemini.spModel.core._
import org.specs2.mutable.SpecificationWithJUnit

class MagnitudeConstraintsSpec extends SpecificationWithJUnit {

  "Magnitude Constraints" should {
    "filter targets on band and faintness" in {
      val ml = MagnitudeConstraints(RBandsList, FaintnessConstraint(10.0), None)

      ml.filter(SiderealTarget.empty.copy(magnitudes = Nil)) should beFalse
      val mag1 = new Magnitude(4.999, MagnitudeBand.R)

      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag1))) should beTrue

      val mag2 = new Magnitude(10.001, MagnitudeBand.R)
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag2))) should beFalse

      val mag3 = new Magnitude(4.999, MagnitudeBand.K)
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag3))) should beFalse

      // Case where there are more than one magnitude band
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag2, mag3))) should beFalse
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag1, mag3))) should beTrue
    }
    "filter targets on band, faintness and saturation" in {
      val ml = MagnitudeConstraints(RBandsList, FaintnessConstraint(10.0), Some(SaturationConstraint(5)))

      ml.filter(SiderealTarget.empty.copy(magnitudes = Nil)) should beFalse
      val mag1 = new Magnitude(4.999, MagnitudeBand.R)

      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag1))) should beFalse

      val mag2 = new Magnitude(5.001, MagnitudeBand.R)
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag2))) should beTrue

      val mag3 = new Magnitude(4.999, MagnitudeBand.K)
      ml.filter(SiderealTarget.empty.copy(magnitudes = List(mag3))) should beFalse
    }
    "support the union operation" in {
      val m1 = MagnitudeConstraints(RBandsList, FaintnessConstraint(10.0), None)
      val m2 = MagnitudeConstraints(SingleBand(MagnitudeBand.J), FaintnessConstraint(10.0), None)
      // Different band
      m1.union(m2) should beNone

      val m3 = MagnitudeConstraints(RBandsList, FaintnessConstraint(5.0), None)
      m1.union(m3) should beSome(MagnitudeConstraints(RBandsList, FaintnessConstraint(10.0), None))

      val m4 = MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), None)
      m1.union(m4) should beSome(MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), None))

      val m5 = MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), Some(SaturationConstraint(10.0)))
      m1.union(m5) should beSome(MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), None))

      val m6 = MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), Some(SaturationConstraint(15.0)))
      m5.union(m6) should beSome(MagnitudeConstraints(RBandsList, FaintnessConstraint(15.0), Some(SaturationConstraint(10.0))))
    }
    "pick the first available R-band" in {
      val bs = RBandsList

      val t1 = SiderealTarget.empty.copy(magnitudes = Nil)
      bs.extract(t1) should beNone
      val t2 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(0.0, MagnitudeBand.J)))
      bs.extract(t2) should beNone
      val t3 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand._r)))
      bs.extract(t3) should beSome(new Magnitude(1.0, MagnitudeBand._r))
      val t4 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.R)))
      bs.extract(t4) should beSome(new Magnitude(1.0, MagnitudeBand.R))
      val t5 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.UC)))
      bs.extract(t5) should beSome(new Magnitude(1.0, MagnitudeBand.UC))
      val t6 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.UC), new Magnitude(1.0, MagnitudeBand._r)))
      bs.extract(t6) should beSome(new Magnitude(1.0, MagnitudeBand._r))
      val t7 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.R), new Magnitude(1.0, MagnitudeBand._r)))
      bs.extract(t7) should beSome(new Magnitude(1.0, MagnitudeBand._r))
      val t8 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.R), new Magnitude(1.0, MagnitudeBand.UC)))
      bs.extract(t8) should beSome(new Magnitude(1.0, MagnitudeBand.R))
      val t9 = SiderealTarget.empty.copy(magnitudes = List(new Magnitude(1.0, MagnitudeBand.R), new Magnitude(1.0, MagnitudeBand.UC), new Magnitude(1.0, MagnitudeBand._r) ))
      bs.extract(t9) should beSome(new Magnitude(1.0, MagnitudeBand._r))
    }
  }
}