package fr.sorbonne_u.cps.pubsub.base.components;


import fr.sorbonne_u.cps.pubsub.base.util.PortCleanupUtil;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * Composant client de démonstration jouant le rôle de <strong>privilégié</strong> :
 * publie ET gère ses propres canaux privilégiés (création / destruction /
 * modification d'autorisations). Compose deux plugins :
 * <ul>
 * <li>{@link ClientRegistrationPlugin} — registration + port {@code ReceivingCI} ;</li>
 * <li>{@link ClientPrivilegedPlugin} — port sortant
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI}
 * (qui inclut publication ; cf. CDC §3.6).</li>
 * </ul>
 *
 * <p>
 * <strong>Convention constructeur</strong> : {@code (uri, brokerReflectionURI,
 * scenario, registrationClass)}. La classe initiale est généralement FREE puis
 * upgradée à STANDARD ou PREMIUM via {@link #modifyServiceClass} avant
 * d'invoquer {@link #createChannel}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PrivilegedClient extends AbstractComponent {

 // -------------------------------------------------------------------------
 // PrivilegedClient — registration + privileged (extends publication)
 // -------------------------------------------------------------------------



 private final ClientRegistrationPlugin regPlugin;
 private final ClientPrivilegedPlugin privPlugin;
 private final TestScenario testScenario;
 private final RegistrationCI.RegistrationClass initialRC;
 private final String brokerReflectionURI;

 /**
 * Crée un client privilégié.
 *
 * @param uri URI de port de réflexion / identifiant.
 * @param brokerReflectionURI URI de réflexion du broker cible.
 * @param ts scénario temporisé optionnel.
 * @param rc classe initiale d'enregistrement.
 * @throws Exception si la construction échoue.
 */
 protected PrivilegedClient(String uri, String brokerReflectionURI,
 TestScenario ts,
 RegistrationCI.RegistrationClass rc) throws Exception {
 super(uri, 1, 1);
 this.testScenario = ts;
 this.initialRC = rc;
 this.brokerReflectionURI = brokerReflectionURI;

 this.regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
 this.regPlugin.setPluginURI(uri + "-reg");

 this.privPlugin = new ClientPrivilegedPlugin(regPlugin, brokerReflectionURI);
 this.privPlugin.setPluginURI(uri + "-priv");

 this.toggleTracing();
 this.getTracer().setTitle(uri);
 }

 /**
 * Installe les plugins {@code reg} + {@code priv} avant {@link #execute()}.
 *
 * @throws ComponentStartException si un plugin ne peut être installé.
 */
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

 /**
 * Enregistre le client à la classe initiale, initialise l'horloge
 * accélérée et exécute le scénario si ce composant y apparaît.
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
 * Modifie la classe d'enregistrement du client (FREE ↔ STANDARD ↔ PREMIUM).
 *
 * @param rc nouvelle classe d'enregistrement.
 * @throws Exception si l'opération échoue côté broker.
 */
 public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
 this.regPlugin.modifyServiceClass(rc);
 }

 /**
 * Crée un canal privilégié dont l'accès est restreint aux URIs de port de
 * réception correspondant au regex {@code authorisedUsers}.
 *
 * @param channel nom du canal à créer (arbitraire, hors patron
 * {@code channel\d+}).
 * @param authorisedUsers regex appliqué sur les URIs de port de réception
 * des clients ; cf. CDC §3.6.
 * @throws Exception si la création échoue (quota, autorisation, conflit…).
 */
 public void createChannel(String channel, String authorisedUsers) throws Exception {
 this.privPlugin.createChannel(channel, authorisedUsers);
 }

 /**
 * Modifie la liste (regex) des utilisateurs autorisés sur un canal
 * privilégié déjà créé par ce client.
 *
 * @param channel nom du canal.
 * @param authorisedUsers nouveau regex d'utilisateurs autorisés.
 * @throws UnknownClientException si le client n'est plus connu côté broker.
 * @throws UnauthorisedClientException si ce client n'est pas le créateur du canal.
 * @throws UnknownChannelException si le canal n'existe pas.
 */
 public void modifyAuthorisedUsers(String channel, String authorisedUsers) throws UnknownClientException, UnauthorisedClientException, UnknownChannelException {
 this.privPlugin.modifyAuthorisedUsers(channel, authorisedUsers);
 }

 /**
 * Détruit un canal privilégié créé par ce client.
 *
 * @param channel nom du canal.
 * @throws UnknownClientException si le client n'est plus connu.
 * @throws UnauthorisedClientException si ce client n'est pas le créateur.
 * @throws UnknownChannelException si le canal n'existe pas.
 */
 public void destroyChannel(String channel) throws UnknownClientException, UnauthorisedClientException, UnknownChannelException {
 this.privPlugin.destroyChannel(channel);
 }

 /**
 * Publie un message sur le canal donné via le port {@code PrivilegedClientCI}.
 *
 * @param channel canal cible (free ou privilégié).
 * @param message message à publier.
 * @throws Exception si la publication échoue côté broker.
 */
 public void publish(String channel, MessageI message) throws Exception {
 this.privPlugin.publish(channel, message);
 }

 /**
 * Hook d'arrêt BCM : déconnecte d'abord les ports sortants encore actifs.
 *
 * @throws ComponentShutdownException si le shutdown parent échoue.
 */
 @Override
 public synchronized void shutdown() throws ComponentShutdownException {
 PortCleanupUtil.disconnectStillConnectedOutboundPorts(this);
 super.shutdown();
 }
}