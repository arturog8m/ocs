package edu.gemini.itc.base

import java.util

import edu.gemini.itc.shared._

import scala.collection.JavaConversions._

sealed trait Recipe

trait ImagingRecipe extends Recipe {
  def calculateImaging(): ImagingResult
  def serviceResult(r: ImagingResult): ItcImagingResult
}

trait ImagingArrayRecipe extends Recipe {
  def calculateImaging(): Array[ImagingResult]
  def serviceResult(r: Array[ImagingResult]): ItcImagingResult
}


trait SpectroscopyRecipe extends Recipe {
  def calculateSpectroscopy(): SpectroscopyResult
  def serviceResult(r: SpectroscopyResult): ItcSpectroscopyResult
}


trait SpectroscopyArrayRecipe extends Recipe {
  def calculateSpectroscopy(): Array[SpectroscopyResult]
  def serviceResult(r: Array[SpectroscopyResult]): ItcSpectroscopyResult

}

object Recipe {

  // =============
  // GENERIC CHART CREATION
  // Utility functions that create generic signal and signal to noise charts for several instruments.

  // create signal/background chart
  def createSignalChart(result: SpectroscopyResult, index: Int): SpcChartData = {
    createSignalChart(result, "Signal & SQRT(Background)\nSummed in an aperture of " + result.specS2N(index).getSpecNpix + " pix diameter", index)
  }

  def createSignalChart(result: SpectroscopyResult, title: String, index: Int): SpcChartData = {
    val data: java.util.List[SpcSeriesData] = new java.util.ArrayList[SpcSeriesData]()
    data.add(new SpcSeriesData(SignalData,     "Signal",               result.specS2N(index).getSignalSpectrum.getData))
    data.add(new SpcSeriesData(BackgroundData, "SQRT(Background)",     result.specS2N(index).getBackgroundSpectrum.getData))
    new SpcChartData(SignalChart, title, ChartAxis("Wavelength (nm)"), ChartAxis("e- per exposure per spectral pixel"), data.toList)
  }

  def createS2NChart(result: SpectroscopyResult): SpcChartData = {
    createS2NChart(result, 0)
  }

  def createS2NChart(result: SpectroscopyResult, index: Int): SpcChartData = {
    createS2NChart(result, "Intermediate Single Exp and Final S/N", index)
  }

  def createS2NChart(result: SpectroscopyResult, title: String, index: Int): SpcChartData = {
    val data: java.util.List[SpcSeriesData] = new util.ArrayList[SpcSeriesData]
    data.add(new SpcSeriesData(SingleS2NData, "Single Exp S/N", result.specS2N(index).getExpS2NSpectrum.getData))
    data.add(new SpcSeriesData(FinalS2NData,  "Final S/N  ",    result.specS2N(index).getFinalS2NSpectrum.getData))
    new SpcChartData(S2NChart, title, ChartAxis("Wavelength (nm)"), ChartAxis("Signal / Noise per spectral pixel"), data.toList)
  }

  // === Imaging

  def toCcdData(r: ImagingResult): ItcCcd =
    ItcCcd(r.is2nCalc.singleSNRatio(), r.is2nCalc.totalSNRatio(), r.peakPixelCount, r.instrument.wellDepth, r.instrument.gain, Warning.collectWarnings(r))

  def serviceResult(r: ImagingResult): ItcImagingResult =
    ItcImagingResult(List(toCcdData(r)))

  def serviceResult(r: Array[ImagingResult]): ItcImagingResult =
    ItcImagingResult(r.map(toCcdData).toList)

  // === Spectroscopy

  /** Collects the relevant information from the internal result in a simplified data object and collects some additional
    * information from the data series like e.g. the max signal to noise. For each individual CCD ITC basically
    * does a full analysis and gives us a separate result. */
  def toCcdData(r: SpectroscopyResult, charts: List[SpcChartData]): ItcCcd = {
    val s2nChart: SpcChartData = charts.find(_.chartType == S2NChart).get
    val singleSNRatioVals: List[Double] = s2nChart.allSeries(SingleS2NData).map(_.yValues.max)
    val singleSNRatio: Double           = if (singleSNRatioVals.isEmpty) 0 else singleSNRatioVals.max
    val totalSNRatioVals: List[Double]  = s2nChart.allSeries(FinalS2NData).map(_.yValues.max)
    val totalSNRatio: Double            = if (totalSNRatioVals.isEmpty) 0 else totalSNRatioVals.max
    ItcCcd(singleSNRatio, totalSNRatio, r.peakPixelCount, r.instrument.wellDepth, r.instrument.gain, Warning.collectWarnings(r))
  }

  // === Java helpers

  // One result (CCD) and a simple set of charts, this covers most cases.
  def serviceResult(r: SpectroscopyResult, charts: java.util.List[SpcChartData]): ItcSpectroscopyResult =
    ItcSpectroscopyResult(List(toCcdData(r, charts.toList)), List(SpcChartGroup(charts.toList)))

  // One result (CCD) and a set of groups of charts, this covers NIFS (1 CCD and separate groups for IFU cases).
  def serviceGroupedResult(r: SpectroscopyResult, charts: java.util.List[java.util.List[SpcChartData]]): ItcSpectroscopyResult =
    ItcSpectroscopyResult(List(toCcdData(r, charts.toList.flatten)), charts.toList.map(l => SpcChartGroup(l.toList)))

  // A set of results and a set of groups of charts, this covers GMOS (3 CCDs and potentially separate groups
  // for IFU cases, if IFU is activated).
  def serviceGroupedResult(rs: Array[SpectroscopyResult], charts: java.util.List[java.util.List[SpcChartData]]): ItcSpectroscopyResult =
    ItcSpectroscopyResult(rs.map(r => toCcdData(r, charts.toList.flatten)).toList, charts.toList.map(l => SpcChartGroup(l.toList)))

}

