package fr.sorbonne_u.cps.pubsub.messages.filters;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.MultiValuesFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertiesFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link PropertiesFilterI}.
 *
 * <p>This filter applies a {@link MultiValuesFilterI} on a group of properties, enabling
 * cross-constraints between their values.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PropertiesFilter implements PropertiesFilterI {
	private static final long serialVersionUID = 1L;

	protected final MultiValuesFilterI multiValuesFilter;

	/**
	 * Crée le filtre.
	 *
	 * @param multiValuesFilter filtre multi-valeurs portant la contrainte croisée (non {@code
	 *     null}).
	 * @throws IllegalArgumentException si {@code multiValuesFilter} est {@code null}.
	 */
	public PropertiesFilter(MultiValuesFilterI multiValuesFilter) {
		if (multiValuesFilter == null) {
			throw new IllegalArgumentException("multiValuesFilter cannot be null.");
		}
		this.multiValuesFilter = multiValuesFilter;
	}

	/**
	 * @return le filtre multi-valeurs sous-jacent (jamais {@code null}).
	 */
	@Override
	public MultiValuesFilterI getMultiValuesFilter() {
		return this.multiValuesFilter;
	}

	/**
	 * @param properties propriétés candidates ({@code length >= 2} attendu).
	 * @return {@code true} ssi toutes les propriétés requises par le {@link MultiValuesFilterI}
	 *     sont présentes dans {@code properties} et que leurs valeurs satisfont {@link
	 *     MultiValuesFilterI#match}.
	 */
	@Override
	public boolean match(PropertyI... properties) {
		if (properties == null || properties.length < 2) {
			return false;
		}

		Map<String, Serializable> byName = new HashMap<String, Serializable>();
		for (PropertyI p : properties) {
			if (p != null) {
				byName.put(p.getName(), p.getValue());
			}
		}

		String[] names = this.multiValuesFilter.getNames();
		Serializable[] values = new Serializable[names.length];
		for (int i = 0; i < names.length; i++) {
			if (!byName.containsKey(names[i])) {
				return false;
			}
			values[i] = byName.get(names[i]);
		}

		return this.multiValuesFilter.match(values);
	}
}
