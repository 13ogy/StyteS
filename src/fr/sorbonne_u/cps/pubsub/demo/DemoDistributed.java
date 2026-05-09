package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.cvm.AbstractDistributedCVM;
import fr.sorbonne_u.components.helpers.CVMDebugModes;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <strong>Démo distribuée historique (gossip)</strong> du projet pub/sub :
 * deux brokers tournant chacun dans une JVM séparée échangent des messages
 * via le réseau gossip (CDC §4.5 — fédération inter-brokers). Cible le
 * cycle de vie {@link AbstractDistributedCVM} de BCM4Java et nécessite donc
 * un lancement multi-JVM par {@code bash-scripts/launch} (cf.
 * {@code docs/SOUTENANCE.md}).
 *
 * <p>
 * <strong>Préconditions d'exécution :</strong>
 * </p>
 * <ul>
 *   <li>JDK 8 (les scripts BCM4Java {@code launch} se basent sur le
 *       {@code SecurityManager} déprécié sur JDK 19).</li>
 *   <li>Un fichier de configuration XML mappant les URIs JVM aux IPs et
 *       passé en ligne de commande.</li>
 *   <li>Lancer trois processus : {@code broker1-jvm}, {@code broker2-jvm},
 *       puis le contrôleur.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DemoDistributed extends AbstractDistributedCVM {
    /**
     * instantiate the DCVM object.
     *
     * <p><strong>Description</strong></p>
     * <p>
     * The constructor gets from the command line arguments the logical
     * name of the current JVM in the assembly and the name of an XML
     * configuration file giving a mapping between the URI of hosts to their
     * IP addresses in the current deployment.  This JVM URI must be in the
     * static array JVM_URIs and all of the hosts URI in the array HOSTS_URIs
     * and only these ones must appear in the XML configuration file
     *
     * <p><strong>Contract</strong></p>
     *
     * <pre>
     * pre	{@code args != null && args.length > 1}
     * post	{@code true}	// TODO
     * </pre>
     *
     * @param args command line arguments from the main method.
     * @throws Exception <i>todo</i>.
     */



    public static final String BROKER1_JVM_URI = "broker1-jvm";
    public static final String BROKER2_JVM_URI = "broker2-jvm";

    public static final String BROKER1_URI = "broker-1";
    public static final String BROKER2_URI = "broker-2";

    public static final String CLIENT1_URI = "client-1";
    public static final String CLIENT2_URI = "client-2";

    // connexion inter-courtier
    public static final String BROKER1_GOSSIP_PORT_URI = "broker-1-gossip-in";
    public static final String BROKER2_GOSSIP_PORT_URI = "broker-2-gossip-in";

    // channels
    public static final String CHANNEL = "channel0";

    // clock
    public static final String  CLOCK_URI          = "gossip-test-clock";
    public static final String  START_INSTANT_STR  = "2026-02-01T10:00:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0;
    public static final long    DELAY_TO_START_MS  = 5_000L;
	/**
	 * Construit ce CVM ; la création réelle des composants se produit dans
	 * {@link #deploy()}.
	 *
	 * @throws Exception si l'initialisation parent échoue.
	 */


    public DemoDistributed(String[] args) throws Exception {
        super(args);
    }


    @Override
    /**
     * Initialise les modes de debug BCM avant la phase
     * {@link #instantiateAndPublish()} ; appelé par le cycle de vie distribué.
     *
     * @throws Exception si l'initialisation parent échoue.
     */
    public void initialise() throws Exception
    {
       AbstractCVM.DEBUG_MODE.add(CVMDebugModes.LIFE_CYCLE);/*
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.PORTS);
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.CONNECTING);*/
        super.initialise();
    }


    @Override
    /**
     * Instancie et publie les composants spécifiques à la JVM courante :
     * deux brokers connectés en gossip, plus un client par JVM.
     *
     * @throws Exception en cas d'échec de création / publication.
     */
    public void instantiateAndPublish() throws Exception{

        if (thisJVMURI.equals(BROKER1_JVM_URI)) {

            List<String> broker1Neighbors = new ArrayList<>();
            broker1Neighbors.add(BROKER2_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            // JVM 1 : Broker B1 connecté à B2 via gossip
            // B1 connaît l'URI du port gossip entrant de B2
            AbstractComponent.createComponent(
                    Broker.class.getCanonicalName(),
                    new Object[] {
                            BROKER1_URI,              // reflectionInboundPortURI
                            2, 1,                     // nbThreads, nbSchedulableThreads
                            3, 2, 5,                  // nbFreeChannels, standardQuota, premiumQuota
                            2, 4, 8,                  // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
                            broker1Neighbors // URI gossip de B2
                    });

            //  C1 s'enregistre chez B1
            AbstractComponent.createComponent(
                    SubscriberClient.class.getCanonicalName(),
                    new Object[] { CLIENT1_URI, BROKER1_URI, RegistrationCI.RegistrationClass.FREE });

            // ClocksServer sur JVM 1
            long current = System.currentTimeMillis();
            long unixEpochStartTimeInNanos =
                    TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START_MS);
            AbstractComponent.createComponent(
                    ClocksServer.class.getCanonicalName(),
                    new Object[] {
                            CLOCK_URI,
                            unixEpochStartTimeInNanos,
                            Instant.parse(START_INSTANT_STR),
                            ACCELERATION_FACTOR
                    });

        } else if (thisJVMURI.equals(BROKER2_JVM_URI)) {


            List<String> broker2Neighbors = new ArrayList<>();
            broker2Neighbors.add(BROKER1_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            // JVM 2 : Broker B2 connecté à B1 via gossip
            AbstractComponent.createComponent(
                    Broker.class.getCanonicalName(),
                    new Object[] {
                            BROKER2_URI,
                            2, 1,
                            3, 2, 5,
                            2, 4, 8,
                            broker2Neighbors // URI gossip de B1
                    });

            //Client C2 s'enregistre chez B2
            AbstractComponent.createComponent(
                    SubscriberClient.class.getCanonicalName(),
                    new Object[] { CLIENT2_URI, BROKER2_URI, RegistrationCI.RegistrationClass.FREE });

        } else {
            System.out.println("Unknown JVM URI: " + thisJVMURI);
        }

        super.instantiateAndPublish();
    }



    @Override
    /**
     * Établit les connexions inter-JVM (gossip + ports clients ↔ brokers)
     * une fois tous les composants publiés.
     *
     * @throws Exception en cas d'échec de connexion.
     */
    public void	interconnect() throws Exception{
        super.interconnect();
    }


    // =========================================================================
    // Main
    // =========================================================================
	/**
	 * Point d'entrée standalone : démarre le cycle de vie centralisé du CVM
	 * pendant la durée codée en dur, puis termine la JVM.
	 *
	 * @param args ignorés.
	 */

    /**
     * Point d'entrée par JVM : instancie le DCVM avec les arguments BCM
     * (URI de la JVM courante + fichier XML de mapping) puis lance le cycle
     * de vie distribué.
     *
     * @param args arguments BCM4Java standard du DCVM.
     */
    public static void main(String[] args)
    {

        try {
            DemoDistributed dcvm = new DemoDistributed(args);
            // DELAY(5s) + exécution(30s) + marge(10s)
            dcvm.startStandardLifeCycle(45_000L);
            Thread.sleep(5_000L);
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);

        }
    }
}
