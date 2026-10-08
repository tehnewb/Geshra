package geshra.event;

/**
 * Cold-path registration metadata. These objects are never traversed by {@link EventPublisher#publish(Event)}.
 *
 * <p>Priority and sequence determine the order used when rebuilding immutable routes.
 * The handler is a borrowed reference; this record does not invoke it or manage its lifetime.</p>
 *
 * @author Albert Beaupre
 */
record EventRegistration(EventHandler<Event> handler, int priority, long sequence) {

}
