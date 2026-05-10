package fr.sorbonne_u.cps.pubsub.messages.filters;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;

/**
 * An implementation of {@link PropertyFilterI}.
 *
 * <p>This filter targets a single property name and applies a {@link ValueFilterI} on its value.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PropertyFilter implements PropertyFilterI {
	private static final long serialVersionUID = 1L;

	protected final String name;
	protected final ValueFilterI valueFilter;

	/**
	 * Crée le filtre.
	 *
	 * @param name nom de la propriété ciblée (non {@code null}, non vide).
	 * @param valueFilter filtre appliqué à la valeur de la propriété (non {@code null}).
	 * @throws IllegalArgumentException si une précondition n'est pas respectée.
	 */
	public PropertyFilter(String name, ValueFilterI valueFilter) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("name cannot be null or empty.");
		}
		if (valueFilter == null) {
			throw new IllegalArgumentException("valueFilter cannot be null.");
		}
		this.name = name;
		this.valueFilter = valueFilter;
	}

	/**
	 * @return le nom de la propriété ciblée (jamais {@code null}).
	 */
	@Override
	public String getName() {
		return this.name;
	}

	/**
	 * @return le filtre de valeur sous-jacent (jamais {@code null}).
	 */
	@Override
	public ValueFilterI getValueFilter() {
		return this.valueFilter;
	}

	/**
	 * @param property propriété candidate.
	 * @return {@code true} ssi {@code property != null}, son nom correspond à {@link #getName()} et
	 *     sa valeur satisfait {@link #getValueFilter()}.
	 */
	@Override
	public boolean match(PropertyI property) {
		return property != null
				&& this.name.equals(property.getName())
				&& this.valueFilter.match(property.getValue());
	}
}
