package fr.sorbonne_u.cps.pubsub.messages;

import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Hashtable;

import fr.sorbonne_u.cps.pubsub.exceptions.UnknownPropertyException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

/**
 * La classe {@code Message} implémente {@link MessageI} comme représentation
 * concrète des messages échangés via le système de publication/souscription.
 *
 * <p>
 * Un message est composé :
 * </p>
 * <ul>
 *   <li>d’une charge utile (payload), tout objet {@link Serializable} ;</li>
 *   <li>d’un horodatage (date de création) ;</li>
 *   <li>d’un ensemble de propriétés nommées (paires nom/valeur).</li>
 * </ul>
 *
 * <p>
 * Cette implémentation respecte le contrat de {@link MessageI} :
 * </p>
 * <ul>
 *   <li>les noms de propriétés sont uniques dans un message ;</li>
 *   <li>{@link #copy()} effectue une copie profonde de la structure du message
 *   et des propriétés, mais conserve une référence vers le même objet payload.</li>
 * </ul>
 *
 * <p>
 * Created on : 2026-02-08
 * </p>
 */
public class Message implements MessageI
{
	private static final long serialVersionUID = 1L;

	/** Charge utile transportée par ce message. */
	private Serializable payload;
	/** Horodatage de création du message (immuable). */
	private final Instant timeStamp;
	/**
	 * Propriétés indexées par leur nom.
	 *
	 * <p>
	 * Un {@link Hashtable} est utilisé pour éviter les avertissements de
	 * sérialisation (il est sérialisable, contrairement à l’interface
	 * {@link Map}).
	 * </p>
	 */
	private final Hashtable<String, PropertyI> properties;

	// -------------------------------------------------------------------------
	// Inner classes
	// -------------------------------------------------------------------------

	/**
	 * Implémentation simple et immuable de {@link MessageI.PropertyI}.
	 *
	 * <p>
	 * Created on : 2026-02-08
	 * </p>
	 */
	public static class Property implements PropertyI
	{
		private static final long serialVersionUID = 1L;

		private final String name;
		private final Serializable value;

	/**
	 * Crée une propriété.
	 *
	 * @param name  nom de la propriété.
	 * @param value valeur de la propriété.
	 */
		public Property(String name, Serializable value)
		{
			assert name != null && !name.isEmpty();
			this.name = name;
			this.value = value;
		}

		/**
		 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI#getName()
		 */
		@Override
		public String getName()
		{
			return this.name;
		}

		/**
		 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI#getValue()
		 */
		@Override
		public Serializable getValue()
		{
			return this.value;
		}
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Crée un message avec le payload donné et aucune propriété.
	 *
	 * <p><strong>Contract</strong></p>
	 *
	 * <pre>
	 * pre  {@code true}  // pas de précondition.
	 * post {@code getTimeStamp() != null}
	 * </pre>
	 *
	 * @param payload charge utile du message.
	 */
	public Message(Serializable payload)
	{
		this(payload, (Map<String, Serializable>) null);
	}

	/**
	 * Internal constructor used to create messages with an explicit timestamp
	 * (mainly to implement {@link #copy()} as an actual copy, preserving the
	 * original timestamp).
	 *
	 * @param payload    payload of the message.
	 * @param timeStamp  timestamp of the message (must not be null).
	 */
	protected Message(Serializable payload, Instant timeStamp)
	{
		this(payload, (Map<String, Serializable>) null, timeStamp);
	}

	/**
	 * Crée un message avec le payload donné et des propriétés initiales.
	 *
	 * <p><strong>Contract</strong></p>
	 *
	 * <pre>
	 * pre  {@code true}  // pas de précondition.
	 * post {@code getTimeStamp() != null}
	 * </pre>
	 *
	 * @param payload            charge utile du message.
	 * @param initialProperties  propriétés initiales indexées par nom (peut être null).
	 */
	public Message(Serializable payload, Map<String, Serializable> initialProperties)
	{
		this(payload, initialProperties, Instant.now());
	}

	/**
	 * Internal constructor used to set an explicit timestamp.
	 *
	 * @param payload            payload of the message.
	 * @param initialProperties  initial properties (may be null).
	 * @param timeStamp          explicit timestamp (must not be null).
	 */
	protected Message(
		Serializable payload,
		Map<String, Serializable> initialProperties,
		Instant timeStamp
		)
	{
		if (timeStamp == null) {
			throw new IllegalArgumentException("timeStamp cannot be null.");
		}

		this.payload = payload;
		this.timeStamp = timeStamp;
		this.properties = new Hashtable<String, PropertyI>();

		if (initialProperties != null && !initialProperties.isEmpty()) {
			for (Map.Entry<String, Serializable> e : initialProperties.entrySet()) {
				String name = e.getKey();
				Serializable value = e.getValue();
				if (name == null || name.isEmpty()) {
					throw new IllegalArgumentException(
						"Property name cannot be null or empty.");
				}
				if (this.properties.containsKey(name)) {
					throw new IllegalArgumentException(
						"Duplicate property name: " + name);
				}
				this.properties.put(name, new Property(name, value));
			}
		}
	}

	// -------------------------------------------------------------------------
	// Methods
	// -------------------------------------------------------------------------

	/**
	 * Retourne une vue non modifiable de la table interne des propriétés.
	 *
	 * <p>
	 * Cette méthode ne fait pas partie de {@link MessageI} ; elle est fournie
	 * comme aide pour des implémentations (par exemple des filtres) ayant
	 * besoin d’un accès rapide aux propriétés par nom.
	 * </p>
	 *
	 * @return vue non modifiable des propriétés internes.
	 */
	public Map<String, PropertyI> getPropertiesMap()
	{
		return Collections.unmodifiableMap(this.properties);
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#propertyExists(java.lang.String)
	 */
	@Override
	public boolean propertyExists(String name)
	{
		assert name != null && !name.isEmpty();
		return this.properties.containsKey(name);
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#putProperty(java.lang.String, java.io.Serializable)
	 */
	@Override
	public void putProperty(String name, Serializable value)
	{
		assert name != null && !name.isEmpty();

		if (this.properties.containsKey(name)) {
			throw new IllegalArgumentException("Property already exists: " + name);
		}

		this.properties.put(name, new Property(name, value));
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#removeProperty(java.lang.String)
	 */
	@Override
	public void removeProperty(String name) throws UnknownPropertyException
	{
		assert name != null && !name.isEmpty();

		if (!this.properties.containsKey(name)) {
			throw new UnknownPropertyException(name);
		}
		this.properties.remove(name);
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#getPropertyValue(java.lang.String)
	 */
	@Override
	public Serializable getPropertyValue(String name) throws UnknownPropertyException
	{
		assert name != null && !name.isEmpty();

		PropertyI p = this.properties.get(name);
		if (p == null) {
			throw new UnknownPropertyException(name);
		}
		return p.getValue();
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#getProperties()
	 */
	@Override
	public PropertyI[] getProperties()
	{
		PropertyI[] ret = this.properties.values().toArray(new PropertyI[0]);
		return Arrays.copyOf(ret, ret.length);
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#copy()
	 */
	@Override
	public MessageI copy()
	{
		Message copy = new Message(this.payload, this.timeStamp);
		for (PropertyI p : this.properties.values()) {
			copy.properties.put(p.getName(), new Property(p.getName(), p.getValue()));
		}
		return copy;
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#setPayload(java.io.Serializable)
	 */
	@Override
	public void setPayload(Serializable payload)
	{
		this.payload = payload;
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#getPayload()
	 */
	@Override
	public Serializable getPayload()
	{
		return this.payload;
	}

	/**
	 * @see fr.sorbonne_u.cps.pubsub.interfaces.MessageI#getTimeStamp()
	 */
	@Override
	public Instant getTimeStamp()
	{
		return this.timeStamp;
	}
}
