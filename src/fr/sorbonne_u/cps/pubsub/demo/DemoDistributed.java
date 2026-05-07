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


    // clock
    public static final String  CLOCK_URI          = "gossip-test-clock";
    public static final String  START_INSTANT_STR  = "2026-02-01T10:00:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0;
    public static final long    DELAY_TO_START_MS  = 5_000L;


    public DemoDistributed(String[] args) throws Exception {
        super(args);
    }


    @Override
    public void initialise() throws Exception
    {
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.LIFE_CYCLE);
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.PORTS);
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.CONNECTING);
        super.initialise();
    }


    @Override
    public void instantiateAndPublish() throws Exception{

        if (thisJVMURI.equals(BROKER1_JVM_URI)) {

            List<String> broker1Neighbors = new ArrayList<>();
            broker1Neighbors.add(BROKER2_GOSSIP_PORT_URI);
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
                    new Object[] { CLIENT1_URI, RegistrationCI.RegistrationClass.FREE });

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
            broker2Neighbors.add(BROKER1_GOSSIP_PORT_URI);
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
                    new Object[] { CLIENT2_URI, RegistrationCI.RegistrationClass.FREE });

        } else {
            System.out.println("Unknown JVM URI: " + thisJVMURI);
        }

        super.instantiateAndPublish();
    }



    @Override
    public void	interconnect() throws Exception{
        super.interconnect();
    }


    // =========================================================================
    // Main
    // =========================================================================

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
