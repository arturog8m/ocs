package edu.gemini.ags.gems;

import edu.gemini.ags.gems.mascot.MascotConf;
import edu.gemini.ags.gems.mascot.Star;
import edu.gemini.ags.gems.mascot.Strehl;
import edu.gemini.ags.gems.mascot.MascotCat;
import edu.gemini.ags.gems.mascot.MascotProgress;
import edu.gemini.catalog.api.MagnitudeLimits;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.Coordinates;
import edu.gemini.shared.skyobject.Magnitude;
import edu.gemini.shared.skyobject.SkyObject;
import edu.gemini.shared.skyobject.coords.HmsDegCoordinates;
import edu.gemini.shared.util.immutable.*;
import edu.gemini.spModel.gemini.gems.Canopus;
import edu.gemini.spModel.gemini.gsaoi.Gsaoi;
import edu.gemini.spModel.gemini.gsaoi.GsaoiOdgw;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality;
import edu.gemini.spModel.gems.GemsGuideProbeGroup;
import edu.gemini.spModel.guide.GuideProbe;
import edu.gemini.spModel.guide.ValidatableGuideProbe;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.GuideGroup;
import edu.gemini.spModel.target.env.GuideProbeTargets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;

/**
 * Analyzes the results of the given catalog queries to find the best guide star asterisms for
 * the given observation context. The Mascot Strehl algorithm is used to get a list of asterisms
 * for the stars found.
 *
 * See OT-27
 */
public class GemsCatalogResults {

    /**
     * Analyze the given position angles and search results to select tip tilt asterisms and flexure stars.
     * @param obsContext observation context
     * @param posAngles position angles to try (should contain at least one element: the current pos angle)
     * @param results results of catalog search
     * @param progress used to report progress of Mascot Strehl calculations and interrupt if requested
     * @return a sorted List of GemsGuideStars
     */
    public List<GemsGuideStars> analyze(ObsContext obsContext, Set<Angle> posAngles,
                                        List<GemsCatalogSearchResults> results, MascotProgress progress) {

        Coordinates base = obsContext.getBaseCoordinates();
        List<GemsGuideStars> result = new ArrayList<GemsGuideStars>();

        for (TiptiltFlexurePair pair : TiptiltFlexurePair.pairs(results)) {
            GemsGuideProbeGroup tiptiltGroup = pair.getTiptiltResults().getCriterion().getKey().getGroup();
            GemsGuideProbeGroup flexureGroup = pair.getFlexureResults().getCriterion().getKey().getGroup();
            List<SkyObject> tiptiltSkyObjectList = filter(obsContext, pair.getTiptiltResults().getResults(),
                    tiptiltGroup, posAngles);
            List<SkyObject> flexureSkyObjectList = filter(obsContext, pair.getFlexureResults().getResults(),
                    flexureGroup, posAngles);
            if (tiptiltSkyObjectList.size() != 0 && flexureSkyObjectList.size() != 0) {
                if (progress != null) {
                    progress.setProgressTitle("Finding asterisms for " + tiptiltGroup.getKey());
                }
                Magnitude.Band bandpass = getBandpass(tiptiltGroup, obsContext.getInstrument());
                double factor = getStrehlFactor(new Some<ObsContext>(obsContext));
                MascotCat.StrehlResults strehlResults = MascotCat.javaFindBestAsterismInSkyObjectList(
                        tiptiltSkyObjectList, base.getRaDeg(), base.getDecDeg(), bandpass.name(), factor, progress);
                for (Strehl strehl : strehlResults.strehlList()) {
                    result.addAll(analyzeAtAngles(obsContext, posAngles, strehl, flexureSkyObjectList, flexureGroup,
                            tiptiltGroup));
                }
            }
        }

        return sortResultsByRanking(result);
    }


    /**
     * Analyze the given position angles and search results to select tip tilt asterisms and flexure stars.
     * This version allows the progress argument to stop the strehl algorithm when a "good enough"
     * asterism has been found and use the results up until that point.
     *
     * @param obsContext observation context
     * @param posAngles position angles to try (should contain at least one element: 0. deg)
     * @param results results of catalog search
     * @param progress used to report progress of Mascot Strehl calculations and interrupt if requested
     *        (using the results up until that point)
     * @return a sorted List of GemsGuideStars
     */
    public List<GemsGuideStars> analyzeGoodEnough(final ObsContext obsContext, final Set<Angle> posAngles,
                                        List<GemsCatalogSearchResults> results, final MascotProgress progress) {

        Coordinates base = obsContext.getBaseCoordinates();
        final List<GemsGuideStars> result = new ArrayList<GemsGuideStars>();

        for (TiptiltFlexurePair pair : TiptiltFlexurePair.pairs(results)) {
            final GemsGuideProbeGroup tiptiltGroup = pair.getTiptiltResults().getCriterion().getKey().getGroup();
            final GemsGuideProbeGroup flexureGroup = pair.getFlexureResults().getCriterion().getKey().getGroup();
            final List<SkyObject> tiptiltSkyObjectList = filter(obsContext, pair.getTiptiltResults().getResults(),
                    tiptiltGroup, posAngles);
            final List<SkyObject> flexureSkyObjectList = filter(obsContext, pair.getFlexureResults().getResults(),
                    flexureGroup, posAngles);
            if (tiptiltSkyObjectList.size() != 0 && flexureSkyObjectList.size() != 0) {
                if (progress != null) {
                    progress.setProgressTitle("Finding asterisms for " + tiptiltGroup.getKey());
                }
                Magnitude.Band bandpass = getBandpass(tiptiltGroup, obsContext.getInstrument());
                MascotProgress strehlHandler = new MascotProgress() {
                    @Override
                    public boolean progress(Strehl strehl, int count, int total, boolean usable) {
                        final List<GemsGuideStars> subResult = analyzeAtAngles(obsContext, posAngles, strehl,
                                flexureSkyObjectList, flexureGroup, tiptiltGroup);
                        boolean used = subResult.size() != 0;
                        if (used) result.addAll(subResult);
                        return progress == null || progress.progress(strehl, count, total, used);
                    }

                    @Override
                    public void setProgressTitle(String s) {
                        progress.setProgressTitle(s);
                    }
                };

                double factor = getStrehlFactor(new Some<ObsContext>(obsContext));
                try {
                    MascotCat.javaFindBestAsterismInSkyObjectList(
                            tiptiltSkyObjectList, base.getRaDeg(), base.getDecDeg(), bandpass.name(), factor, strehlHandler);
                } catch (CancellationException e) {
                    // continue on with results so far?
                }
            }
        }

        return sortResultsByRanking(result);
    }

    // Tries the given asterism and flexure star at the given position angles and returns a list of
    // combinations that work.
    private List<GemsGuideStars> analyzeAtAngles(ObsContext obsContext, Set<Angle> posAngles, Strehl strehl,
                                                 List<SkyObject> flexureSkyObjectList,
                                                 GemsGuideProbeGroup flexureGroup,
                                                 GemsGuideProbeGroup tiptiltGroup) {
        final List<GemsGuideStars> result = new ArrayList<GemsGuideStars>();
        for (Angle posAngle : posAngles) {
            List<SkyObject> flexureList = filter(obsContext, flexureSkyObjectList, flexureGroup, posAngle);
            SkyObject[] flexureStars = GemsUtil.brightestFirstSkyObject(flexureList);
            result.addAll(analyzeStrehl(obsContext, strehl, posAngle, tiptiltGroup, flexureGroup, flexureStars, true));
            if ("CWFS".equals(tiptiltGroup.getKey())) {
                // try different order of cwfs1 and cwfs2
                result.addAll(analyzeStrehl(obsContext, strehl, posAngle, tiptiltGroup, flexureGroup, flexureStars, false));
            }
        }
        return result;
    }


    // Analyzes the given strehl object at the given position angle and returns a list of
    // GemsGuideStars objects, each containing a 1 to 3 star asterism from the given tiptiltGroup group and
    // one star from the flexure group. If any of the stars in the asterism is not valid at the position
    // angle or if no flexure star can be found, an empty list is returned.
    //
    // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
    // combinations of cwfs1 and cwfs2, since cwfs3 is otherwise fixed)
    private List<GemsGuideStars> analyzeStrehl(ObsContext obsContext, Strehl strehl, Angle posAngle,
                                                 GemsGuideProbeGroup tiptiltGroup, GemsGuideProbeGroup flexureGroup,
                                                 SkyObject[] flexureStars, boolean reverseOrder) {
        List<GemsGuideStars> result = new ArrayList<GemsGuideStars>();
        List<SPTarget> tiptiltTargetList = getTargetListFromStrehl(strehl);

        // XXX The TPE assumes canopus tiptilt if there are only 2 stars (one of each ODGW and CWFS),
        // So don't add any items to the list that have only 2 stars and GSAOI as tiptilt.
        if (tiptiltGroup == GsaoiOdgw.Group.instance && tiptiltTargetList.size() == 1) {
            return result;
        }

        if (validate(obsContext, tiptiltTargetList, tiptiltGroup, posAngle)) {
            List<GuideProbeTargets> guideProbeTargets = assignGuideProbeTargets(obsContext, posAngle,
                    tiptiltGroup, tiptiltTargetList, flexureGroup, flexureStars, reverseOrder);
            if (!guideProbeTargets.isEmpty()) {
                GuideGroup guideGroup = GuideGroup.create(None.<String>instance(), DefaultImList.create(guideProbeTargets));
                GemsStrehl gemsStrehl = new GemsStrehl(strehl.avgstrehl(), strehl.rmsstrehl(), strehl.minstrehl(), strehl.maxstrehl());
                GemsGuideStars gemsGuideStars = new GemsGuideStars(posAngle, tiptiltGroup, gemsStrehl, guideGroup);
                result.add(gemsGuideStars);
            }
        }
        return result;
    }


    // Returns a list of GuideProbeTargets for the given tiptilt targets and flexure star.
    //
    // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
    // combinations of cwfs1 and cwfs2, since cwfs3 is otherwise fixed)
    private List<GuideProbeTargets> assignGuideProbeTargets(ObsContext obsContext, Angle posAngle,
                                                              GemsGuideProbeGroup tiptiltGroup,
                                                              List<SPTarget> tiptiltTargetList,
                                                              GemsGuideProbeGroup flexureGroup,
                                                              SkyObject[] flexureStars,
                                                              boolean reverseOrder) {
        List<GuideProbeTargets> result = new ArrayList<GuideProbeTargets>(tiptiltTargetList.size() + 1);

        // assign guide probes for tiptilt asterism
        for(SPTarget target : tiptiltTargetList) {
            Option<GuideProbeTargets> guideProbeTargets = assignGuideProbeTarget(obsContext, posAngle, tiptiltGroup,
                    target, tiptiltGroup, result, tiptiltTargetList, reverseOrder);
            if (guideProbeTargets.isEmpty()) return new ArrayList<GuideProbeTargets>();
            result.add(guideProbeTargets.getValue());
            // Update the ObsContext, since validation of the following targets may depend on it
            obsContext = obsContext.withTargets(obsContext.getTargets().putPrimaryGuideProbeTargets(guideProbeTargets.getValue()));
        }

        // assign guide probe for flexure star
        for (SkyObject flexureStar : flexureStars) {
            SPTarget target = new SPTarget(flexureStar);
            Option<GuideProbeTargets> guideProbeTargets = assignGuideProbeTarget(obsContext, posAngle, flexureGroup,
                    target, tiptiltGroup, result, tiptiltTargetList, false);
            if (!guideProbeTargets.isEmpty()) {
                result.add(guideProbeTargets.getValue());
                break;
            }
        }

        if (result.size() == tiptiltTargetList.size() + 1) {
            return result;
        }
        return new ArrayList<GuideProbeTargets>();
    }


    // Returns the GuideProbeTargets object for the given tiptilt target.
    //
    // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
    // combinations of cwfs1 and cwfs2, since cwfs3 is otherwise fixed)
    private Option<GuideProbeTargets> assignGuideProbeTarget(ObsContext obsContext, Angle posAngle,
                                                             GemsGuideProbeGroup group, SPTarget target,
                                                             GemsGuideProbeGroup tiptiltGroup,
                                                             List<GuideProbeTargets> otherTargets,
                                                             List<SPTarget> tiptiltTargetList,
                                                             boolean reverseOrder) {
        // First try to assign cwfs3 to the brightest star, if applicable (assignCwfs3ToBrightest arg = true)
        Option<GuideProbe> guideProbe = getGuideProbe(obsContext, target, group, posAngle, tiptiltGroup,
                otherTargets, tiptiltTargetList, true, reverseOrder);

        if (guideProbe.isEmpty() && "CWFS".equals(tiptiltGroup.getKey())) {
            // if that didn't work, try to assign cwfs3 to the second brightest star (assignCwfs3ToBrightest arg = false)
            guideProbe = getGuideProbe(obsContext, target, group, posAngle, tiptiltGroup,
                    otherTargets, tiptiltTargetList, false, reverseOrder);
        }

        if (guideProbe.isEmpty()) {
            return None.instance();
        }
        return new Some<GuideProbeTargets>(GuideProbeTargets.create(guideProbe.getValue(), target));
    }


    // Returns the given skyObjectList with any objects removed that are not valid in at least one of the
    // given position angles.
    private List<SkyObject> filter(ObsContext obsContext, List<SkyObject> skyObjectList, GemsGuideProbeGroup group,
                                   Set<Angle> posAngles) {
        List<SkyObject> result = new ArrayList<SkyObject>(skyObjectList.size());
        for (Angle posAngle : posAngles) {
            result.addAll(filter(obsContext, skyObjectList, group, posAngle));
        }
        return result;
    }

    // Returns the given skyObjectList with any objects removed that are not valid in the
    // given position angle.
    private List<SkyObject> filter(ObsContext obsContext, List<SkyObject> skyObjectList,
                                   GemsGuideProbeGroup group, Angle posAngle) {
        List<SkyObject> result = new ArrayList<SkyObject>(skyObjectList.size());
        for (SkyObject skyObject : skyObjectList) {
            if (validate(obsContext, new SPTarget(skyObject), group, posAngle)) {
                result.add(skyObject);
            }
        }
        return result;
    }

    // Returns the input list sorted by ranking. See OT-27 and GemsGuideStars.compareTo
    private List<GemsGuideStars> sortResultsByRanking(List<GemsGuideStars> list) {
        // Sort by ranking and remove duplicates
        Set<GemsGuideStars> set = new TreeSet<GemsGuideStars>(list);
        List<GemsGuideStars> result = new ArrayList<GemsGuideStars>(set);
        Collections.reverse(result); // put highest ranking first in list
        printResults(result);
        return result;
    }

    private void printResults(List<GemsGuideStars> result) {
        System.out.println("Results:");
        int i = 0;
        for(GemsGuideStars gemsGuideStars : result) {
            i++;
            System.out.println("result #" + i + ": " + gemsGuideStars);
        }
    }

    // Returns true if all the stars in the given target list are valid for the given group
    private boolean validate(ObsContext obsContext, List<SPTarget> targetList, GemsGuideProbeGroup group, Angle posAngle) {
        for(SPTarget target : targetList) {
            if (!validate(obsContext, target, group, posAngle)) {
                System.out.println("Target " + target + " is not valid for " + group.getKey() + " at pos angle " + posAngle);
                return false;
            }
        }
        return true;
    }

    // Returns true if the given target is valid for the given group
    private boolean validate(ObsContext obsContext, SPTarget target, GemsGuideProbeGroup group, Angle posAngle) {
        final ObsContext ctx = obsContext.withPositionAngle(posAngle);
        for (GuideProbe guideProbe : group.getMembers()) {
            if (guideProbe instanceof ValidatableGuideProbe) {
                ValidatableGuideProbe v = (ValidatableGuideProbe) guideProbe;
                if (v.validate(target, ctx)) {
                    return true;
                }
            } else {
                return true; // validation not available
            }
        }
        return false;
    }

    // Returns the first valid guide probe for the given target in the given guide probe group at the given
    // position angle. Note that if tiptiltGroup != group, we're looking for a flexure star, otherwise a
    // tiptilt star.
    // If assignCwfs3ToBrightest is true, the brightest star (in tiptiltTargetList) is assigned to cwfs3,
    // otherwise the second brightest (OT-27).
    // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
    // combinations of cwfs1 and cwfs2, since cwfs3 is otherwise fixed)
    private Option<GuideProbe> getGuideProbe(ObsContext obsContext, SPTarget target, GemsGuideProbeGroup group,
                                             Angle posAngle, GemsGuideProbeGroup tiptiltGroup,
                                             List<GuideProbeTargets> otherTargets, List<SPTarget> tiptiltTargetList,
                                             boolean assignCwfs3ToBrightest, boolean reverseOrder) {
        final ObsContext ctx = obsContext.withPositionAngle(posAngle);

        boolean isFlexure = (tiptiltGroup != group);
        boolean isTiptilt = !isFlexure;

        if (isFlexure && "ODGW".equals(tiptiltGroup.getKey())) {
            // Special case:
            // If the tip tilt asterism is assigned to the GSAOI ODGW group, then the flexure star must be assigned to CWFS3.
            if (Canopus.Wfs.cwfs3.validate(target, ctx)) {
                return new Some<GuideProbe>(Canopus.Wfs.cwfs3);
            }
        } else {
            List<GuideProbe> members = new ArrayList<GuideProbe>(group.getMembers());
            if (reverseOrder) {
                Collections.reverse(members);
            }
            for (GuideProbe guideProbe : members) {
                boolean valid = validate(ctx, target, guideProbe);
                if (valid) {
                    if (isTiptilt) {
                        valid = checkOtherTargets(guideProbe, otherTargets);
                        if (valid && "CWFS".equals(tiptiltGroup.getKey())) {
                            valid = checkCwfs3Rule(guideProbe, target, tiptiltTargetList, assignCwfs3ToBrightest);
                        }
                    }
                    if (valid) {
                        return new Some<GuideProbe>(guideProbe);
                    }
                }
            }
        }
        return None.instance();
    }

    // Returns true if the given target is valid for the given guide probe
    private boolean validate(ObsContext ctx, SPTarget target, GuideProbe guideProbe) {
        boolean valid = (guideProbe instanceof ValidatableGuideProbe) ?
                ((ValidatableGuideProbe) guideProbe).validate(target, ctx) : true;

        // Additional check for mag range (for cwfs1 and cwfs2, since different than cwfs3 and group range)
        if (valid && guideProbe instanceof Canopus.Wfs) {
            final Canopus.Wfs wfs = (Canopus.Wfs) guideProbe;
            final GemsMagnitudeTable.CanopusWfsCalculator canopusWfsCalculator = GemsMagnitudeTable.CanopusWfsMagnitudeLimitsCalculator();
            valid = checkMagLimit(target, canopusWfsCalculator.getNominalMagnitudeLimits(wfs));
        }
        return valid;
    }

    // Returns true if the target magnitude is within the given limits
    private boolean checkMagLimit(SPTarget target, final MagnitudeLimits magLimits) {
        return target.getMagnitude(magLimits.getBand()).map(new MapOp<Magnitude, Boolean>() {
            @Override public Boolean apply(Magnitude magnitude) {
                return magLimits.contains(magnitude);
            }
        }).getOrElse(true);
    }

    // Returns true if none of the other targets are assigned the given guide probe.
    //
    // From OT-27: Only one star per GSAOI ODGW is allowed -- for example, if an asterism is formed
    // of two guide stars destined for ODGW2, then it cannot be used.
    //
    // Also for Canopus: only assign one star per cwfs
    private boolean checkOtherTargets(GuideProbe guideProbe, List<GuideProbeTargets> otherTargets) {
        for (GuideProbeTargets otherTarget : otherTargets) {
            if (otherTarget.getGuider() == guideProbe) {
                return false;
            }
        }
        return true;
    }

    // Returns true if the given cwfs guide probe can be assigned to the given target according to the rules in OT-27.
    // If assignCwfs3ToBrightest is true, the brightest star in the asterism (in tiptiltTargetList) is assigned to cwfs3,
    // otherwise the second brightest (OT-27).
    private boolean checkCwfs3Rule(GuideProbe guideProbe, SPTarget target, List<SPTarget> tiptiltTargetList,
                               boolean assignCwfs3ToBrightest) {
        boolean isCwfs3 = guideProbe == Canopus.Wfs.cwfs3;
        if (tiptiltTargetList.size() <= 1) {
            return isCwfs3; // single star asterism must be cwfs3
        }

        // sort, put brightest stars first
        SPTarget[] ar = GemsUtil.brightestFirstSPTarget(tiptiltTargetList);
        boolean targetIsBrightest = ar[0] == target;
        boolean targetIsSecondBrightest = ar[1] == target;

        if (isCwfs3) {
            if (assignCwfs3ToBrightest) return targetIsBrightest;
            return targetIsSecondBrightest;
        } else {
            if (assignCwfs3ToBrightest) return !targetIsBrightest;
            return !targetIsSecondBrightest;
        }
    }

    // Returns the stars in the given asterism as a SPTarget list, sorted by R mag, brightest first.
    private List<SPTarget> getTargetListFromStrehl(Strehl strehl) {
        List<SPTarget> targetList = new ArrayList<SPTarget>();
        for(Star star : strehl.getStars()) {
            targetList.add(starToSPTarget(star));
        }
        return Arrays.asList(GemsUtil.brightestFirstSPTarget(targetList));
    }

    // Returns a SkyObject for the given Star
    private SkyObject starToSkyObject(Star star) {
        String id = star.name();

        Angle ra = new Angle(star.ra(), Angle.Unit.DEGREES);
        Angle dec = new Angle(star.dec(), Angle.Unit.DEGREES);
        HmsDegCoordinates.Builder b = new HmsDegCoordinates.Builder(ra, dec);
        HmsDegCoordinates coords = b.build();

        List<Magnitude> magList = new ArrayList<Magnitude>(6);

        double invalid = MascotConf.invalidMag();
        if (star.bmag() != invalid) magList.add(new Magnitude(Magnitude.Band.B, star.bmag()));
        if (star.vmag() != invalid) magList.add(new Magnitude(Magnitude.Band.V, star.vmag()));
        if (star.rmag() != invalid) magList.add(new Magnitude(Magnitude.Band.R, star.rmag()));
        if (star.jmag() != invalid) magList.add(new Magnitude(Magnitude.Band.J, star.jmag()));
        if (star.hmag() != invalid) magList.add(new Magnitude(Magnitude.Band.H, star.hmag()));
        if (star.kmag() != invalid) magList.add(new Magnitude(Magnitude.Band.K, star.kmag()));
        ImList<Magnitude> mags = DefaultImList.create(magList);

        return new SkyObject.Builder(id, coords).magnitudes(mags).build();
    }

    // Returns an SPTarget for the given Star
    private SPTarget starToSPTarget(Star star) {
        return new SPTarget(starToSkyObject(star));
    }

    // OT-33: If the asterism is a Canopus asterism, use R. If an ODGW asterism,
    // see OT-22 for a mapping of GSAOI filters to J, H, and K.
    // If iterating over filters, I think we can assume the filter in
    // the static component as a first pass at least.
    private Magnitude.Band getBandpass(GemsGuideProbeGroup group, SPInstObsComp inst) {
        if (group == GsaoiOdgw.Group.instance) {
            if (inst instanceof Gsaoi) {
                Gsaoi gsaoi = (Gsaoi) inst;
                Option<Magnitude.Band> band = gsaoi.getFilter().getCatalogBand();
                if (!band.isEmpty()) {
                    return band.getValue();
                }
            }
        }
        return Magnitude.Band.R;
    }

    // REL-426: Multiply the average, min, and max Strehl values reported by Mascot by the following scale
    // factors depending on the filter used in the instrument component of the observation (GSAOI, F2 in the future):
    //   0.2 in J,
    //   0.3 in H,
    //   0.4 in K
    // See OT-22 for the mapping of GSAOI filters to JHK equivalent
    //
    // Update for REL-1321:
    // Multiply the average, min, and max Strehl values reported by Mascot by the following scale factors depending
    // on the filter used in the instrument component of the observation (GSAOI, F2 and GMOS-S in the future) and
    // the conditions:
    //  J: IQ20=0.12 IQ70=0.06 IQ85=0.024 IQAny=0.01
    //  H: IQ20=0.18 IQ70=0.14 IQ85=0.06 IQAny=0.01
    //  K: IQ20=0.35 IQ70=0.18 IQ85=0.12 IQAny=0.01
    public static double getStrehlFactor(Option<ObsContext> obsContextOption) {
        if (!obsContextOption.isEmpty()) {
            ObsContext obsContext = obsContextOption.getValue();
            SPInstObsComp inst = obsContext.getInstrument();
            if (inst instanceof Gsaoi) {
                Gsaoi gsaoi = (Gsaoi) inst;
                Option<Magnitude.Band> band = gsaoi.getFilter().getCatalogBand();
                if (!band.isEmpty()) {
                    String s = band.getValue().name();
                    SPSiteQuality.Conditions conditions = obsContext.getConditions();
                    if ("J".equals(s)) {
                        if (conditions != null) {
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_20) return 0.12;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_70) return 0.06;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_85) return 0.024;
                        }
                        return 0.01;
                    }
                    if ("H".equals(s)) {
                        if (conditions != null) {
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_20) return 0.18;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_70) return 0.14;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_85) return 0.06;

                        }
                        return 0.01;
                    }
                    if ("K".equals(s)) {
                        if (conditions != null) {
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_20) return 0.35;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_70) return 0.18;
                            if (conditions.iq == SPSiteQuality.ImageQuality.PERCENT_85) return 0.12;
                        }
                        return 0.01;
                    }
                }
            }
        }
        return 0.3;
    }
}
