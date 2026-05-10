package fr.sorbonne_u.cps.pubsub.base.components;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.cps.pubsub.base.connectors.PublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.RegistrationConnector;

import fr.sorbonne_u.cps.pubsub.base.connectors.PrivilegedConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;


import fr.sorbonne_u.components.AbstractComponent;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Pub/sub client component.
 *
 * <p>
 * The client owns:
 * </p>
 * <ul>
 *   <li>an inbound port offering {@code ReceivingCI} to receive deliveries from the broker;</li>
 *   <li>an outbound port requiring {@code RegistrationCI} to register and manage subscriptions;</li>
 *   <li>an outbound port requiring {@code PublishingCI} to publish messages;</li>
 *   <li>an outbound port requiring {@code PrivilegedClientCI} to manage privileged channels (STANDARD/PREMIUM).</li>
 * </ul>
 *
 * <p>
 * The method {@link #register(fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass)} establishes the
 * required connections to the broker.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class Client extends AbstractComponent {

	private ClientInboundPort receptionPortIN;

	/**
	 * URI du port inbound {@link fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI}
	 * de ce client. Sert d'identité unique côté broker.
	 */
	public String getReceptionPortURI()
	{
		try {
			return this.receptionPortIN.getPortURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private ClientPublishingOutboundPort publishingPortOUT;
	private ClientRegistrationOutboundPort registrationPortOUT;
	private ClientPrivilegedOutboundPort privilegedPortOUT;

	private RegistrationClass rcCurrent;

	private boolean registered;

	/** Reflection inbound port URI of the broker this client connects to.
	 *  May be {@code null} if the legacy no-arg constructor was used; in
	 *  that case {@link #start()} will fail with a clear message. */
	private final String brokerReflectionURI;

	/**
	 * @deprecated use {@link #Client(int, int, String)} instead.
	 *             Without an explicit broker reflection URI the client
	 *             cannot derive the broker registration port URI in a
	 *             multi-broker environment (Phase C.3).
	 */
	@Deprecated
	public Client(int nbThreads, int nbSchedulableThreads )throws Exception {
		this(nbThreads, nbSchedulableThreads, null);
	}

	/**
	 * Preferred constructor (Phase C.3): the client knows which broker
	 * it must contact, identified by that broker's reflection inbound
	 * port URI. The client will derive the registration port URI itself.
	 */
	public Client(int nbThreads, int nbSchedulableThreads,
				  String brokerReflectionURI) throws Exception {
		super(nbThreads, nbSchedulableThreads);
		this.brokerReflectionURI = brokerReflectionURI;
		this.registered = false;
		this.receptionPortIN = new ClientInboundPort(this);
		this.receptionPortIN.publishPort();

		this.publishingPortOUT = new ClientPublishingOutboundPort(this);
		this.publishingPortOUT.publishPort();

		this.registrationPortOUT = new ClientRegistrationOutboundPort(this);
		this.registrationPortOUT.publishPort();

		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(this);
		this.privilegedPortOUT.publishPort();

	}
	// Component life cycle
	@Override
	public void start() throws ComponentStartException {
		super.start();
		if (this.brokerReflectionURI == null || this.brokerReflectionURI.isEmpty()) {
			throw new ComponentStartException(
				"Client requires a broker reflection inbound port URI; "
				+ "use Client(int,int,String) instead of the deprecated "
				+ "no-arg variant (Phase C.3).");
		}
		try {
			this.doPortConnection(
					this.registrationPortOUT.getPortURI(),
					Broker.registrationPortURIFor(this.brokerReflectionURI),
					RegistrationConnector.class.getCanonicalName());
		}catch (Exception e) { throw new ComponentStartException(e);}
	}
	@Override
	public void execute() throws Exception {
		super.execute();
	}

	@Override
	public void finalise() throws Exception {
		if (this.registrationPortOUT.connected())
			this.doPortDisconnection(this.registrationPortOUT.getPortURI());
		if (this.publishingPortOUT.connected())
			this.doPortDisconnection(this.publishingPortOUT.getPortURI());
		if (this.privilegedPortOUT.connected())
			this.doPortDisconnection(this.privilegedPortOUT.getPortURI());
		super.finalise();
	}

	@Override
	public void shutdown() throws ComponentShutdownException {
		try {
			this.receptionPortIN.unpublishPort();
			this.publishingPortOUT.unpublishPort();
			this.registrationPortOUT.unpublishPort();
			this.privilegedPortOUT.unpublishPort();
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}
		super.shutdown();
	}
	
	/**
	 * Enregistre ce client auprès du broker dans la classe de service
	 * {@code rc}. Établit aussi la connexion sortante adaptée
	 * (publishing pour FREE, privileged sinon). Idempotent : un second
	 * appel renvoie immédiatement.
	 *
	 * @throws Exception erreurs de connexion ou métier remontées par le broker.
	 */
	public void register(RegistrationClass rc) throws Exception {
		if (this.registered) {
			return;
		}

		
		this.logMessage("Registering client.") ;

		// Registering
		String portINURI = registrationPortOUT.register(receptionPortIN.getPortURI(), rc);

		if (rc == RegistrationClass.FREE) {
			// FREE clients only get publishing
			this.doPortConnection(
					this.publishingPortOUT.getPortURI(),
					portINURI,
					PublishingConnector.class.getCanonicalName());
		} else {
			// STANDARD/PREMIUM get privileged (which also covers publishing)
			this.doPortConnection(
					this.privilegedPortOUT.getPortURI(),
					portINURI,
					PrivilegedConnector.class.getCanonicalName());
		}
		this.rcCurrent = rc;

		this.logMessage("Client registered");
		this.registered = true;
	}
	
	/**
	 * Demande au broker un changement de classe de service. Reconfigure
	 * localement la paire de ports outbound utilisée pour publier (publishing
	 * vs privileged) en fonction de la nouvelle classe.
	 *
	 * @throws AlreadyRegisteredException si {@code rc} est identique à la classe courante.
	 */
	public void modifyServiceClass(RegistrationClass rc) throws Exception {
		
		this.logMessage("Modifying registration class");
		
		if (rc == this.rcCurrent) {
			throw new AlreadyRegisteredException();
		}

		String portINURI =
			registrationPortOUT.modifyServiceClass(receptionPortIN.getPortURI(), rc);

		if (this.rcCurrent == RegistrationClass.FREE) {
			this.doPortDisconnection(this.publishingPortOUT.getPortURI());
		} else {
			this.doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}

		if (rc == RegistrationClass.FREE) {
			this.doPortConnection(
					this.publishingPortOUT.getPortURI(),
					portINURI,
					PublishingConnector.class.getCanonicalName());
		} else {
			this.doPortConnection(
					this.privilegedPortOUT.getPortURI(),
					portINURI,
					PrivilegedConnector.class.getCanonicalName());
		}
		this.rcCurrent = rc;
		this.logMessage("Client service class modified (audit1 minimal).");
		
	}

	/** Souscrit à {@code channel} avec le filtre {@code filter}. */
	public void subscribe(String channel, MessageFilterI filter) throws Exception
	{
		this.registrationPortOUT.subscribe(
			this.receptionPortIN.getPortURI(),
			channel,
			filter);
	}

	/** Publie {@code message} sur {@code channel} (livraison asynchrone). */
	public void publish(String channel, MessageI message) throws Exception
	{
		this.publishingPortOUT.publish(
			this.receptionPortIN.getPortURI(),
			channel,
			message);
	}

	// -------------------------------------------------------------------------
	// Privileged channel management API
	// -------------------------------------------------------------------------

	/** @return {@code true} ssi ce client est propriétaire de {@code channel}. */
	public boolean hasCreatedChannel(String channel) throws Exception
	{
		return this.privilegedPortOUT.hasCreatedChannel(this.receptionPortIN.getPortURI(), channel);
	}

	/** @return {@code true} ssi le quota de canaux privilégiés est atteint. */
	public boolean channelQuotaReached() throws Exception
	{
		return this.privilegedPortOUT.channelQuotaReached(this.receptionPortIN.getPortURI());
	}

	/** Crée un canal privilégié possédé par ce client. */
	public void createChannel(String channel, String authorisedUsersRegex) throws Exception
	{
		this.privilegedPortOUT.createChannel(this.receptionPortIN.getPortURI(), channel, authorisedUsersRegex);
	}

	/** Modifie la regex {@code authorisedUsers} d'un canal privilégié possédé. */
	public void modifyAuthorisedUsers(String channel, String authorisedUsersRegex) throws Exception
	{
		this.privilegedPortOUT.modifyAuthorisedUsers(this.receptionPortIN.getPortURI(), channel, authorisedUsersRegex);
	}

	/** Destruction asynchrone d'un canal privilégié possédé. */
	public void destroyChannel(String channel) throws Exception
	{
		this.privilegedPortOUT.destroyChannel(this.receptionPortIN.getPortURI(), channel);
	}

	/** Destruction synchrone d'un canal privilégié possédé. */
	public void destroyChannelNow(String channel) throws Exception
	{
		this.privilegedPortOUT.destroyChannelNow(this.receptionPortIN.getPortURI(), channel);
	}


	/**
	 * Callback de livraison appelé par {@code ClientInboundPort} pour
	 * chaque message reçu (mode unitaire). Implémentation par défaut :
	 * trace pour audit.
	 */
	public void receive(String channel, MessageI message)
	{
		this.traceMessage(
			"Client " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " properties=" + java.util.Arrays.toString(message.getProperties())
				+ " timestamp=" + message.getTimeStamp() + "\n");
	}

	/**
	 * Callback de livraison en mode lot : par défaut, délègue à la version
	 * unitaire pour chaque message non nul.
	 */
	public void receive(String channel, MessageI[] messages)
	{
		this.traceMessage(
			"Client " + this.getReflectionInboundPortURI()
				+ " received batch(" + (messages == null ? 0 : messages.length)
				+ ") on " + channel + "\n");
		if (messages != null) {
			for (MessageI m : messages) {
				receive(channel, m);
			}
		}
	}
	
}
