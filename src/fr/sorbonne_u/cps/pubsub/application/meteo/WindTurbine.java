package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.base.components.SubscriberClient;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Composant "Éolienne" (CDC §3.4) modélisé comme un client pub/sub
 * <strong>souscripteur</strong> spécialisé.
 *
 * <p>
 * <strong>Choix de conception</strong> : <em>WindTurbine</em> hérite de
 * {@link SubscriberClient}, le composant client générique qui possède déjà
 * les greffons {@link fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin}
 * et {@link fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin}. On
 * évite ainsi de redupliquer le câblage des plugins (installation,
 * connexion, déconnexion, propre {@code start}/{@code shutdown}) déjà
 * présent dans la classe parente. Le composant ne contient plus que la
 * logique métier (filtrage géographique, dispatch wind/alert, mode
 * sécurité). Cette factorisation s'inscrit dans le principe DRY.
 * </p>
 *
 * <p>
 * Le composant reçoit des observations de vent ({@link WindDataI}) et des
 * alertes ({@link MeteoAlertI}). Les observations de vent sont filtrées par
 * distance côté courtier via
 * {@link MeteoFilters#windWithinDistance(PositionI, double)}, ce qui
 * <em>déporte</em> le filtrage géographique vers le système pub/sub plutôt
 * que de transporter inutilement les messages éloignés.
 * </p>
 *
 * <p>
 * Sur réception d'une alerte concernant la région de l'éolienne, le composant
 * bascule en mode "safety" lorsque le niveau atteint le seuil configuré, et
 * revient à la normale sur niveau {@link MeteoAlertI.Level#GREEN}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class WindTurbine extends SubscriberClient
{
	// -------------------------------------------------------------------------
	// Champs métier (l'enregistrement et le greffon de souscription sont
	// hérités de SubscriberClient ; cf. choix de conception en tête de classe).
	// -------------------------------------------------------------------------

	private final String turbineId;
	private final Position2D position;
	private final double maxDistance;
	private final long recentWindowMillis;
	private final MeteoAlertI.Level threshold;
	private volatile boolean safetyMode;
	private final boolean autoSubscribeDefaults;

	// Canaux mémorisés à la souscription pour permettre le dispatch par
	// canal dans onReceive(...) — évite tout instanceof sur le payload.
	private volatile String windChannel;
	private volatile String alertChannel;

	private static class TimedWind
	{
		final long ts;
		final WindDataI wind;

		TimedWind(long ts, WindDataI wind)
		{
			this.ts = ts;
			this.wind = wind;
		}
	}

	private final List<TimedWind> windBuffer = new ArrayList<>();

	// -------------------------------------------------------------------------
	// Constructeurs
	// -------------------------------------------------------------------------

	/**
	 * Constructeur principal. La signature respecte la convention
	 * {@code (uri, scenario?, turbineId, position, maxDistance,
	 * recentWindowMillis, threshold, brokerReflectionURI)} utilisée par les
	 * démos centralisées.
	 *
	 * @param uri					URI de port de réflexion / identifiant participant.
	 * @param testScenario			scénario temporisé optionnel ({@code null} = aucun).
	 * @param turbineId				identifiant métier de l'éolienne.
	 * @param position				position géographique.
	 * @param maxDistance			rayon maximal accepté pour les observations de vent.
	 * @param recentWindowMillis	fenêtre glissante (ms) pour pondérer les observations.
	 * @param threshold				seuil d'alerte au-delà duquel passer en mode sécurité.
	 * @param brokerReflectionURI	URI de réflexion du courtier cible.
	 * @throws Exception			si l'initialisation BCM ou des plugins échoue.
	 */
	protected WindTurbine(
		String uri,
		TestScenario testScenario,
		String turbineId,
		PositionI position,
		double maxDistance,
		long recentWindowMillis,
		MeteoAlertI.Level threshold,
		String brokerReflectionURI) throws Exception
	{
		super(uri, brokerReflectionURI, testScenario, RegistrationCI.RegistrationClass.FREE);
		if (turbineId == null || turbineId.isEmpty()) {
			throw new IllegalArgumentException("turbineId cannot be null/empty");
		}
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		if (!(position instanceof Position2D)) {
			throw new IllegalArgumentException(
				"WindTurbine requires a Position2D (got "
				+ position.getClass().getName() + ")");
		}
		this.turbineId = turbineId;
		this.position = (Position2D) position;
		this.maxDistance = maxDistance;
		this.recentWindowMillis = recentWindowMillis;
		this.threshold = threshold;
		this.safetyMode = false;
		this.autoSubscribeDefaults = (testScenario == null);
	}

	/**
	 * Surcharge sans scénario, pour les déploiements répartis (cf.
	 * {@link fr.sorbonne_u.cps.pubsub.demo.DemoTimedDistributed}).
	 *
	 * @param uri					URI de port de réflexion.
	 * @param turbineId				identifiant métier.
	 * @param position				position géographique.
	 * @param maxDistance			rayon maximal accepté.
	 * @param recentWindowMillis	fenêtre glissante (ms).
	 * @param threshold				seuil d'alerte.
	 * @param brokerReflectionURI	URI de réflexion du courtier.
	 * @throws Exception			si l'initialisation échoue.
	 */
	protected WindTurbine(
		String uri,
		String turbineId,
		PositionI position,
		double maxDistance,
		long recentWindowMillis,
		MeteoAlertI.Level threshold,
		String brokerReflectionURI) throws Exception
	{
		this(uri, null, turbineId, position, maxDistance,
				recentWindowMillis, threshold, brokerReflectionURI);
	}

	// -------------------------------------------------------------------------
	// Cycle de vie BCM (start/finalise/shutdown sont hérités de
	// SubscriberClient ; on n'a qu'à enrichir execute() pour ajouter les
	// souscriptions par défaut quand aucun scénario n'est fourni).
	// -------------------------------------------------------------------------

	/**
	 * Enregistre le composant (via le parent) puis, en l'absence de
	 * scénario, souscrit aux canaux par défaut afin de rester utilisable
	 * dans les déploiements simples (sans {@code TestScenario}).
	 *
	 * @throws Exception si l'enregistrement, l'horloge ou la souscription
	 *					 échoue.
	 */
	@Override
	public void execute() throws Exception
	{
		super.execute();
		if (this.autoSubscribeDefaults) {
			subscribeToWindAndAlerts(MeteoProperties.DEFAULT_WIND_CHANNEL,
									MeteoProperties.DEFAULT_ALERT_CHANNEL);
		}
	}

	// -------------------------------------------------------------------------
	// API métier
	// -------------------------------------------------------------------------

	/** @return la position géographique de l'éolienne. */
	public PositionI getPosition()
	{
		return position;
	}

	/**
	 * Souscrit aux observations de vent (filtrées par distance) et à toutes
	 * les alertes météo, en réutilisant la méthode {@code subscribe} héritée
	 * du parent (qui passe par le greffon de souscription).
	 *
	 * @param windChannel	canal des observations de vent.
	 * @param alertChannel	canal des alertes.
	 * @throws Exception	si l'enregistrement des souscriptions auprès du
	 *						courtier échoue.
	 */
	public void subscribeToWindAndAlerts(String windChannel, String alertChannel) throws Exception
	{
		MessageFilterI windFilter = MeteoFilters.windWithinDistance(this.position, this.maxDistance);
		MessageFilterI alertFilter = MeteoFilters.anyAlert();

		this.windChannel  = windChannel;
		this.alertChannel = alertChannel;

		this.subscribe(windChannel, windFilter);
		this.subscribe(alertChannel, alertFilter);
		this.traceMessage("WindTurbine[" + turbineId + "] subscribed to "
				+ windChannel + " and " + alertChannel + "\n");
	}

	/**
	 * Hook de livraison appelé par le greffon de souscription pour chaque
	 * message reçu sur l'un des canaux souscrits. <em>Override</em> du
	 * comportement neutre du parent : le dispatch se fait par <strong>nom
	 * de canal</strong> (et non par {@code instanceof} sur le payload),
	 * ce qui découple la dispatch du type concret de message.
	 *
	 * @param channel canal d'origine.
	 * @param message message reçu.
	 */
	@Override
	public void onReceive(String channel, MessageI message)
	{
		try {
			if (channel.equals(this.windChannel)) {
				onWind((WindDataI) message.getPayload(),
					   message.getTimeStamp().toEpochMilli());
			} else if (channel.equals(this.alertChannel)) {
				onAlert((MeteoAlertI) message.getPayload());
			} else {
				this.traceMessage(
					"WindTurbine[" + turbineId + "] received message on "
						+ "unsubscribed channel " + channel + "\n");
			}
		} catch (Exception e) {
			this.logMessage("WindTurbine[" + turbineId + "] receive failed: "
					+ e.getMessage() + "\n");
		}
	}

	private void onWind(WindDataI wind, long messageTs)
	{
		long now = Instant.now().toEpochMilli();
		windBuffer.removeIf(tw -> now - tw.ts > recentWindowMillis);

		double d = distanceFromTurbine(wind.getPosition());
		windBuffer.add(new TimedWind(messageTs, wind));
		this.traceMessage("WindTurbine[" + turbineId + "] accept wind d=" + d
				+ ": " + wind + "\n");

		double sumX = 0.0;
		double sumY = 0.0;
		for (TimedWind tw : windBuffer) {
			double dist = distanceFromTurbine(tw.wind.getPosition());
			double w = 1.0 / (dist + 1e-6);
			sumX += w * tw.wind.xComponent();
			sumY += w * tw.wind.yComponent();
		}
		double orientX = -sumX;
		double orientY = -sumY;
		this.traceMessage(
			"WindTurbine[" + turbineId + "] orientation vector = ("
				+ orientX + ", " + orientY + ") safetyMode=" + safetyMode + "\n");
	}

	private void onAlert(MeteoAlertI alert)
	{
		boolean concerned = false;
		for (RegionI r : alert.getRegions()) {
			if (r.in(this.position)) {
				concerned = true;
				break;
			}
		}
		if (!concerned) {
			this.traceMessage("WindTurbine[" + turbineId
					+ "] ignore alert (not concerned): " + alert + "\n");
			return;
		}

		MeteoAlertI.LevelI rawLevel = alert.getLevel();
		MeteoAlertI.Level level = (rawLevel instanceof MeteoAlertI.Level)
			? (MeteoAlertI.Level) rawLevel
			: null;

		this.traceMessage("WindTurbine[" + turbineId + "] received alert: "
				+ alert + "\n");

		if (level == MeteoAlertI.Level.GREEN) {
			safetyMode = false;
			this.traceMessage("WindTurbine[" + turbineId
					+ "] RETURN NORMAL (GREEN)\n");
			return;
		}

		if (level != null && level.ordinal() >= threshold.ordinal()) {
			safetyMode = true;
			this.traceMessage(
				"WindTurbine[" + turbineId + "] ENTER SAFETY MODE (level="
					+ level + ", threshold=" + threshold + ")\n");
		}
	}

	private double distanceFromTurbine(PositionI other)
	{
		// La géométrie est encapsulée dans Position2D (CDC §3.4) ; le champ
		// {@code position} étant typé Position2D (validé au constructeur),
		// aucune vérification de type n'est nécessaire ici.
		return this.position.distanceTo(other);
	}
}
