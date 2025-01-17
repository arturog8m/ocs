package edu.gemini.epics.acm;

/**
 * Defines the interface that must be implemented by clients to monitor the
 * execution of a command.
 * 
 * @author jluhrs
 *
 */
public interface CaCommandListener {
    /**
     * Called when the command completes successfully.
     */
    void onSuccess();

    /**
     * Called when the command completes with an error.
     */
    void onFailure(Exception cause);
}
