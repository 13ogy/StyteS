package fr.sorbonne_u.cps.pubsub.base.components;


import fr.sorbonne_u.cps.pubsub.base.util.PortCleanupUtil;
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

/**
 * Composant client de démonstration jouant le rôle de <strong>souscripteur
 * pur</strong> : enregistré au broker (par défaut FREE) puis souscrit à des
 * canaux. Compose deux plugins :
 * </p>
 * <ul>
 *   <li>{@link ClientRegistrationPlugin} — possède le port {@link
 *       fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI} entrant et le port
 *       {@link fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI} sortant ;</li>
 *   <li>{@link ClientSubscriptionPlugin} — méthodes {@code subscribe} /
 *       {@code unsubscribe} ; reçoit les messages via le hook
 *       {@link #onReceive(String, MessageI)}.</li>
 * </ul>
 *
 * <p>
 * Les démos centralisées passent en plus un {@link TestScenario} ; en
 * déploiement distribué (cf. {@link Demo3JVMs}, {@link DemoFinal}) le
 * scénario peut être {@code null}.
 * </p>
 *
 * <p>
 * <strong>Convention constructeur</strong> : {@code (uri, brokerReflectionURI)}
 * où {@code uri} sert à la fois comme URI de port de réflexion BCM et comme
 * identifiant participant dans les {@link TestScenario}. Les surcharges 4-arg
 * (avec scénario + classe initiale) sont utilisées par les démos.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class SubscriberClient extends AbstractComponent {

    // -------------------------------------------------------------------------
    // SubscriberClient — registration + subscription only
    // -------------------------------------------------------------------------

    private final ClientRegistrationPlugin regPlugin;
    private final ClientSubscriptionPlugin subPlugin;
    private final TestScenario testScenario;
    private final RegistrationCI.RegistrationClass initialRC;
    private final String uri;
    private final String brokerReflectionURI;

    /**
     * Construit un souscripteur attaché à un broker, avec un scénario
     * temporisé optionnel.
     *
     * @param uri                 URI de port de réflexion / identifiant participant.
     * @param brokerReflectionURI URI de réflexion du broker cible.
     * @param ts                  scénario temporisé ({@code null} = aucun).
     * @param rc                  classe initiale d'enregistrement (FREE/STANDARD/PREMIUM).
     * @throws Exception si la construction du composant ou des plugins échoue.
     */
    protected SubscriberClient(String uri, String brokerReflectionURI,
                            TestScenario ts,
                            RegistrationCI.RegistrationClass rc) throws Exception {
        super(uri, 1, 1); // 1 schedulable thread for executeTestScenario
        this.testScenario = ts;
        this.initialRC    = rc;
        this.uri=uri;
        this.brokerReflectionURI = brokerReflectionURI;
        this.regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
        this.regPlugin.setPluginURI(uri + "-reg");

        this.subPlugin = new ClientSubscriptionPlugin(
                regPlugin, this::onReceive);
        this.subPlugin.setPluginURI(uri + "-sub");

        this.regPlugin.setSubscriptionPlugin(this.subPlugin);

        this.toggleTracing();
        this.getTracer().setTitle(uri);
    }
    /**
     * Constructeur sans scénario, pour les déploiements répartis (cf.
     * {@link DemoDistributed} / {@link Demo3JVMs}).
     *
     * @param uri                 URI de port de réflexion / identifiant.
     * @param brokerReflectionURI URI de réflexion du broker cible.
     * @param rc                  classe initiale d'enregistrement.
     * @throws Exception si la construction échoue.
     */
    protected SubscriberClient(String uri, String brokerReflectionURI,
                               RegistrationCI.RegistrationClass rc) throws Exception {
        this(uri, brokerReflectionURI, null, rc);
    }

    /**
     * Hook de réception (passé en {@code MessageDeliveryHandler} au
     * {@link ClientSubscriptionPlugin}). Trace + log le message reçu.
     *
     * @param channel canal sur lequel le message est délivré.
     * @param message message livré ({@code null} possible).
     */
    public void onReceive(String channel, MessageI message) {
        String msg = "[" + uri + "] RECEIVED on " + channel
                + ": payload=" + (message != null ? message.getPayload() : "null")
                + "\n";
        System.out.println(msg);
        this.traceMessage(msg);
        this.logMessage(msg);
    }
    /**
     * Installe les plugins {@code reg} + {@code sub} avant {@link #execute()}.
     *
     * @throws ComponentStartException si un plugin ne peut être installé.
     */
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

    /**
     * Enregistre le client à la classe initiale, initialise l'horloge
     * accélérée puis exécute le scénario si ce composant y apparaît.
     *
     * @throws Exception en cas d'échec d'enregistrement, d'horloge ou de scénario.
     */
    @Override
    public void execute() throws Exception {
        super.execute();
        this.regPlugin.register(this.initialRC);
        this.traceMessage("registered ✓\n");
        if (this.testScenario != null
            && this.testScenario.entityAppearsIn(this.getReflectionInboundPortURI())) {
            this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI,
                    this.testScenario.getClockURI());
            this.traceMessage("clock initialized ✓\n");
            this.executeTestScenario(this.testScenario);
        }
    }

    /**
     * Souscrit au canal donné avec le filtre fourni (action métier appelée
     * depuis les étapes du scénario).
     *
     * @param channel nom du canal (free ou privilégié).
     * @param filter  filtre de message à appliquer côté broker.
     * @throws Exception si la souscription échoue côté broker.
     */
    public void subscribe(String channel, MessageFilterI filter) throws Exception {
        this.subPlugin.subscribe(channel, filter);
    }

    /**
     * Désenregistre le client auprès du broker.
     *
     * @throws UnknownClientException si le client n'est plus connu côté broker.
     */
    public void unregister() throws UnknownClientException {
        this.regPlugin.unregister();
    }

    /**
     * Annule la souscription sur un canal donné.
     *
     * @param channel nom du canal.
     * @throws Exception si la désinscription échoue côté broker.
     */
    public void unsubscribe(String channel) throws Exception {
        this.subPlugin.unsubscribe(channel);
    }

    /**
     * Modifie la classe d'enregistrement (FREE ↔ STANDARD ↔ PREMIUM).
     *
     * @param rc nouvelle classe d'enregistrement.
     * @throws Exception si l'opération échoue côté broker.
     */
    public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
        this.regPlugin.modifyServiceClass(rc);
    }

    /**
     * Hook d'arrêt BCM : déconnecte d'abord les ports sortants encore actifs
     * (cf. {@link PortCleanupUtil}) avant de propager au parent.
     *
     * @throws ComponentShutdownException si le shutdown parent échoue.
     */
    @Override
    public synchronized void shutdown() throws ComponentShutdownException {
        PortCleanupUtil.disconnectStillConnectedOutboundPorts(this);
        super.shutdown();
    }
}