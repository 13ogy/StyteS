package fr.sorbonne_u.cps.pubsub.tests;

import static org.junit.Assert.*;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AfterOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BeforeOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BetweenTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.ComparableValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertiesFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;

import org.junit.Test;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tests JUnit 4 unitaires pour le sous-système de filtres (CDC §3.2) ; couvre les classes du
 * package {@code fr.sorbonne_u.cps.pubsub.messages.filters} ainsi que {@link MessageFilter}
 * (productions classes under test).
 *
 * <p>Points vérifiés :
 *
 * <ul>
 *   <li>filtres de valeur :
 *       <ul>
 *         <li>{@link EqualsValueFilter} : égalité stricte ;
 *         <li>{@link ComparableValueFilter} : comparaisons sur Comparable (GE, LE, BETWEEN) ;
 *         <li>{@link AcceptAllValueFilter} : accepte toujours.
 *       </ul>
 *   <li>{@link PropertyFilter} : applique un {@code ValueFilterI} sur une {@link
 *       MessageI.PropertyI} (pas sur un message entier) ;
 *   <li>{@link PropertiesFilter} : applique un {@code MultiValuesFilterI} sur plusieurs propriétés
 *       (contraintes croisées) ;
 *   <li>filtres temporels : accept-all + intervalles inclusifs ;
 *   <li>{@link MessageFilter} : match global sur un message complet (propriétés + temps).
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class MessageFilterTest {

	/** Helper : trace de progression dans la console pour faciliter le diagnostic. */
	private static void info(String s) {
		System.out.println("[MessageFilterTest] " + s);
	}

	/**
	 * {@link EqualsValueFilter} accepte uniquement la valeur exactement égale (au sens de {@code
	 * Object.equals}) à la valeur attendue ; rejette {@code null}.
	 */
	@Test
	public void testEqualsValueFilter() {
		info("EqualsValueFilter matches only if value.equals(expected).");

		MessageFilterI.ValueFilterI f = new EqualsValueFilter("demo");
		assertTrue(f.match("demo"));
		assertFalse(f.match("other"));
		assertFalse(f.match(null));
	}

	/**
	 * {@link ComparableValueFilter} et ses sous-classes effectuent des comparaisons cohérentes sur
	 * des valeurs {@code Comparable} (Integer ici).
	 */
	@Test
	public void testComparableValueFilterNumbersAsComparable() {
		info("ComparableValueFilter compares Comparable values (we use Integers here).");

		MessageFilterI.ValueFilterI ge10 = ComparableValueFilter.greaterOrEqual(10);
		assertTrue(ge10.match(10));
		assertTrue(ge10.match(11));
		assertFalse(ge10.match(9));

		MessageFilterI.ValueFilterI le10 = ComparableValueFilter.lowerOrEqual(10);
		assertTrue(le10.match(10));
		assertTrue(le10.match(9));
		assertFalse(le10.match(11));

		MessageFilterI.ValueFilterI between = ComparableValueFilter.betweenInclusive(10, 20);
		assertTrue(between.match(10));
		assertTrue(between.match(15));
		assertTrue(between.match(20));
		assertFalse(between.match(9));
		assertFalse(between.match(21));
	}

	/** {@link AcceptAllValueFilter} accepte n'importe quelle valeur, y compris {@code null}. */
	@Test
	public void testAcceptAllValueFilter() {
		info("AcceptAllValueFilter always matches (including null).");

		MessageFilterI.ValueFilterI f = new AcceptAllValueFilter();
		assertTrue(f.match("anything"));
		assertTrue(f.match(null));
	}

	/**
	 * {@link PropertyFilter#match} retourne {@code true} ssi le nom de propriété correspond à celui
	 * du filtre ET que le value filter accepte la valeur.
	 */
	@Test
	public void testPropertyFilterPositiveAndNegative() {
		info("PropertyFilter.match applies to a single PropertyI.");

		MessageI.PropertyI p1 = new Message.Property("type", (Serializable) "demo");
		MessageI.PropertyI p2 = new Message.Property("type", (Serializable) "other");
		MessageI.PropertyI p3 = new Message.Property("otherName", (Serializable) "demo");

		MessageFilterI.PropertyFilterI pf =
				new PropertyFilter("type", new EqualsValueFilter("demo"));

		assertTrue(pf.match(p1));
		assertFalse(pf.match(p2));
		assertFalse(pf.match(p3));
	}

	/**
	 * {@link PropertiesFilter} évalue une contrainte croisée sur plusieurs propriétés via un {@code
	 * MultiValuesFilterI} fourni par l'appelant.
	 */
	@Test
	public void testPropertiesFilterCrossConstraintWithCustomMultiValuesFilter() {
		info(
				"PropertiesFilter applies a MultiValuesFilterI over several properties"
					+ " (cross-constraint).");

		MessageFilterI.MultiValuesFilterI mv =
				new fr.sorbonne_u.cps.pubsub.messages.filters.MultiValuesFilter(
						"type", "stationId") {
					@Override
					protected boolean matchValues(Serializable... values) {
						String type = (String) values[0];
						String stationId = (String) values[1];
						return "wind".equals(type)
								&& stationId != null
								&& stationId.startsWith("WS");
					}
				};

		MessageFilterI.PropertiesFilterI pf = new PropertiesFilter(mv);

		MessageI.PropertyI typeOk = new Message.Property("type", (Serializable) "wind");
		MessageI.PropertyI stationOk = new Message.Property("stationId", (Serializable) "WS1");
		MessageI.PropertyI stationKo = new Message.Property("stationId", (Serializable) "XX");

		assertTrue(pf.match(typeOk, stationOk));
		assertFalse(pf.match(typeOk, stationKo));
	}

	/**
	 * Les trois filtres temporels concrets ({@link AcceptAllTimeFilter}, {@link BetweenTimeFilter},
	 * {@link AfterOrAtTimeFilter}, {@link BeforeOrAtTimeFilter}) implémentent correctement les
	 * inclusions d'intervalle.
	 */
	@Test
	public void testTimeFilters() {
		info("Time filters check inclusion in a time interval.");

		Instant now = Instant.now();
		Instant before = now.minusSeconds(10);
		Instant after = now.plusSeconds(10);

		MessageFilterI.TimeFilterI acceptAll = new AcceptAllTimeFilter();
		assertTrue(acceptAll.match(now));

		MessageFilterI.TimeFilterI between = new BetweenTimeFilter(before, after);
		assertTrue(between.match(now));
		assertFalse(between.match(before.minusSeconds(1)));
		assertFalse(between.match(after.plusSeconds(1)));

		MessageFilterI.TimeFilterI afterOrAt = new AfterOrAtTimeFilter(now);
		assertTrue(afterOrAt.match(now));
		assertTrue(afterOrAt.match(after));
		assertFalse(afterOrAt.match(before));

		MessageFilterI.TimeFilterI beforeOrAt = new BeforeOrAtTimeFilter(now);
		assertTrue(beforeOrAt.match(now));
		assertTrue(beforeOrAt.match(before));
		assertFalse(beforeOrAt.match(after));
	}

	/**
	 * {@link MessageFilter#match} effectue un AND global sur le message complet : propriétés (via
	 * {@code PropertyFilterI}) ET fenêtre temporelle (via {@code TimeFilterI}).
	 *
	 * @throws Exception si la construction du message lève (ne doit pas se produire).
	 */
	@Test
	public void testMessageFilterGlobalMatch() throws Exception {
		info("MessageFilter global match on a full Message: propertyFilters + time filter.");

		Message m = new Message("hello");
		m.putProperty("type", "demo");

		MessageFilterI.PropertyFilterI pf =
				new PropertyFilter("type", new EqualsValueFilter("demo"));

		MessageFilterI filter =
				new MessageFilter(
						new MessageFilterI.PropertyFilterI[] {pf},
						new MessageFilterI.PropertiesFilterI[0],
						new AcceptAllTimeFilter());

		assertTrue(filter.match(m));

		Message m2 = new Message("hello");
		m2.putProperty("type", "other");
		assertFalse(filter.match(m2));
	}
}
