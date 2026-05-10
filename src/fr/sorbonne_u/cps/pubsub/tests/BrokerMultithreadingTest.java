package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test JUnit 4 prouvant que les trois pools d'executors ouverts par
 * {@link Broker} (réception, propagation, livraison) matérialisent bien
 * des threads <em>distincts</em> et exécutent les tâches en parallèle, et
 * non séquentiellement sur un thread unique.
 *
 * <p><strong>Méthode</strong></p>
 *
 * <p>Le test sous-classe {@link Broker} en {@link InstrumentedBroker} pour
 * pouvoir soumettre directement des tâches bloquantes à chacun des trois
 * indices d'executor exposés par le composant ({@code esReceptionIndex},
 * {@code esPropagationIndex}, {@code esDeliveryIndex}). Chaque tâche
 * relâche un {@link CountDownLatch} commun puis bloque sur ce même latch :
 * si les trois tâches s'exécutent réellement en parallèle, les trois
 * threads atteignent le {@code countDown}, le latch tombe à zéro et tous
 * progressent ; si elles étaient sérialisées sur un thread unique, la
 * première bloquerait pour toujours et le test expirerait sur
 * {@link CountDownLatch#await(long, TimeUnit)} .</p>
 *
 * <p>En complément, chaque tâche enregistre {@code Thread.currentThread()}
 * dans une file partagée. On vérifie ensuite que les trois identités sont
 * <em>distinctes</em> — ce qui n'est possible qu'avec un vrai pool
 * multi-threads par executor (un pool {@code newSingleThreadExecutor}
 * réutiliserait la même instance pour toutes les tâches qui lui sont
 * soumises, mais comme nos trois soumissions vont à <em>trois pools
 * distincts</em>, on doit nécessairement observer 3 threads différents si
 * la concurrence est effective).</p>
 *
 * <p>Created on : 2026-05-10</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class		BrokerMultithreadingTest
{
	private static final String BROKER_RIP = "instrumented-broker-rip";

	/** Limite max d'attente pour la barrière concurrente — au-delà, on
	 * considère que les tâches sont sérialisées et le test échoue. */
	private static final long	BARRIER_TIMEOUT_S = 5L;

	/** Latch + recorder partagés ; remis à zéro à chaque test. */
	static CountDownLatch sharedBarrier;
	static ConcurrentLinkedQueue<Thread> threadsThatExecuted;

	/**
	 * Variante du courtier instrumentée pour exposer une méthode
	 * {@link #fireProbe} qui soumet la même tâche-sonde aux trois
	 * executors du pipeline et publie ensuite les threads observés via
	 * {@link BrokerMultithreadingTest#threadsThatExecuted}.
	 */
	@OfferedInterfaces(offered = {
		RegistrationCI.class, PublishingCI.class,
		PrivilegedClientCI.class, GossipReceiverCI.class
	})
	@RequiredInterfaces(required = {
		ReceivingCI.class, GossipSenderCI.class
	})
	public static class InstrumentedBroker extends Broker
	{
		protected InstrumentedBroker(String reflectionInboundPortURI,
								 int nbThreads, int nbSchedulableThreads,
								 int nbFreeChannels,
								 int standardQuota, int premiumQuota,
								 int nbReceptionThreads,
								 int nbPropagationThreads,
								 int nbDeliveryThreads) throws Exception
		{
			super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads,
				nbFreeChannels, standardQuota, premiumQuota,
				nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads);
		}

		/**
		 * Soumet une même tâche-sonde aux trois executors du pipeline. La
		 * tâche : (1) enregistre {@link Thread#currentThread()} dans la
		 * file partagée du test, (2) décrémente le latch partagé, (3)
		 * attend que le latch tombe à zéro pour s'assurer que les trois
		 * tâches sont concurrentes. Si l'un des executors était dégradé à
		 * un thread unique partagé avec les deux autres, l'attente
		 * expirerait.
		 *
		 * @throws Exception si l'une des soumissions {@code runTask}
		 *					 échoue.
		 */
		public void fireProbe() throws Exception
		{
			this.runTask(this.getReceptionExecutorIndex(), o -> probe());
			this.runTask(this.getPropagationExecutorIndex(), o -> probe());
			this.runTask(this.getDeliveryExecutorIndex(), o -> probe());
		}

		private static void probe()
		{
			threadsThatExecuted.add(Thread.currentThread());
			sharedBarrier.countDown();
			try {
				if (!sharedBarrier.await(BARRIER_TIMEOUT_S, TimeUnit.SECONDS)) {
					throw new IllegalStateException(
						"timeout sur la barrière : les tâches semblent "
						+ "sérialisées sur un thread unique");
				}
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Démarre une CVM avec un {@link InstrumentedBroker}, soumet une
	 * sonde à chacun des trois executors et vérifie que :
	 * <ol>
	 * <li>le {@link CountDownLatch} commun atteint zéro avant le délai
	 * (preuve que les trois threads progressent en parallèle) ;</li>
	 * <li>les trois sondes ont été exécutées par <em>trois threads
	 * distincts</em> (preuve que chaque executor possède bien son
	 * propre pool indépendant).</li>
	 * </ol>
	 *
	 * @throws Exception si une étape de la CVM ou de la soumission
	 *					 échoue.
	 */
	@Test
	public void			brokerExecutorPoolsRunInParallel() throws Exception
	{
		sharedBarrier = new CountDownLatch(3);
		threadsThatExecuted = new ConcurrentLinkedQueue<>();

		AbstractCVM cvm = new AbstractCVM()
		{
			@Override
			public void deploy() throws Exception
			{
				AbstractComponent.createComponent(
					InstrumentedBroker.class.getName(),
					new Object[] { BROKER_RIP, 2, 1, 3, 2, 5, 2, 4, 8 });
				super.deploy();
			}

			@Override
			public void execute() throws Exception
			{
				super.execute();
				InstrumentedBroker b =
					(InstrumentedBroker) this.uri2component.get(BROKER_RIP);
				b.fireProbe();
				if (!sharedBarrier.await(BARRIER_TIMEOUT_S, TimeUnit.SECONDS)) {
					throw new IllegalStateException(
						"barrière non atteinte : les trois pools du courtier "
						+ "n'exécutent PAS en parallèle (sérialisation sur un "
						+ "thread unique suspectée)");
				}
			}
		};

		cvm.startStandardLifeCycle(BARRIER_TIMEOUT_S * 1_000L + 1_000L);

		assertEquals(
			"on attendait 3 sondes exécutées (réception, propagation, "
			+ "livraison)",
			3, threadsThatExecuted.size());

		Set<Thread> distinct = new HashSet<>(threadsThatExecuted);
		assertEquals(
			"les 3 sondes doivent avoir été servies par 3 threads "
			+ "distincts ; threads observés : " + threadsThatExecuted,
			3, distinct.size());

		for (Thread t : distinct) {
			assertTrue(
				"les threads des executors du courtier doivent porter une "
				+ "étiquette dérivée de l'URI de réflexion du composant "
				+ "(convention BCM4JavaComponentThreadFactory) ; vu : "
				+ t.getName(),
				t.getName().contains(BROKER_RIP));
		}
	}

	/**
	 * Garde-fou : on s'assure que la JVM dispose d'au moins deux cœurs ;
	 * sinon le multithreading effectif ne peut être démontré.
	 */
	@Test
	public void			runtimeOffersAtLeastTwoCores()
	{
		int cores = Runtime.getRuntime().availableProcessors();
		assertTrue(
			"JVM ne dispose que de " + cores + " cœur(s) ; le multithreading "
			+ "effectif requiert au moins 2 processeurs disponibles.",
			cores >= 2);
	}
}
