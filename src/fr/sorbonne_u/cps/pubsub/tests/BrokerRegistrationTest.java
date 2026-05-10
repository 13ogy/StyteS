package fr.sorbonne_u.cps.pubsub.tests;

import static org.junit.Assert.*;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Component-level unit tests for the Broker's registration API (CDC §3.5).
 *
 * <p>Because {@link Broker} is a BCM component, it requires the BCM lifecycle to be running (RMI
 * registry, executor services, port publication). This test uses a minimal {@link AbstractCVM}
 * fixture ({@link BrokerRegistrationCVM}) that is started <em>once per class</em> via {@link
 * BeforeClass}. All assertions are executed synchronously inside {@code execute()} and stored in a
 * static results map; each {@code @Test} method then checks the stored result without needing BCM.
 *
 * <p>The stub client component ({@link StubReceiverComponent}) publishes a {@link
 * fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI} inbound port so the Broker's {@code register()}
 * can do its port connection without the {@code -ea} BCM precondition conflict that occurs when
 * using {@link fr.sorbonne_u.cps.pubsub.base.components.PluginClient}.
 *
 * <p>Ce qui est testé (CDC §3.5) :
 *
 * <ul>
 *   <li>{@code register(uri, FREE)} returns the publishing port URI; {@code registered(uri)} is
 *       subsequently {@code true}.
 *   <li>A second {@code register} with the same URI throws {@link AlreadyRegisteredException}.
 *   <li>{@code register(uri, STANDARD)} returns the privileged port URI, which differs from the
 *       FREE publishing URI.
 *   <li>{@code registered(uri, rc)} throws {@link UnknownClientException} for an unknown URI (new
 *       C.1 contract).
 *   <li>{@code registered(uri, FREE)} is {@code true} when the class matches, {@code false} when it
 *       does not.
 *   <li>{@code unregister} removes the client; a second call throws {@link UnknownClientException}.
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerRegistrationTest {
	// -------------------------------------------------------------------------
	// Test result storage (populated by the CVM fixture, read by @Test methods)
	// -------------------------------------------------------------------------

	private static final ConcurrentMap<String, Object> results = new ConcurrentHashMap<>();

	/** Sentinel meaning "no exception was thrown where one was expected". */
	private static final String NO_THROW = "NO_THROW";

	// -------------------------------------------------------------------------
	// CVM fixture
	// -------------------------------------------------------------------------

	/**
	 * Minimal BCM deployment that drives all registration tests inside {@link #execute()} and
	 * stores results in {@link BrokerRegistrationTest#results}.
	 */
	public static class BrokerRegistrationCVM extends AbstractCVM {
		private String brokerUri;
		private String stubAUri;
		private String stubBUri;

		/** Crée la CVM (rien d'autre à faire — le déploiement est dans {@link #deploy}). */
		public BrokerRegistrationCVM() throws Exception {
			super();
		}

		/**
		 * Crée le {@link Broker} et deux {@link StubReceiverComponent}, puis délègue à {@code
		 * super.deploy()}.
		 */
		@Override
		public void deploy() throws Exception {
			// Broker with nbSchedulableThreads=1 (avoids a BCM precondition at 0).
			this.brokerUri =
					AbstractComponent.createComponent(
							Broker.class.getCanonicalName(),
							new Object[] {
								2, // nbThreads
								1, // nbSchedulableThreads
								3, // nbFreeChannels
								2, // standardQuota
								5, // premiumQuota
								1, // nbReceptionThreads
								1, // nbPropagationThreads
								1 // nbDeliveryThreads
							});

			// Two stub ReceivingCI components (one for FREE, one for STANDARD).
			this.stubAUri =
					AbstractComponent.createComponent(
							StubReceiverComponent.class.getCanonicalName(), new Object[] {});

			this.stubBUri =
					AbstractComponent.createComponent(
							StubReceiverComponent.class.getCanonicalName(), new Object[] {});

			super.deploy();
		}

		/**
		 * Exécute synchroniquement les six tests de registration du broker et stocke chaque
		 * résultat (valeur ou exception) dans {@link BrokerRegistrationTest#results}, sous une clé
		 * {@code Tn_*} lue par les @{@link Test} méthodes.
		 */
		@Override
		public void execute() throws Exception {
			super.execute();

			// Retrieve component references from the CVM map.
			Broker broker = (Broker) this.uri2component.get(this.brokerUri);
			StubReceiverComponent stubA =
					(StubReceiverComponent) this.uri2component.get(this.stubAUri);
			StubReceiverComponent stubB =
					(StubReceiverComponent) this.uri2component.get(this.stubBUri);

			if (broker == null || stubA == null || stubB == null) {
				results.put("fixture_setup_failed", true);
				return;
			}

			final String uriA = stubA.getReceptionPortURI();
			final String uriB = stubB.getReceptionPortURI();

			// ------------------------------------------------------------------
			// Test 1: register(uriA, FREE) — returns publishing port URI
			// ------------------------------------------------------------------
			try {
				String returnedUri = broker.register(uriA, RegistrationClass.FREE);
				results.put("T1_returnedUri", returnedUri);
				results.put("T1_registered", broker.registered(uriA));
			} catch (Exception e) {
				results.put("T1_error", e);
			}

			// ------------------------------------------------------------------
			// Test 2: register(uriA, FREE) again — must throw AlreadyRegisteredException
			// ------------------------------------------------------------------
			try {
				broker.register(uriA, RegistrationClass.FREE);
				results.put("T2_result", NO_THROW);
			} catch (AlreadyRegisteredException e) {
				results.put("T2_result", AlreadyRegisteredException.class.getSimpleName());
			} catch (Exception e) {
				results.put("T2_result", "unexpected:" + e.getClass().getSimpleName());
			}

			// ------------------------------------------------------------------
			// Test 3: register(uriB, STANDARD) — returns privileged port URI
			// ------------------------------------------------------------------
			try {
				String returnedUri = broker.register(uriB, RegistrationClass.STANDARD);
				results.put("T3_returnedUri", returnedUri);
				// The privileged URI for STANDARD must differ from the FREE publishing URI.
				results.put(
						"T3_differentFromFree", !returnedUri.equals(results.get("T1_returnedUri")));
			} catch (Exception e) {
				results.put("T3_error", e);
			}

			// ------------------------------------------------------------------
			// Test 4: registered("unknown", rc) — must throw UnknownClientException (C.1)
			// ------------------------------------------------------------------
			try {
				broker.registered("fake-uri-that-does-not-exist-99999", RegistrationClass.FREE);
				results.put("T4_result", NO_THROW);
			} catch (UnknownClientException e) {
				results.put("T4_result", UnknownClientException.class.getSimpleName());
			} catch (Exception e) {
				results.put("T4_result", "unexpected:" + e.getClass().getSimpleName());
			}

			// ------------------------------------------------------------------
			// Test 5: registered(uriA, rc) — true for FREE, false for STANDARD
			// ------------------------------------------------------------------
			try {
				boolean matchFree = broker.registered(uriA, RegistrationClass.FREE);
				boolean matchStandard = broker.registered(uriA, RegistrationClass.STANDARD);
				results.put("T5_matchFree", matchFree);
				results.put("T5_notMatchStandard", !matchStandard);
			} catch (Exception e) {
				results.put("T5_error", e);
			}

			// ------------------------------------------------------------------
			// Test 6: unregister(uriA) — client removed; second call throws
			// ------------------------------------------------------------------
			try {
				broker.unregister(uriA);
				results.put("T6_registeredAfterUnregister", broker.registered(uriA));
				// Second unregister must throw UnknownClientException.
				try {
					broker.unregister(uriA);
					results.put("T6_secondUnregister", NO_THROW);
				} catch (UnknownClientException e) {
					results.put(
							"T6_secondUnregister", UnknownClientException.class.getSimpleName());
				}
			} catch (Exception e) {
				results.put("T6_error", e);
			}
		}
	}

	// -------------------------------------------------------------------------
	// One-time fixture execution
	// -------------------------------------------------------------------------

	/**
	 * Démarre une fois la fixture BCM avant l'ensemble des @{@link Test} de cette classe. La
	 * méthode {@link BrokerRegistrationCVM#execute()} exécute tous les scénarios synchrones et
	 * stocke leurs résultats dans {@link #results}.
	 *
	 * @throws Exception si le démarrage de la CVM échoue.
	 */
	@BeforeClass
	public static void runFixture() throws Exception {
		BrokerRegistrationCVM cvm = new BrokerRegistrationCVM();
		// 4 seconds is enough for the synchronous scenario in execute().
		cvm.startStandardLifeCycle(4000L);
	}

	// -------------------------------------------------------------------------
	// Helper
	// -------------------------------------------------------------------------

	private static void info(String s) {
		System.out.println("[BrokerRegistrationTest] " + s);
	}

	// -------------------------------------------------------------------------
	// @Test methods — check stored results
	// -------------------------------------------------------------------------

	/**
	 * La fixture BCM a démarré et résolu le {@link Broker} et les deux {@link
	 * StubReceiverComponent}.
	 */
	@Test
	public void testFixtureSetupSucceeded() {
		info("Fixture must have resolved Broker and both StubReceiverComponents.");
		assertNull("Fixture setup failed", results.get("fixture_setup_failed"));
	}

	/**
	 * {@code register(uri, FREE)} retourne une URI non vide ; {@code registered(uri)} vaut {@code
	 * true} ensuite.
	 */
	@Test
	public void testRegisterFreeReturnsPublishingPortAndIsRegistered() {
		info("register(uri, FREE) returns a URI; registered(uri) is true afterward.");
		assertNull("T1 unexpected error", results.get("T1_error"));
		String uri = (String) results.get("T1_returnedUri");
		assertNotNull("T1: returned URI must not be null", uri);
		assertFalse("T1: returned URI must not be empty", uri.isEmpty());
		assertTrue("T1: registered(uri) must be true", (Boolean) results.get("T1_registered"));
	}

	/** Enregistrer deux fois la même URI doit lever {@link AlreadyRegisteredException}. */
	@Test
	public void testRegisterDuplicateThrowsAlreadyRegisteredException() {
		info("Registering the same URI twice must throw AlreadyRegisteredException.");
		assertEquals(
				"T2 must throw AlreadyRegisteredException",
				AlreadyRegisteredException.class.getSimpleName(),
				results.get("T2_result"));
	}

	/**
	 * {@code register(uri, STANDARD)} retourne l'URI du port privilégié, distincte de celle
	 * retournée pour FREE.
	 */
	@Test
	public void testRegisterStandardReturnsPrivilegedPortUri() {
		info("register(uri, STANDARD) returns privileged URI (different from FREE URI).");
		assertNull("T3 unexpected error", results.get("T3_error"));
		String uri = (String) results.get("T3_returnedUri");
		assertNotNull("T3: returned URI must not be null", uri);
		assertFalse("T3: returned URI must not be empty", uri.isEmpty());
		assertTrue(
				"T3: STANDARD URI must differ from FREE URI",
				(Boolean) results.get("T3_differentFromFree"));
	}

	/**
	 * {@code registered(unknownUri, rc)} doit lever {@link UnknownClientException} (contrat C.1).
	 */
	@Test
	public void testRegisteredWithClassThrowsUnknownClientExceptionForUnknownUri() {
		info("registered(unknownUri, rc) must throw UnknownClientException (C.1 contract).");
		assertEquals(
				"T4 must throw UnknownClientException",
				UnknownClientException.class.getSimpleName(),
				results.get("T4_result"));
	}

	/**
	 * {@code registered(uri, FREE)} vaut {@code true} après une inscription FREE ; {@code
	 * registered(uri, STANDARD)} vaut {@code false}.
	 */
	@Test
	public void testRegisteredWithClassMatchAndMismatch() {
		info(
				"registered(uri, FREE) true; registered(uri, STANDARD) false after FREE"
					+ " registration.");
		assertNull("T5 unexpected error", results.get("T5_error"));
		assertTrue("T5: registered(uri, FREE) must be true", (Boolean) results.get("T5_matchFree"));
		assertTrue(
				"T5: registered(uri, STANDARD) must be false",
				(Boolean) results.get("T5_notMatchStandard"));
	}

	/**
	 * {@code unregister} retire le client ; un second appel doit lever {@link
	 * UnknownClientException}.
	 */
	@Test
	public void testUnregisterRemovesClientAndSecondUnregisterThrows() {
		info("unregister removes client; second call throws UnknownClientException.");
		assertNull("T6 unexpected error", results.get("T6_error"));
		assertFalse(
				"T6: registered(uri) must be false after unregister",
				(Boolean) results.get("T6_registeredAfterUnregister"));
		assertEquals(
				"T6: second unregister must throw UnknownClientException",
				UnknownClientException.class.getSimpleName(),
				results.get("T6_secondUnregister"));
	}
}
