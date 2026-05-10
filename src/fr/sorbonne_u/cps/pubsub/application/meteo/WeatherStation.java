package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.base.components.PublisherClient;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;

/**
 * Composant "Station météo" (CDC §3.4) modélisé comme un client pub/sub
 * <strong>publieur</strong> spécialisé.
 *
 * <p>
 * <strong>Choix de conception</strong> : <em>WeatherStation</em> hérite de
 * {@link PublisherClient}, qui fournit déjà les greffons
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin} +
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin} ainsi que
 * tout le câblage de cycle de vie. Le composant n'embarque que sa logique
 * métier (fabrication des messages vent + identifiants/position de la
 * station). Cela évite la duplication du wiring BCM4Java et garantit que
 * tous les publieurs FREE partagent le même contrat.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class WeatherStation extends PublisherClient
{
	// -------------------------------------------------------------------------
	// Champs métier (l'enregistrement + le greffon de publication sont
	// hérités de PublisherClient).
	// -------------------------------------------------------------------------

	private final String stationId;
	private final PositionI position;

	// -------------------------------------------------------------------------
	// Constructeurs
	// -------------------------------------------------------------------------

	/**
	 * Constructeur principal utilisé par les démos centralisées.
	 *
	 * @param uri					URI de port de réflexion / identifiant participant.
	 * @param testScenario			scénario temporisé optionnel ({@code null} = aucun).
	 * @param stationId				identifiant métier de la station.
	 * @param position				position géographique.
	 * @param brokerReflectionURI	URI de réflexion du courtier cible.
	 * @throws Exception			si l'initialisation BCM ou des plugins échoue.
	 */
	protected WeatherStation(
		String uri,
		TestScenario testScenario,
		String stationId,
		PositionI position,
		String brokerReflectionURI) throws Exception
	{
		super(uri, brokerReflectionURI, testScenario,
				RegistrationCI.RegistrationClass.FREE);
		if (stationId == null || stationId.isEmpty()) {
			throw new IllegalArgumentException("stationId cannot be null/empty");
		}
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.stationId = stationId;
		this.position = position;
	}

	/**
	 * Surcharge sans scénario (déploiements répartis).
	 *
	 * @param uri					URI de port de réflexion.
	 * @param stationId				identifiant métier de la station.
	 * @param position				position géographique.
	 * @param brokerReflectionURI	URI de réflexion du courtier cible.
	 * @throws Exception			si l'initialisation échoue.
	 */
	protected WeatherStation(
		String uri,
		String stationId,
		PositionI position,
		String brokerReflectionURI) throws Exception
	{
		this(uri, null, stationId, position, brokerReflectionURI);
	}

	/**
	 * Surcharge legacy (URI = stationId).
	 *
	 * @param uri		URI de port de réflexion BCM = identifiant métier.
	 * @param position	position géographique.
	 * @throws Exception si l'initialisation échoue.
	 */
	protected WeatherStation(String uri, PositionI position) throws Exception
	{
		this(uri, null, uri, position, null);
	}

	// -------------------------------------------------------------------------
	// API métier
	// -------------------------------------------------------------------------

	/** @return la position géographique de la station. */
	public PositionI getPosition()
	{
		return position;
	}

	/**
	 * Publie une observation de vent sur le canal indiqué. Le message est
	 * construit via {@link WindMessageFactory#build(String, WindDataI)}
	 * (CDC §3.5 — convention de propriétés des messages vent).
	 *
	 * @param windChannel	canal de publication (typiquement
	 *						{@link MeteoProperties#DEFAULT_WIND_CHANNEL}).
	 * @param wind			observation de vent à publier (non {@code null}).
	 * @throws Exception	si la publication via le greffon échoue.
	 */
	public void publishWind(String windChannel, WindDataI wind) throws Exception
	{
		MessageI m = WindMessageFactory.build(stationId, wind);

		String out = "WeatherStation[" + stationId + "] publish wind " + wind
				+ " on " + windChannel;
		this.traceMessage(out + "\n");
		this.logMessage(out + "\n");
		this.publish(windChannel, m);
	}
}
