//
// $Id: AbortObserveEvent.java 6272 2005-06-02 05:39:33Z shane $
//

package edu.gemini.spModel.event;

import edu.gemini.pot.sp.SPObservationID;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.PioParseException;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;

/**
 * An event that signifies that an observation was aborted.
 */
public final class AbortObserveEvent extends ObsExecEvent {

    public static final String REASON_PARAM = "reason";

    private String _reason;

    public AbortObserveEvent(long time, SPObservationID obsId, String reason) {
        super(time, obsId);
        _reason = reason;
    }

    public AbortObserveEvent(ParamSet paramSet) throws PioParseException {
        super(paramSet);

        _reason = Pio.getValue(paramSet, REASON_PARAM);
    }

    public String getReason() {
        return _reason;
    }

    public void doAction(ExecAction action) {
        action.abortObserve(this);
    }

    public ParamSet toParamSet(PioFactory factory) {
        ParamSet paramSet = super.toParamSet(factory);

        if (_reason != null) {
            Pio.addParam(factory, paramSet, REASON_PARAM, _reason);
        }
        return paramSet;
    }

    public boolean equals(Object other) {
        boolean res = super.equals(other);
        if (!res) return false;

        AbortObserveEvent that = (AbortObserveEvent) other;
        if (_reason == null) {
            if (that._reason != null) return false;
        } else if (that._reason == null) {
            return false;
        } else {
            if (!_reason.equals(that._reason)) return false;
        }

        return true;
    }

    public int hashCode() {
        int res = super.hashCode();
        res = 37 * res + _reason.hashCode();
        return res;
    }

    public String getKind() {
        return "AbortObserve";
    }

    public String getName() {
        return "Abort Observe";
    }
}
