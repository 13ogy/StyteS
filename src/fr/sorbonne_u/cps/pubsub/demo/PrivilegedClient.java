package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

public class PrivilegedClient extends AbstractComponent {

    // -------------------------------------------------------------------------
    // PrivilegedClient — registration + privileged (extends publication)
    // -------------------------------------------------------------------------



    private final ClientRegistrationPlugin regPlugin;
    private final ClientPrivilegedPlugin privPlugin;
    private final TestScenario testScenario;
    private final RegistrationCI.RegistrationClass initialRC;

    public PrivilegedClient(String uri, TestScenario ts,
                            RegistrationCI.RegistrationClass rc) throws Exception {
        super(uri, 1, 1);
        this.testScenario = ts;
        this.initialRC    = rc;

        this.regPlugin  = new ClientRegistrationPlugin();
        this.regPlugin.setPluginURI(uri + "-reg");

        this.privPlugin = new ClientPrivilegedPlugin(regPlugin);
        this.privPlugin.setPluginURI(uri + "-priv");

        this.toggleTracing();
        this.getTracer().setTitle(uri);
    }

    @Override
    public synchronized void start() throws ComponentStartException {
        try {
            this.installPlugin(this.regPlugin);
            this.installPlugin(this.privPlugin);
        } catch (Exception e) {
            throw new ComponentStartException(e);
        }
        super.start();
    }

    @Override
    public void execute() throws Exception {
        super.execute();
        this.regPlugin.register(this.initialRC);

        if (this.testScenario != null) {
            this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI,
                    this.testScenario.getClockURI());
            this.executeTestScenario(this.testScenario);
        }
    }

    @Override
    public synchronized void finalise() throws Exception {
        this.privPlugin.finalise();
        this.regPlugin.finalise();
        super.finalise();
    }

    @Override
    public synchronized void shutdown() throws ComponentShutdownException {
        try {
            this.privPlugin.uninstall();
            this.regPlugin.uninstall();
        } catch (Exception e) {
            throw new ComponentShutdownException(e);
        }
        super.shutdown();
    }

    public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
        this.regPlugin.modifyServiceClass(rc);
    }
    public void createChannel(String channel, String authorisedUsers) throws Exception {
        this.privPlugin.createChannel(channel, authorisedUsers);
    }
    public void publish(String channel, MessageI message) throws Exception {
        this.privPlugin.publish(channel, message);
    }
}