package edu.gemini.epics.acm;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableSet;

import edu.gemini.epics.EpicsService;
import gov.aps.jca.CAException;

/**
 * Works as the main access point for the EPICS Action Command Model API. It
 * acts as a factory for all the API objects, and controls the life cycle of the
 * underlying EPICS services.
 * <p>
 * CaService is a singleton.
 * <p>
 * The list of IP addresses used to discover EPICS channels can be set by
 * calling <code>setAddressList()</code> before the first call to getInstance().
 * Calling <code>setAddressList()</code> after the singleton has been created
 * has no effect. If no list is set when the singleton is created, it will use
 * the value of the environment variable EPICS_CA_ADDR_LIST.
 * <p>
 * The method <code>unbind()</code> must be called to stop the service. Failing
 * to do it will keep the application running. References to the CaService must
 * not be used once <code>unbind()</code> has been called.
 * 
 * @author jluhrs
 *
 */
public final class CaService {

    private static final String EPICS_CA_ADDR_LIST = "EPICS_CA_ADDR_LIST";
    private EpicsService epicsService;
    private final Map<String, CaStatusAcceptorImpl> statusAcceptors;
    private final Map<String, CaApplySenderImpl> applySenders;
    private final Map<String, CaCommandSenderImpl> commandSenders;
    static private String addrList;
    static private CaService theInstance;

    private CaService(String addrList) {
        statusAcceptors = new HashMap<>();
        applySenders = new HashMap<>();
        commandSenders = new HashMap<>();
        epicsService = new EpicsService(addrList);

        epicsService.startService();
    }

    private CaService() {

        this(System.getenv(EPICS_CA_ADDR_LIST).replaceAll("\\\\ ", " "));

    }

    /**
     * Sets the list of IP addresses used to discover EPICS channels.
     * 
     * @param addrList
     *            the list of IP addresses used to discover EPICS channels.
     */
    public static void setAddressList(String addrList) {
        CaService.addrList = addrList;
    }

    /**
     * Retrieves the CaService single instance.
     * 
     * @return the single instance of CaService.
     */
    public static CaService getInstance() {
        if (theInstance == null) {
            if (addrList == null) {
                theInstance = new CaService();
            } else {
                theInstance = new CaService(addrList);
            }
        }
        return theInstance;
    }

    /**
     * Free resources and stop the underlying EPICS service.
     */
    public void unbind() {
        assert (epicsService != null);
        for (CaStatusAcceptorImpl sa : statusAcceptors.values()) {
            sa.unbind();
        }
        for (CaApplySenderImpl apply : applySenders.values()) {
            apply.unbind();
        }
        for (CaCommandSenderImpl cs : commandSenders.values()) {
            cs.unbind();
        }
        epicsService.stopService();
        epicsService = null;
        theInstance = null;
    }

    /**
     * Creates a status acceptor. If the status acceptor already exists, it
     * returns the existing object.
     * 
     * @param name
     *            the name of the new status acceptor.
     * @param description
     *            optional description for the status acceptor
     * @return the status acceptor.
     */
    public CaStatusAcceptor createStatusAcceptor(String name, String description) {
        CaStatusAcceptorImpl sa = statusAcceptors.get(name);
        if (sa == null) {
            sa = new CaStatusAcceptorImpl(name, description, epicsService);
            statusAcceptors.put(name, sa);
        }

        return sa;
    }

    public CaStatusAcceptor createStatusAcceptor(String name) {
        return createStatusAcceptor(name, null);
    }

    /**
     * Retrieves an existing status acceptor.
     * 
     * @param name
     *            the name of the status acceptor.
     * @return the status acceptor, or <code>null</code> if it does not exist.
     */
    public CaStatusAcceptor getStatusAcceptor(String name) {
        return statusAcceptors.get(name);
    }

    /**
     * Creates an apply sender. If the apply sender already exists, it returns
     * the existing object.
     * 
     * @param name
     *            the name of the new apply sender.
     * @param applyRecord
     *            the name of the EPICS apply record.
     * @param carRecord
     *            the name of the EPICS CAR record associated with the apply.
     * @param description
     *            optional description for the apply sender.
     * @return the apply sender.
     * @throws CAException
     */
    public CaApplySender createApplySender(String name, String applyRecord,
            String carRecord, String description) throws CAException {
        CaApplySenderImpl apply = applySenders.get(name);
        if (apply == null) {
            apply = new CaApplySenderImpl(name, applyRecord, carRecord,
                    description, epicsService);
            applySenders.put(name, apply);
        }
        return apply;
    }

    public CaApplySender createApplySender(String name, String applyRecord,
            String carRecord) throws CAException {
        return createApplySender(name, applyRecord, carRecord, null);
    }

    /**
     * Retrieves an existing apply sender.
     * 
     * @param name
     *            the name of the apply sender.
     * @return the apply sender, or <code>null</code> if it does not exist.
     */
    public CaApplySender getApplySender(String name) {
        return applySenders.get(name);
    }

    /**
     * Creates an command sender. If the command sender already exists, it
     * returns the existing object.
     * 
     * @param name
     *            the name of the new command sender.
     * @param apply
     *            the apply sender used to trigger the command.
     * @param cadName
     *            the EPICS name of the CAD record, needed to mark the record
     *            for execution if it has no parameters.
     * @param description
     *            optional description for the command sender
     * @return the command sender.
     */
    public CaCommandSender createCommandSender(String name,
            CaApplySender apply, String cadName, String description) {
        CaCommandSenderImpl cs = commandSenders.get(name);
        if (cs == null) {
            cs = new CaCommandSenderImpl(name, apply, description,
                    epicsService, cadName);
            commandSenders.put(name, cs);
        }
        return cs;
    }

    public CaCommandSender createCommandSender(String name,
            CaApplySender apply, String cadName) {
        return createCommandSender(name, apply, cadName, null);
    }

    /**
     * Retrieves an existing command sender.
     * 
     * @param name
     *            the name of the command sender.
     * @return the command sender, or <code>null</code> if it does not exist.
     */
    public CaCommandSender getCommandSender(String name) {
        return commandSenders.get(name);
    }

    /**
     * Retrieves the names of all existing command senders
     * 
     * @return a set of all the command sender names.
     */
    public ImmutableSet<String> getCommandSenderNames() {
        return ImmutableSet.copyOf(commandSenders.keySet());
    }

    /**
     * Retrieves the names of all existing apply senders
     * 
     * @return a set of all the command sender names.
     */
    public ImmutableSet<String> getApplySenderNames() {
        return ImmutableSet.copyOf(applySenders.keySet());
    }

    /**
     * Retrieves the names of all existing status acceptors
     * 
     * @return a set of all the status acceptors names.
     */
    public ImmutableSet<String> getStatusAcceptorsNames() {
        return ImmutableSet.copyOf(statusAcceptors.keySet());
    }

    /**
     * Destroys a command sender with a given name. If the command sender does
     * not exists, it does nothing.
     * 
     * @param name the name of the command sender to destroy.
     */
    public void destroyCommandSender(String name) {
        CaCommandSenderImpl cs = commandSenders.remove(name);
        if (cs != null) {
            cs.unbind();
        }
    }

    /**
     * Destroys a apply sender with a given name. If the apply sender does
     * not exists, it does nothing.
     * 
     * @param name the name of the apply sender to destroy.
     */
    public void destroyApplySender(String name) {
        CaApplySenderImpl apply = applySenders.remove(name);
        if (apply != null) {
            apply.unbind();
        }
    }

    /**
     * Destroys a status acceptor with a given name. If the status acceptor does
     * not exists, it does nothing.
     * 
     * @param name the name of the status acceptor to destroy.
     */
    public void destroyStatusAcceptor(String name) {
        CaStatusAcceptorImpl sa = statusAcceptors.remove(name);
        if (sa != null) {
            sa.unbind();
        }
    }

}
