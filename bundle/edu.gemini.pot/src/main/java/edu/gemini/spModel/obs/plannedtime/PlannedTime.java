package edu.gemini.spModel.obs.plannedtime;

import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.PredicateOp;
import edu.gemini.spModel.config2.Config;
import edu.gemini.spModel.config2.ConfigSequence;
import edu.gemini.spModel.config2.ItemKey;
import edu.gemini.spModel.time.ChargeClass;

import java.io.Serializable;
import java.util.*;

/**
 * Step-by-step detailed planned time accounting information for an observation.
 */
public final class PlannedTime implements Serializable {
    /**
     * Determines whether any of the given items have been updated in the
     * configuration (as opposed to their values in the previous configuration).
     *
     * @param cur current configuration
     * @param prevOpt previous configuration wrapped in an Option
     * @param keys item keys to examine
     *
     * @return <code>true</code> if any item has been updated in <code>cur</code>,
     * <code>false</code> otherwise
     */
    public static boolean isUpdated(Config cur, Option<Config> prevOpt, ItemKey... keys) {
        if (prevOpt.isEmpty()) return false;
        Config prev = prevOpt.getValue();

        for (ItemKey key : keys) {
            Object curValue  = cur.getItemValue(key);
            Object prevValue = prev.getItemValue(key);

            if (curValue == null) {
                if (prevValue != null) return true;
            } else {
                if (!curValue.equals(prevValue)) return true;
            }
        }
        return false;
    }

    // A Comparator of two Comparable items of a given type that reverses the
    // outcome of the natural ordering.
//    private static final class RevComparator<T extends Comparable<T>> implements Comparator<T> {
//        @Override public int compare(T o1, T o2) { return o2.compareTo(o1); }
//    }

    public interface StepCalculator {
        CategorizedTimeGroup calc(Config stepConfig, Option<Config> prevStepConfig);
    }

    private static long toMillsec(double sec) {
        return Math.round(1000.0 * sec);
    }

    public enum Category {
        CONFIG_CHANGE("Config Change"),
        EXPOSURE("Exposure"),
        READOUT("Readout"),
        DHS_WRITE("DHS Write"),
        ;

        public final String display;

        private Category(String display) {
            this.display = display;
        }

        public final CategorizedTime ZERO = new CategorizedTime(this, 0);

        // REL-1678: 7 seconds DHS write overhead
        public static final CategorizedTime DHS_OVERHEAD = CategorizedTime.apply(Category.DHS_WRITE, 7000);
    }

    public static final class CategorizedTime implements Comparable<CategorizedTime>, Serializable {
//        public static Comparator<CategorizedTime> REV_COMPARATOR = new RevComparator<CategorizedTime>();

        public final Category category;

        /** Time in milliseconds. */
        public final long time;

        public final String detail;

        private CategorizedTime(Category cat, long time) {
            this(cat, time, null);
        }

        private CategorizedTime(Category cat, long time, String detail) {
            if (cat == null) throw new IllegalArgumentException("Category is null");
            this.category = cat;
            this.time     = time;
            this.detail   = (detail == null) ? cat.display : detail;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CategorizedTime that = (CategorizedTime) o;

            if (time != that.time) return false;
            if (category != that.category) return false;
            if (detail != that.detail) return false;

            return true;
        }

        @Override public int hashCode() {
            int result = category.hashCode();
            result = 31 * result + (int) (time ^ (time >>> 32));
            result = 31 * result + detail.hashCode();
            return result;
        }

        public CategorizedTime add(long time) {
            return (time == 0) ? this : new CategorizedTime(category, this.time + time, this.detail);
        }

        /**
         * @param sec time in seconds for compatibility with old code --
         * converted to a <code>long</code> in millisec
         */
        public static CategorizedTime fromSeconds(Category cat, double sec) {
            long time = toMillsec(sec);
            return (time == 0) ? cat.ZERO : new CategorizedTime(cat, time);
        }

        public static CategorizedTime fromSeconds(Category cat, double sec, String detail) {
            return (detail == null) ? fromSeconds(cat, sec) : new CategorizedTime(cat, toMillsec(sec), detail);
        }

        public static CategorizedTime apply(Category cat, long time) {
            return (time == 0) ? cat.ZERO : new CategorizedTime(cat, time);
        }

        public static CategorizedTime apply(Category cat, long time, String detail) {
            return (detail == null) ? apply(cat, time) : new CategorizedTime(cat, time, detail);
        }

        @Override public int compareTo(CategorizedTime that) {
            int res = category.compareTo(that.category);
            if (res != 0) return res;

            if (time < that.time) return -1;
            if (time > that.time) return 1;

            return (detail == null || that.detail == null) ? 0 : detail.compareTo(that.detail);
        }
    }

    public static final class CategorizedTimeGroup implements Iterable<CategorizedTime>, Serializable {
        public static final CategorizedTimeGroup EMPTY = new CategorizedTimeGroup(Collections.<CategorizedTime>emptySet());

        public final Set<CategorizedTime> times;

        private CategorizedTimeGroup(Set<CategorizedTime> times) {
            this.times = times;
        }

        /**
         * Filters all the contained CategorizedTimes on a particular category,
         * returning only those in this category.
         */
        public Set<CategorizedTime> times(Category cat) {
            Set<CategorizedTime> res = new TreeSet<CategorizedTime>();
            for (CategorizedTime ct : times) if (ct.category == cat) res.add(ct);
            return res;
        }

        public CategorizedTimeGroup filter(PredicateOp<CategorizedTime> op) {
            Set<CategorizedTime> res = new TreeSet<CategorizedTime>();
            for (CategorizedTime ct : times) if (op.apply(ct)) res.add(ct);
            return (times.size() == res.size()) ? this : new CategorizedTimeGroup(res);
        }

        public CategorizedTimeGroup add(CategorizedTime ct) {
            Set<CategorizedTime> s = new TreeSet<CategorizedTime>(times);
            s.add(ct);
            return new CategorizedTimeGroup(Collections.unmodifiableSet(s));
        }

        public CategorizedTimeGroup add(CategorizedTimeGroup ctg) {
            if (times.size() == 0) return ctg;
            if (ctg.times.size() == 0) return this;

            Set<CategorizedTime> s = new TreeSet<CategorizedTime>(times);
            s.addAll(ctg.times);
            return new CategorizedTimeGroup(Collections.unmodifiableSet(s));
        }

        public CategorizedTimeGroup addAll(Collection<CategorizedTime> times) {
            if (times.size() == 0) return this;
            Set<CategorizedTime> s = new TreeSet<CategorizedTime>(this.times);
            s.addAll(times);
            return new CategorizedTimeGroup(Collections.unmodifiableSet(s));
        }

        public CategorizedTimeGroup addAll(CategorizedTime... times) {
            if (times.length == 0) return this;
            return addAll(Arrays.asList(times));
        }

        public static CategorizedTimeGroup apply(CategorizedTime ct) {
            return EMPTY.add(ct);
        }

        public static CategorizedTimeGroup apply(Collection<CategorizedTime> col) {
            return EMPTY.addAll(col);
        }

        public static CategorizedTimeGroup apply(CategorizedTime... col) {
            return EMPTY.addAll(col);
        }

        @Override public Iterator<CategorizedTime> iterator() {
            return times.iterator();
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CategorizedTimeGroup that = (CategorizedTimeGroup) o;
            if (!times.equals(that.times)) return false;
            return true;
        }

        @Override public int hashCode() {
            return times.hashCode();
        }

        public Map<Category, CategorizedTime> maxTimes() {
            Map<Category, CategorizedTime> max = new TreeMap<Category, CategorizedTime>();
            for (CategorizedTime ct : times) {
                CategorizedTime cur = max.get(ct.category);
                if ((cur == null) || (cur.compareTo(ct) < 0)) {
                    max.put(ct.category, ct);
                }
            }
            return max;
        }

        public long totalTime() {
            if (times.size() == 0) return 0;

            // Times are stored sorted.  All CategorizedTime instances in the
            // same category except the last (biggest) should be ignored
            // because they represent concurrent actions.
            Iterator<CategorizedTime> it = times.iterator();
            long res = 0;
            CategorizedTime prev = it.next();
            while (it.hasNext()) {
                CategorizedTime cur = it.next();
                if (cur.category != prev.category) res += prev.time;
                prev = cur;
            }
            res += prev.time;
            return res;
        }
    }

    public static final class Setup implements Serializable {
        /** Time in milliseconds. */
        public final long time;
        public final long reacquisitionTime;
        public final ChargeClass chargeClass;

        /**
         * @param time time in milliseconds.
         */
        private Setup(long time, long reacquisitionTime, ChargeClass chargeClass) {
            if (chargeClass == null) throw new IllegalArgumentException("chargeClass is null");
            this.time              = time;
            this.reacquisitionTime = reacquisitionTime;
            this.chargeClass       = chargeClass;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Setup setup = (Setup) o;

            if (time != setup.time) return false;
            if (reacquisitionTime != setup.reacquisitionTime) return false;
            if (chargeClass != setup.chargeClass) return false;

            return true;
        }

        @Override public int hashCode() {
            int result = (int) (time ^ (time >>> 32));
            result = 31 * result + (int) (reacquisitionTime ^ (reacquisitionTime >>> 32));
            result = 31 * result + chargeClass.hashCode();
            return result;
        }

        /**
         * @param sec compatibility with old code base; converted to ms
         */
        public static Setup fromSeconds(double sec, double reacquSec, ChargeClass chargeClass) {
            return new Setup(toMillsec(sec), toMillsec(reacquSec), chargeClass);
        }

        public static Setup apply(long time, ChargeClass chargeClass) {
            return new Setup(time, 0, chargeClass);
        }

        public static Setup apply(long time, long reacquTime, ChargeClass chargeClass) {
            return new Setup(time, reacquTime, chargeClass);
        }
    }

    public static final class Step implements Serializable {
        public final CategorizedTimeGroup times;
        public final ChargeClass chargeClass;
        public final boolean executed;
        public final String obsType;


        private Step(CategorizedTimeGroup times, ChargeClass chargeClass, boolean executed, String obsType) {
            if (times == null) throw new IllegalArgumentException("times are null");
            if (chargeClass == null) throw new IllegalArgumentException("chargeClass is null");
            if (obsType == null) throw new IllegalArgumentException("obsType is null");

            this.times       = times;
            this.chargeClass = chargeClass;
            this.executed    = executed;
            this.obsType     = obsType;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Step step = (Step) o;

            if (executed != step.executed) return false;
            if (chargeClass != step.chargeClass) return false;
            if (!obsType.equals(step.obsType)) return false;
            if (!times.equals(step.times)) return false;

            return true;
        }

        @Override public int hashCode() {
            int result = times.hashCode();
            result = 31 * result + chargeClass.hashCode();
            result = 31 * result + (executed ? 1 : 0);
            result = 31 * result + obsType.hashCode();
            return result;
        }

        public long totalTime() {
            return times.totalTime();
        }

        public long totalTime(ChargeClass chargeClass) {
            return (this.chargeClass == chargeClass) ? totalTime() : 0;
        }

        public static Step apply(CategorizedTimeGroup times, ChargeClass chargeClass, boolean executed, String obsType) {
            return new Step(times, chargeClass, executed, obsType);
        }
    }

    public final Setup setup;
    public final List<Step> steps;
    public final ConfigSequence sequence;

    private PlannedTime(Setup setup, List<Step> steps, ConfigSequence sequence) {
        if (setup == null) throw new IllegalArgumentException("setup is null");
        if (steps == null) throw new IllegalArgumentException("steps are null");

        this.setup    = setup;
        this.steps    = steps;
        this.sequence = sequence;
    }

    public long totalTime() {
        long res = setup.time;
        for (Step step : steps) res += step.totalTime();
        return res;
    }

    public long totalTime(ChargeClass chargeClass) {
        long res = (setup.chargeClass == chargeClass) ? setup.time : 0;
        for (Step step : steps) res += step.totalTime(chargeClass);
        return res;
    }

    public PlannedTimeSummary toPlannedTimeSummary() {
        return new PlannedTimeSummary(totalTime(ChargeClass.PROGRAM), totalTime());
    }

    public PlannedStepSummary toPlannedStepSummary() {

        long[] stepTimes   = new long[steps.size()];
        boolean[] executed = new boolean[steps.size()];
        String[] obsTypes  = new String[steps.size()];

        int i = 0;
        for (Step step : steps) {
            stepTimes[i] = step.totalTime();
            executed[i]  = step.executed;
            obsTypes[i]  = step.obsType;
            ++i;
         }

        return new PlannedStepSummary(setup.time, setup.reacquisitionTime, stepTimes, executed, obsTypes);
    }

    public static PlannedTime apply(Setup setup) {
        return new PlannedTime(setup, Collections.<Step>emptyList(), ConfigSequence.EMPTY);
    }

    public static PlannedTime apply(Setup setup, List<Step> steps, ConfigSequence sequence) {
        steps = Collections.unmodifiableList(new ArrayList<Step>(steps));
        return new PlannedTime(setup, steps, sequence);
    }
}
