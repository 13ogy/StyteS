package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.MultiValuesFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertiesFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;

/**
 * Basic implementation of {@link PropertiesFilterI}.
 *
 * <p>
 * This filter applies a {@link MultiValuesFilterI} on a group of properties,
 * enabling cross-constraints between their values.
 * </p>
 *
 * <p>Created on : 2026-02-15</p>
 */
public class PropertiesFilter implements PropertiesFilterI
{
	private static final long serialVersionUID = 1L;

	protected final MultiValuesFilterI multiValuesFilter;

	public PropertiesFilter(MultiValuesFilterI multiValuesFilter)
	{
		if (multiValuesFilter == null) {
			throw new IllegalArgumentException("multiValuesFilter cannot be null.");
		}
		this.multiValuesFilter = multiValuesFilter;
	}

	@Override
	public MultiValuesFilterI getMultiValuesFilter()
	{
		return this.multiValuesFilter;
	}

	@Override
	public boolean match(PropertyI... properties)
	{
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
