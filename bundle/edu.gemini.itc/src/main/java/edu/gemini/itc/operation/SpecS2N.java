package edu.gemini.itc.operation;

import edu.gemini.itc.base.VisitableSampledSpectrum;

import java.util.stream.IntStream;

/**
 * A set of common values that are accessed by service users through the SpectroscopyResult object.
 */
public interface SpecS2N {

    double getImageQuality();
    double getSpecNpix();
    VisitableSampledSpectrum getSignalSpectrum();
    VisitableSampledSpectrum getBackgroundSpectrum();
    VisitableSampledSpectrum getExpS2NSpectrum();
    VisitableSampledSpectrum getFinalS2NSpectrum();

    default double getPeakPixelCount() {
        final double[] sig = getSignalSpectrum().getValues();
        final double[] bck = getBackgroundSpectrum().getValues();

        // This is a set of conditions that need to hold true for the peak pixel calculation.
        // I am adding these assertions to avoid problems with future refactorings.
        if (getSignalSpectrum().getStart() != getBackgroundSpectrum().getStart()) throw new Error();
        if (getSignalSpectrum().getEnd()   != getBackgroundSpectrum().getEnd())   throw new Error();
        if (sig.length != bck.length)                                             throw new Error();

        // Calculate the peak pixel
        return IntStream.range(0, sig.length).mapToDouble(i -> bck[i]*bck[i] + sig[i]).max().getAsDouble();
    }

}
