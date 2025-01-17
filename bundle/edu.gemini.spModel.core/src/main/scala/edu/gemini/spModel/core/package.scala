package edu.gemini.spModel

import scala.util.control.NonFatal
import scalaz._, Scalaz._
import scala.collection.immutable.NumericRange

package object core {

  /** Turns any non-fatal exceptions in the given block into lefts.
    * This is the same as scalaz `\/.fromTryCatchNonFatal`, so we should
    * switch to that at some point after we update to a newer version.
    */
  def catchingNonFatal[T](a: => T): Throwable \/ T =
    try {
      \/-(a)
    } catch {
      case NonFatal(t) => -\/(t)
    }

  type RA = RightAscension
  val  RA = RightAscension

  type Dec = Declination
  val  Dec = Declination

  type Ephemeris = Long ==>> Coordinates

  /** Operations for maps of types that we can interpolate.  We use these for Ephemerides. */
  implicit class MoreMapOps[K, V](m: K ==>> V)(implicit O: Order[K], I: Interpolate[K,V]) {

    /** Perform an exact or interpolated lookup. */
    def iLookup(k: K): Option[V] =
      m.lookup(k) match {
        case Some(v) => Some(v)
        case None    =>
          val (lt, gt) = m.split(k)
          for {
            a <- lt.findMax
            b <- gt.findMin
            c <- I.interpolate(a, b, k)
          } yield c
      }

    /** Construct an exact or interpolated slice. */
    def iSlice(lo: K, hi: K): Option[K ==>> V] =
      ^(iLookup(lo), iLookup(hi)) { (lov, hiv) =>
        m.trim(O(_, lo), O(_, hi)) + (lo -> lov) + (hi -> hiv)
      }

    /** Construct a table of (K, V) values on the given interval. */
    def iTable(lo: K, hi: K, step: K)(implicit ev: Integral[K]): Option[List[(K, V)]] =
      NumericRange.inclusive(lo, hi, step).toList.traverse(k => iLookup(k).strengthL(k))

  }

}

