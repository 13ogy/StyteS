package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.util.ArrayList;

public class PublisherClient extends AbstractComponent {

    // -------------------------------------------------------------------------
    // PublisherClient — registration + publication only
    // -------------------------------------------------------------------------

    private final ClientRegistrationPlugin regPlugin;
    private final ClientPublicationPlugin pubPlugin;
    private final TestScenario testScenario;
    private final RegistrationCI.RegistrationClass initialRC;

    protected PublisherClient(String uri, TestScenario ts,
                           RegistrationCI.RegistrationClass rc) throws Exception {
        super(uri, 1, 1);
        this.testScenario = ts;
        this.initialRC    = rc;

        this.regPlugin = new ClientRegistrationPlugin();
        this.regPlugin.setPluginURI(uri + "-reg");

        this.pubPlugin = new ClientPublicationPlugin(regPlugin);
        this.pubPlugin.setPluginURI(uri + "-pub");

        this.toggleTracing();
        this.getTracer().setTitle(uri);
    }

    @Override
    public synchronized void start() throws ComponentStartException {
        try {
            this.installPlugin(this.regPlugin);
            this.installPlugin(this.pubPlugin);
        } catch (Exception e) {
            throw new ComponentStartException(e);
        }
        super.start();
    }

    @Override
    public void execute() throws Exception {
        super.execute();
        this.regPlugin.register(this.initialRC);
        this.traceMessage("registered ✓\n");
        if (this.testScenario != null) {
            this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI,
                    this.testScenario.getClockURI());
            this.traceMessage("clock initialized ✓\n");
            this.executeTestScenario(this.testScenario);
        }
    }


    public void publish(String channel, MessageI message) throws Exception {
        this.pubPlugin.publish(channel, message);
    }
    public void publish(String channel, ArrayList<MessageI> messages) throws Exception {
        this.pubPlugin.publish(channel, messages);
    }

    public void unregister() throws UnknownClientException {
        this.regPlugin.unregister();
    }
    public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
        this.regPlugin.modifyServiceClass(rc);
    }
}