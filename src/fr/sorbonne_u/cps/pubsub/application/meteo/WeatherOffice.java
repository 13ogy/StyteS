package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.base.components.PrivilegedClient;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;

/**
 * Composant "Bureau météo" (CDC §3.4) modélisé comme un client pub/sub
 * <strong>privilégié</strong> spécialisé.
 *
 * <p>
 * <strong>Choix de conception</strong> : <em>WeatherOffice</em> hérite de
 * {@link PrivilegedClient}, qui fournit
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin} +
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin} (canal
 * privilégié = inclut publication, cf. CDC §3.6). Le composant n'apporte
 * que la fabrication des messages d'alerte et la promotion automatique en
 * classe {@code STANDARD} (le bureau a besoin des canaux privés pour
 * diffuser ses alertes ; cf. CDC §3.3 — quotas par classe de service).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class WeatherOffice extends PrivilegedClient
{
	// -------------------------------------------------------------------------
	// Champs métier (l'enregistrement + le greffon privilégié sont hérités
	// de PrivilegedClient).
	// -------------------------------------------------------------------------

	private final String officeId;

	// -------------------------------------------------------------------------
	// Constructeurs
	// -------------------------------------------------------------------------

	/**
	 * Constructeur principal utilisé par les démos centralisées.
	 *
	 * @param uri					URI de port de réflexion / identifiant participant.
	 * @param testScenario			scénario temporisé optionnel ({@code null} = aucun).
	 * @param officeId				identifiant métier du bureau météo.
	 * @param brokerReflectionURI	URI de réflexion du courtier cible.
	 * @throws Exception			si l'initialisation BCM ou des plugins échoue.
	 */
	protected WeatherOffice(
		String uri,
		TestScenario testScenario,
		String officeId,
		String brokerReflectionURI) throws Exception
	{
		super(uri, brokerReflectionURI, testScenario,
				RegistrationCI.RegistrationClass.FREE);
		this.officeId = officeId;
	}

	/**
	 * Surcharge sans scénario (déploiements répartis).
	 *
	 * @param uri					URI de port de réflexion.
	 * @param officeId				identifiant métier.
	 * @param brokerReflectionURI	URI de réflexion du courtier cible.
	 * @throws Exception			si l'initialisation échoue.
	 */
	protected WeatherOffice(
		String uri,
		String officeId,
		String brokerReflectionURI) throws Exception
	{
		this(uri, null, officeId, brokerReflectionURI);
	}

	// (Pas de surcharge legacy : le constructeur 3-arg suffit. Les démos
	// répartis fournissent toujours l'URI de broker en argument explicite.)

	// -------------------------------------------------------------------------
	// Cycle de vie : enregistré FREE par le parent puis promu STANDARD pour
	// pouvoir créer/publier sur des canaux privilégiés.
	// -------------------------------------------------------------------------

	/**
	 * Le parent {@link PrivilegedClient#execute()} effectue l'enregistrement
	 * FREE puis exécute le scénario éventuel ; on enchaîne ici la promotion
	 * vers la classe {@code STANDARD} pour activer les fonctionnalités
	 * privilégiées attendues du bureau (création de canaux privés et
	 * publications associées, cf. CDC §3.3).
	 *
	 * @throws Exception si l'enregistrement initial ou la promotion échoue.
	 */
	@Override
	public void execute() throws Exception
	{
		super.execute();
		this.modifyServiceClass(RegistrationCI.RegistrationClass.STANDARD);
	}

	// -------------------------------------------------------------------------
	// API métier
	// -------------------------------------------------------------------------

	/**
	 * Publie une alerte météo sur le canal indiqué. Le message est construit
	 * via {@link MeteoAlertMessageFactory#build(String, MeteoAlertI)}
	 * (CDC §3.5 — convention de propriétés des messages d'alerte).
	 *
	 * @param alertChannel	canal de publication (typiquement
	 *						{@link MeteoProperties#DEFAULT_ALERT_CHANNEL} ou un
	 *						canal privilégié appartenant au bureau).
	 * @param alert			alerte à publier (non {@code null}).
	 * @throws Exception	si la publication via le greffon échoue.
	 */
	public void publishAlert(String alertChannel, MeteoAlertI alert) throws Exception
	{
		MessageI m = MeteoAlertMessageFactory.build(officeId, alert);

		String out = "WeatherOffice[" + officeId + "] publish alert " + alert
				+ " on " + alertChannel;
		this.traceMessage(out + "\n");
		this.logMessage(out + "\n");
		this.publish(alertChannel, m);
	}
}
