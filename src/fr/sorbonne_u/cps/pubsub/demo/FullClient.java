package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.util.concurrent.Future;

public class FullClient extends AbstractComponent {

    private final ClientRegistrationPlugin regPlugin;
    private final ClientSubscriptionPlugin subPlugin;
    private final ClientPublicationPlugin pubPlugin;

    private final TestScenario testScenario;
    private final RegistrationCI.RegistrationClass initialRC;
    private final String uri;

    public FullClient(String uri, TestScenario ts,
                           RegistrationCI.RegistrationClass rc) throws Exception {
        super(uri, 1, 1);
        this.testScenario = ts;
        this.initialRC    = rc;
        this.uri=uri;
        this.regPlugin = new ClientRegistrationPlugin();
        this.regPlugin.setPluginURI(uri + "-reg");

        this.subPlugin = new ClientSubscriptionPlugin(
                regPlugin, this::onReceive);
        this.subPlugin.setPluginURI(uri + "-sub");

        this.regPlugin.setSubscriptionPlugin(this.subPlugin);

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

        if (this.testScenario != null) {
            this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI,
                    this.testScenario.getClockURI());
            this.executeTestScenario(this.testScenario);
        }
    }

    @Override
    public synchronized void finalise() throws Exception {
        this.pubPlugin.finalise();
        this.regPlugin.finalise();
        this.subPlugin.finalise();
        super.finalise();
    }

    @Override
    public synchronized void shutdown() throws ComponentShutdownException {
        try {
            this.pubPlugin.uninstall();
            this.regPlugin.uninstall();
            this.subPlugin.uninstall();
        } catch (Exception e) {
            throw new ComponentShutdownException(e);
        }
        super.shutdown();
    }

    public void onReceive(String channel, MessageI message) {
        String msg = "[" + uri + "] RECEIVED on " + channel
                + ": payload=" + (message != null ? message.getPayload() : "null")
                + "\n";
        System.out.println(msg);
        this.traceMessage(msg);
        this.logMessage(msg);
    }
    public void publish(String channel, MessageI message) throws Exception {
        this.pubPlugin.publish(channel, message);
    }
    public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
        this.regPlugin.modifyServiceClass(rc);
    }
    public void subscribe(String channel, MessageFilterI filter) throws Exception {
        this.subPlugin.subscribe(channel, filter);
    }
    public void unsubscribe(String channel) throws Exception {
        this.subPlugin.unsubscribe(channel);
    }
    public Future<MessageI> getNextMessage(String channel){
        return this.subPlugin.getNextMessage(channel);
    }

    public MessageI waitForNextMessage(String channel){
        return this.subPlugin.waitForNextMessage(channel);
    }
    public MessageI waitForNextMessage(String channel, Duration d) {
        return this.subPlugin.waitForNextMessage(channel, d);
    }
}
