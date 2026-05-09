package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

public class SubscriberClient extends AbstractComponent {

    // -------------------------------------------------------------------------
    // SubscriberClient — registration + subscription only
    // -------------------------------------------------------------------------

    private final ClientRegistrationPlugin regPlugin;
    private final ClientSubscriptionPlugin subPlugin;
    private final TestScenario testScenario;
    private final RegistrationCI.RegistrationClass initialRC;
    private final String uri;

    protected SubscriberClient(String uri, TestScenario ts,
                            RegistrationCI.RegistrationClass rc) throws Exception {
        super(uri, 1, 1); // 1 schedulable thread for executeTestScenario
        this.testScenario = ts;
        this.initialRC    = rc;
        this.uri=uri;
        this.regPlugin = new ClientRegistrationPlugin();
        this.regPlugin.setPluginURI(uri + "-reg");

        this.subPlugin = new ClientSubscriptionPlugin(
                regPlugin, this::onReceive);
        this.subPlugin.setPluginURI(uri + "-sub");

        this.regPlugin.setSubscriptionPlugin(this.subPlugin);

        this.toggleTracing();
        this.getTracer().setTitle(uri);
    }
    // Constructeur sans scénario pour le déploiement réparti
    protected SubscriberClient(String uri,
                               RegistrationCI.RegistrationClass rc) throws Exception {
        this(uri, null, rc);
    }

    public void onReceive(String channel, MessageI message) {
        String msg = "[" + uri + "] RECEIVED on " + channel
                + ": payload=" + (message != null ? message.getPayload() : "null")
                + "\n";
        System.out.println(msg);
        this.traceMessage(msg);
        this.logMessage(msg);
    }
    @Override
    public synchronized void start() throws ComponentStartException {
        try {

            this.installPlugin(this.regPlugin);
            this.installPlugin(this.subPlugin);
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

    // Domain actions called from scenario steps
    public void subscribe(String channel, MessageFilterI filter) throws Exception {
        this.subPlugin.subscribe(channel, filter);
    }
    public void unregister() throws UnknownClientException {
        this.regPlugin.unregister();
    }
    public void unsubscribe(String channel) throws Exception {
        this.subPlugin.unsubscribe(channel);
    }
    public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
        this.regPlugin.modifyServiceClass(rc);
    }
}