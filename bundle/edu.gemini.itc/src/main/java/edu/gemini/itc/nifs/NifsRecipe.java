package edu.gemini.itc.nifs;

import edu.gemini.itc.altair.Altair;
import edu.gemini.itc.base.*;
import edu.gemini.itc.operation.ImageQualityCalculatable;
import edu.gemini.itc.operation.ImageQualityCalculationFactory;
import edu.gemini.itc.operation.SpecS2N;
import edu.gemini.itc.operation.SpecS2NLargeSlitVisitor;
import edu.gemini.itc.shared.*;
import edu.gemini.spModel.core.GaussianSource;
import edu.gemini.spModel.core.PointSource$;
import edu.gemini.spModel.core.UniformSource$;
import scala.Option;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class performs the calculations for Nifs
 * used for imaging.
 */
public final class NifsRecipe implements SpectroscopyRecipe {

    private final ItcParameters p;
    private final Nifs instrument;
    private final SourceDefinition _sdParameters;
    private final ObservationDetails _obsDetailParameters;
    private final ObservingConditions _obsConditionParameters;
    private final NifsParameters _nifsParameters;
    private final TelescopeDetails _telescope;

    /**
     * Constructs a NifsRecipe given the parameters.
     * Useful for testing.
     */
    public NifsRecipe(final ItcParameters p, final NifsParameters instr) {
        this.p                  = p;
        instrument              = new Nifs(instr, p.observation());
        _sdParameters           = p.source();
        _obsDetailParameters    = p.observation();
        _obsConditionParameters = p.conditions();
        _nifsParameters         = instr;
        _telescope              = p.telescope();

        // some general validations
        Validation.validate(instrument, _obsDetailParameters, _sdParameters);
    }

    /**
     * Performs recipe calculation.
     */
    public ItcSpectroscopyResult serviceResult(final SpectroscopyResult r) {
        final List<List<SpcChartData>> groups = new ArrayList<>();
        // The array specS2N models the different IFUs, for each one we produce a separate output.
        for (int i = 0; i < r.specS2N().length; i++) {
            final List<SpcChartData> charts = new ArrayList<>();
            charts.add(createNifsSignalChart(r, i));
            charts.add(createNifsS2NChart(r, i));
            groups.add(charts);
        }
        return Recipe$.MODULE$.serviceGroupedResult(r, groups);
    }

    public SpectroscopyResult calculateSpectroscopy() {

        // Calculate image quality
        final ImageQualityCalculatable IQcalc = ImageQualityCalculationFactory.getCalculationInstance(_sdParameters, _obsConditionParameters, _telescope, instrument);
        IQcalc.calculate();

        final Option<AOSystem> altair;
        if (_nifsParameters.altair().isDefined()) {
            final Altair ao = new Altair(instrument.getEffectiveWavelength(), _telescope.getTelescopeDiameter(), IQcalc.getImageQuality(), _nifsParameters.altair().get(), 0.0);
            altair = Option.apply((AOSystem) ao);
        } else {
            altair = Option.empty();
        }

        final SEDFactory.SourceResult calcSource = SEDFactory.calculate(instrument, _sdParameters, _obsConditionParameters, _telescope, altair);

        // End of the Spectral energy distribution portion of the ITC.

        // Start of morphology section of ITC
        // Module 1a
        // The purpose of this section is to calculate the fraction of the
        // source flux which is contained within an aperture which we adopt
        // to derive the signal to noise ratio.  There are several cases
        // depending on the source morphology.
        // Define the source morphology
        //
        // inputs: source morphology specification

        final double pixel_size = instrument.getPixelSize();

        //IFU morphology section
        final double im_qual = altair.isDefined() ? altair.get().getAOCorrectedFWHM() : IQcalc.getImageQuality();
        final VisitableMorphology morph, haloMorphology;
        if (_sdParameters.profile() == PointSource$.MODULE$) {
            morph = new AOMorphology(im_qual);
            haloMorphology = new AOMorphology(IQcalc.getImageQuality());
        } else if (_sdParameters.profile() instanceof GaussianSource) {
            morph = new GaussianMorphology(im_qual);
            haloMorphology = new GaussianMorphology(IQcalc.getImageQuality());
        } else if (_sdParameters.profile() == UniformSource$.MODULE$) {
            morph = new USBMorphology();
            haloMorphology = new USBMorphology();
        } else {
            throw new IllegalArgumentException();
        }
        morph.accept(instrument.getIFU().getAperture());

        //for now just a single item from the list
        final List<Double> sf_list = instrument.getIFU().getFractionOfSourceInAperture();  //extract corrected source fraction list

        instrument.getIFU().clearFractionOfSourceInAperture();
        haloMorphology.accept(instrument.getIFU().getAperture());


        final List<Double> halo_sf_list = instrument.getIFU().getFractionOfSourceInAperture();  //extract uncorrected halo source fraction list

        final List<Double> ap_offset_list = instrument.getIFU().getApertureOffsetList();

        // In this version we are bypassing morphology modules 3a-5a.
        // i.e. the output morphology is same as the input morphology.
        // Might implement these modules at a later time.
        double spec_source_frac = 0;
        double halo_spec_source_frac = 0;

        final Iterator<Double> src_frac_it = sf_list.iterator();
        final Iterator<Double> halo_src_frac_it = halo_sf_list.iterator();

        int i = 0;
        final SpecS2N[] specS2Narr = new SpecS2N[_obsDetailParameters.analysisMethod() instanceof IfuSummed ? 1 : sf_list.size()];

        while (src_frac_it.hasNext()) {
            double ap_diam = 1;

            if (_obsDetailParameters.analysisMethod()  instanceof IfuSummed) {
                while (src_frac_it.hasNext()) {
                    spec_source_frac = spec_source_frac + src_frac_it.next();
                    halo_spec_source_frac = halo_spec_source_frac + halo_src_frac_it.next();
                    ap_diam = (ap_offset_list.size() / 2);
                }
            } else {
                spec_source_frac = src_frac_it.next();
                halo_spec_source_frac = halo_src_frac_it.next();
                ap_diam = 1;
            }


            // fp mask is fixed as 0.15
            final double fpMask = 0.15;

            final SpecS2NLargeSlitVisitor specS2N = new SpecS2NLargeSlitVisitor(
                    fpMask,
                    pixel_size,
                    instrument.getSpectralPixelWidth(),
                    instrument.getObservingStart(),
                    instrument.getObservingEnd(),
                    instrument.getGratingDispersion_nm(),
                    instrument.getGratingDispersion_nmppix(),
                    spec_source_frac,
                    im_qual,
                    ap_diam,
                    instrument.getReadNoise(),
                    instrument.getDarkCurrent(),
                    _obsDetailParameters);

            specS2N.setSourceSpectrum(calcSource.sed);
            specS2N.setBackgroundSpectrum(calcSource.sky);
            specS2N.setHaloSpectrum(altair.isDefined() ? calcSource.halo.get() : (VisitableSampledSpectrum) calcSource.sed.clone());
            specS2N.setHaloImageQuality(IQcalc.getImageQuality());
            if (_nifsParameters.altair().isDefined())
                specS2N.setSpecHaloSourceFraction(halo_spec_source_frac);
            else
                specS2N.setSpecHaloSourceFraction(0.0);

            calcSource.sed.accept(specS2N);

            specS2Narr[i++] = specS2N;
        }

        // TODO: no SFCalc and ST for Nifs, introduce specific result type? or optional values? work with null for now
        return new SpectroscopyResult(p, instrument, null, IQcalc, specS2Narr, null, altair);
    }

    // NIFS CHARTS

    private static SpcChartData createNifsSignalChart(final SpectroscopyResult result, final int index) {
        final Nifs instrument = (Nifs) result.instrument();
        final List<Double> ap_offset_list = instrument.getIFU().getApertureOffsetList();
        final String title = instrument.getIFUMethod() instanceof IfuSummed ?
                "Signal and SQRT(Background)\nIFU summed apertures: " +
                        instrument.getIFUNumX() + "x" + instrument.getIFUNumY() +
                        ", " + String.format("%.3f", instrument.getIFUNumX() * IFUComponent.IFU_LEN_X) + "\"x" +
                        String.format("%.3f", instrument.getIFUNumY() * IFUComponent.IFU_LEN_Y) + "\"" :
                "Signal and SQRT(Background)\nIFU element offset: " + String.format("%.3f", ap_offset_list.get(index)) + " arcsec";
        return Recipe$.MODULE$.createSignalChart(result, title, index);
    }

    private static SpcChartData createNifsS2NChart(final SpectroscopyResult result, final int index) {
        final Nifs instrument = (Nifs) result.instrument();
        final List<Double> ap_offset_list = instrument.getIFU().getApertureOffsetList();
        final String title = instrument.getIFUMethod() instanceof IfuSummed ?
                "Intermediate Single Exp and Final S/N\nIFU apertures: " +
                        instrument.getIFUNumX() + "x" + instrument.getIFUNumY() +
                        ", " + String.format("%.3f", instrument.getIFUNumX() * IFUComponent.IFU_LEN_X) + "\"x" +
                        String.format("%.3f", instrument.getIFUNumY() * IFUComponent.IFU_LEN_Y) + "\"" :
                "Intermediate Single Exp and Final S/N\nIFU element offset: " + String.format("%.3f", ap_offset_list.get(index)) + " arcsec";
        return Recipe$.MODULE$.createS2NChart(result, title, index);
    }


}
