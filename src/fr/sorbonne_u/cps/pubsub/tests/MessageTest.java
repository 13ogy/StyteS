package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.exceptions.UnknownPropertyException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import org.junit.Test;

import java.io.Serializable;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Tests JUnit 4 unitaires pour {@link fr.sorbonne_u.cps.pubsub.messages.Message}
 * (production class under test) — couvre le contrat de
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.MessageI} (CDC §3.1).
 *
 * <p>
 * Points vérifiés :
 * </p>
 * <ul>
 *   <li>un message porte un payload et un timestamp non nul ;</li>
 *   <li>les propriétés respectent le contrat CDC : noms uniques, test d'existence,
 *       récupération de valeur ;</li>
 *   <li>la suppression de propriétés fonctionne, et toute propriété manquante lève
 *       {@link UnknownPropertyException} ;</li>
 *   <li>{@code copy()} préserve le timestamp et les propriétés, et autorise une
 *       modification de payload uniquement sur la copie.</li>
 * </ul>
 *
 * <p>
 * Exécuté par {@code org.junit.runner.JUnitCore}, sans démarrage du CVM BCM.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class MessageTest {

	/** Helper : trace de progression dans la console pour faciliter le diagnostic. */
	private static void info(String s) {
		System.out.println("[MessageTest] " + s);
	}

	/**
	 * Un message construit avec un payload garde ce payload et expose un
	 * {@code timeStamp} non nul, antérieur ou égal à l'instant courant.
	 */
	@Test
	public void testPayloadAndTimestampNotNull() {
		info("payload and timestamp are set at construction time.");

		Serializable payload = "hello";
		Message m = new Message(payload);

		assertEquals(payload, m.getPayload());
		assertNotNull(m.getTimeStamp());

		assertTrue(!m.getTimeStamp().isAfter(Instant.now()));
	}

	/**
	 * {@code putProperty} ajoute une propriété, {@code propertyExists} et
	 * {@code getPropertyValue} la retrouvent ensuite.
	 *
	 * @throws Exception si la lecture de la valeur échoue (ne doit pas se produire).
	 */
	@Test
	public void testPutPropertyThenExistsAndGetPropertyValue() throws Exception {
		info("putProperty + propertyExists + getPropertyValue.");

		Message m = new Message("p");
		m.putProperty("type", "wind");

		assertTrue(m.propertyExists("type"));
		assertEquals("wind", m.getPropertyValue("type"));
	}

	/**
	 * {@code putProperty} doit refuser un nom de propriété déjà présent
	 * (précondition CDC §3.1).
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testPutPropertyDuplicateNameThrows() {
		info("putProperty must not accept duplicate property names (CDC precondition).");

		Message m = new Message("p");
		m.putProperty("type", "wind");
		m.putProperty("type", "other"); // must throw
	}

	/**
	 * {@code removeProperty} retire bien une propriété existante.
	 *
	 * @throws Exception si {@code removeProperty} échoue (ne doit pas se produire).
	 */
	@Test
	public void testRemovePropertyThenDoesNotExist() throws Exception {
		info("removeProperty removes an existing property.");

		Message m = new Message("p");
		m.putProperty("type", "wind");
		assertTrue(m.propertyExists("type"));

		m.removeProperty("type");
		assertFalse(m.propertyExists("type"));
	}

	/**
	 * {@code removeProperty} sur une propriété inconnue doit lever
	 * {@link UnknownPropertyException}.
	 *
	 * @throws Exception attendue : {@link UnknownPropertyException}.
	 */
	@Test(expected = UnknownPropertyException.class)
	public void testRemoveMissingPropertyThrows() throws Exception {
		info("removeProperty on missing property must throw UnknownPropertyException.");
		Message m = new Message("p");
		m.removeProperty("missing");
	}

	/**
	 * {@code getPropertyValue} sur une propriété inconnue doit lever
	 * {@link UnknownPropertyException}.
	 *
	 * @throws Exception attendue : {@link UnknownPropertyException}.
	 */
	@Test(expected = UnknownPropertyException.class)
	public void testGetPropertyValueMissingThrows() throws Exception {
		info("getPropertyValue on missing property must throw UnknownPropertyException.");
		Message m = new Message("p");
		m.getPropertyValue("missing");
	}

	/**
	 * {@code getProperties} doit retourner un tableau défensif : modifier le
	 * tableau retourné ne doit pas affecter l'état interne du message.
	 */
	@Test
	public void testGetPropertiesReturnsDefensiveCopy() {
		info("getProperties must return an array copy (defensive copy).");

		Message m = new Message("p");
		m.putProperty("a", "1");
		m.putProperty("b", "2");

		MessageI.PropertyI[] props1 = m.getProperties();
		assertEquals(2, props1.length);

		props1[0] = null;

		MessageI.PropertyI[] props2 = m.getProperties();
		assertEquals(2, props2.length);
		assertNotNull(props2[0]);
		assertNotNull(props2[1]);
	}

	/**
	 * {@code copy()} préserve timestamp + propriétés et permet une modification
	 * indépendante du payload sur la copie (référence partagée mais
	 * {@code setPayload} écrase localement).
	 *
	 * @throws Exception si la lecture de la propriété copiée échoue.
	 */
	@Test
	public void testCopyPreservesTimestampAndProperties() throws Exception {
		info("copy() preserves timestamp and properties, and keeps same payload reference.");

		Message m = new Message((Serializable) "payload");
		m.putProperty("type", "demo");

		MessageI c = m.copy();

		assertEquals(m.getTimeStamp(), c.getTimeStamp());
		assertEquals(m.getPayload(), c.getPayload());
		assertEquals("demo", c.getPropertyValue("type"));

		c.setPayload("other");

		assertEquals("payload", m.getPayload());
		assertEquals("other", c.getPayload());
	}
}
