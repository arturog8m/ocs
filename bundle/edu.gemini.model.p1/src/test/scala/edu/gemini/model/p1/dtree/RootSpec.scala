package edu.gemini.model.p1.dtree

import org.specs2.mutable.SpecificationWithJUnit
import edu.gemini.model.p1.immutable.{Semester, Instrument}
import edu.gemini.model.p1.immutable.SemesterOption._

class RootSpec extends SpecificationWithJUnit {

  "The Root Spec" should {
    "include Gsaoi" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain(Instrument.Gsaoi)
    }
    "include Texes" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain (Instrument.Texes)
    }
    "include Speckles" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain (Instrument.Dssi)
    }
    "include Visitor" in {
      val root = new Root(Semester(2016, B))
      root.choices must contain(Instrument.Visitor)
    }
    "include Gpi" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain(Instrument.Gpi)
    }
    "includes Graces" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain(Instrument.Graces)
    }
    "includes Phoenix" in {
      val root = new Root(Semester(2016, A))
      root.choices must contain(Instrument.Phoenix)
    }
    "Michelle has been removed in 2016A" in {
      val root = new Root(Semester(2016, A))
      root.choices must not contain Instrument.Michelle
    }
    "T-ReCS has been removed in 2016A" in {
      val root = new Root(Semester(2016, A))
      root.choices must not contain Instrument.Trecs
    }
  }

}
