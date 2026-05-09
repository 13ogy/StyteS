package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

/**
 * Singleton {@link MessageFilterI} accepting every message.
 *
 * <p>
 * Per the soutenance review, the previous implementation built a heavyweight
 * {@link fr.sorbonne_u.cps.pubsub.messages.MessageFilter} with empty filter
 * arrays. This class replaces it: {@link #match(MessageI)} simply returns
 * {@code true} without allocating or scanning any property.
 * </p>
 *
 * <p>
 * Use {@link fr.sorbonne_u.cps.pubsub.messages.MessageFilter#acceptAll()} to
 * obtain the canonical instance.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class AcceptAllMessageFilter implements MessageFilterI
{
	private static final long serialVersionUID = 1L;

	/** Canonical singleton. */
	public static final AcceptAllMessageFilter INSTANCE = new AcceptAllMessageFilter();

	private static final PropertyFilterI[] NO_PROPERTY_FILTERS = new PropertyFilterI[0];
	private static final PropertiesFilterI[] NO_PROPERTIES_FILTERS = new PropertiesFilterI[0];
	private static final TimeFilterI ACCEPT_ALL_TIME = new AcceptAllTimeFilter();

	private AcceptAllMessageFilter() { }

	@Override
	public PropertyFilterI[] getPropertyFilters() { return NO_PROPERTY_FILTERS; }

	@Override
	public PropertiesFilterI[] getPropertiesFilters() { return NO_PROPERTIES_FILTERS; }

	@Override
	public TimeFilterI getTimeFilter() { return ACCEPT_ALL_TIME; }

	@Override
	public boolean match(MessageI message) { return true; }

	private Object readResolve() { return INSTANCE; }
}
