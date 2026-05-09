package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.components.ports.PortI;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal helper used by demo client components to defensively disconnect
 * any outbound port still wired in their {@link AbstractComponent#shutdown()}
 * teardown path.
 *
 * <p>Background: BCM4Java's {@code AbstractComponent.shutdown()} iterates the
 * component's port map and calls {@code destroyPort()} on each entry, which
 * trips a {@code !connected()} precondition assertion (under {@code -ea}) for
 * any outbound port that was not explicitly disconnected first. This helper
 * walks the port map via reflection and asks the owner to disconnect every
 * outbound port that reports {@code connected() == true}, swallowing any
 * exception from individual disconnect calls (best-effort cleanup).
 *
 * <p>This is needed because some BCM facilities (notably
 * {@code initialiseClock()} via {@code ClocksServerOutboundPort}) may leave a
 * port connected when teardown races with scheduled-task execution.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class PortCleanupUtil
{
	private PortCleanupUtil() {}

	/**
	 * Best-effort port cleanup: walks the owner's port map via reflection,
	 * disconnects every connected outbound port and unpublishes every
	 * published port. Any individual failure is swallowed so that the cleanup
	 * never breaks the surrounding {@code shutdown()} call.
	 *
	 * @param owner the component being shut down (must not be {@code null}).
	 */
	public static void disconnectStillConnectedOutboundPorts(ComponentI owner)
	{
		try {
			Field f = AbstractComponent.class.getDeclaredField("portURIs2ports");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<String, PortI> map = (Map<String, PortI>) f.get(owner);

			List<String> toDisconnect = new ArrayList<>();
			List<PortI> toUnpublish = new ArrayList<>();
			for (Map.Entry<String, PortI> e : map.entrySet()) {
				PortI p = e.getValue();
				if (p instanceof AbstractOutboundPort
						&& ((AbstractOutboundPort) p).connected()) {
					toDisconnect.add(e.getKey());
				}
				try {
					if (p.isPublished()) {
						toUnpublish.add(p);
					}
				} catch (Exception ignore) {}
			}

			Method dpd = AbstractComponent.class
					.getDeclaredMethod("doPortDisconnection", String.class);
			dpd.setAccessible(true);
			for (String puri : toDisconnect) {
				try { dpd.invoke(owner, puri); } catch (Exception ignore) {}
			}
			for (PortI p : toUnpublish) {
				try { p.unpublishPort(); } catch (Exception ignore) {}
			}
		} catch (Exception ignore) {
			// Best-effort — never let cleanup itself break shutdown.
		}
	}
}
