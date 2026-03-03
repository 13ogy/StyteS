package fr.sorbonne_u.cps.pubsub.messages.filters;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;

/**
 * An implementation of {@link PropertyFilterI}.
 *
 * <p>
 * This filter targets a single property name and applies a {@link ValueFilterI}
 * on its value.
 * </p>
 *
 *
 * @author Bogdan Styn
 */
public class PropertyFilter implements PropertyFilterI
{
	private static final long serialVersionUID = 1L;

	protected final String name;
	protected final ValueFilterI valueFilter;

	public PropertyFilter(String name, ValueFilterI valueFilter)
	{
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("name cannot be null or empty.");
		}
		if (valueFilter == null) {
			throw new IllegalArgumentException("valueFilter cannot be null.");
		}
		this.name = name;
		this.valueFilter = valueFilter;
	}

	@Override
	public String getName()
	{
		return this.name;
	}

	@Override
	public ValueFilterI getValueFilter()
	{
		return this.valueFilter;
	}

	@Override
	public boolean match(PropertyI property)
	{
		if (property == null) {
			return false;
		}
		if (!this.name.equals(property.getName())) {
			return false;
		}
		return this.valueFilter.match(property.getValue());
	}
}
