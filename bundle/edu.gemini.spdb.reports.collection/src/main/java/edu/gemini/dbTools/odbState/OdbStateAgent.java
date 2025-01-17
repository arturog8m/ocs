//
// $Id: OdbStateAgent.java 46832 2012-07-19 00:28:38Z rnorris $
//
package edu.gemini.dbTools.odbState;

import edu.gemini.pot.client.SPDB;
import edu.gemini.pot.spdb.IDBDatabaseService;
import edu.gemini.spModel.core.SPProgramID;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.logging.Logger;

public final class OdbStateAgent {

    private final OdbStateIO stateIO;

    public OdbStateAgent(final Logger log, final OdbStateConfig config) {
        log.info("State file is " + config.stateFile.getAbsolutePath());
        this.stateIO = new OdbStateIO(log, config.stateFile);
    }

    // For each program key, get a reference to the corresponding program and then fetch its state.
    private static SortedMap<SPProgramID, ProgramState> getCurrentState(final Logger log, final IDBDatabaseService db, Set<Principal> user) {
        final SortedMap<SPProgramID, ProgramState> m = new TreeMap<SPProgramID, ProgramState>();
        final List<ProgramListFunctor.ProgramRef> refs = ProgramListFunctor.getProgramRefs(db, user);
        for (final ProgramListFunctor.ProgramRef ref : refs) {
            if (ref.getId().toString().matches("G[NS]-\\d\\d\\d\\d[AB]-.*")) {
                final ProgramState ps = ProgramStateFunctor.getProgramState(log, db, ref.getKey(), user);
                if (ps != null)
                    m.put(ref.getId(), ps);
            }
        }
        return m;
    }

    private void writeState(final SortedMap<SPProgramID, ProgramState> state) throws IOException {
        stateIO.writeState(state.values().toArray(ProgramState.EMPTY_STATE_ARRAY));
    }

    public void updateState(final Logger log, Set<Principal> user) throws IOException {
        writeState(getCurrentState(log, SPDB.get(), user));
    }

    @SuppressWarnings("UnusedParameters")
    public static void run(final File tempDir, final Logger log, final Map<String, String> env, Set<Principal> user) throws IOException {
        new OdbStateAgent(log, new OdbStateConfig(tempDir)).updateState(log, user);
    }

}
