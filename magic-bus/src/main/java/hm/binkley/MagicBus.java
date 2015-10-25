package hm.binkley;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import javax.annotation.Nonnull;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * {@code MagicBus} is an intraprocess message bus.  Subscribers call {@link
 * #subscribe(Class, Mailbox)} to register mailboxes for receiving messages.
 * Senders call {@link #post(Object)} to send messages.
 * <p>
 * Delivery is synchronous.  Order of delivery is: <ol><li>Base type
 * subscribers before subtype subscribers &mdash; Subscribers to {@code
 * Object.class} before all others, subscribers to most specific type
 * last</li> <li>Earlier subscribers before later subscribers.  Ensuring
 * synchronous subscription order is the responsibility of the
 * caller</li></ol>
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 */
public interface MagicBus {
    /**
     * Subscribes the given <var>mailbox</var> for messages of
     * <var>messageType</var> and subtypes.
     *
     * @param messageType the message type token, never missing
     * @param mailbox the mailbox for delivery, never missing
     * @param <T> the message type
     */
    <T> void subscribe(@Nonnull Class<T> messageType,
            @Nonnull Mailbox<? super T> mailbox);

    /**
     * Unsubscribes the given <var>mailbox</var> for messages of
     * <var>messageType</var> and subtypes.
     *
     * @param messageType the message type token, never missing
     * @param mailbox the mailbox for delivery, never missing
     * @param <T> the message type
     */
    <T> void unsubscribe(@Nonnull Class<T> messageType,
            @Nonnull Mailbox<? super T> mailbox);

    /**
     * Publishes a message.  Subscribers to the type of <var>message</var> and
     * its supertypes recieve the message in their mailboxes.
     * <p>
     * If there is no eligible mailbox, <var>message</var> is sent to the
     * "unsubscribed messages" (dead letter) box.
     * <p>
     * If publishing fails (a mailbox throws a checked exception),
     * <var>message</var> is sent to the "failed message" box.
     *
     * @param message the message, never missing
     */
    void post(@Nonnull Object message);

    /**
     * Receives messages for a given type and subtypes.
     *
     * @param <T> the message type
     */
    @FunctionalInterface
    public interface Mailbox<T> {
        /**
         * Receives the given <var>message</var>.
         *
         * @param message the received message, never missing
         */
        void receive(@Nonnull final T message)
                throws Exception;
    }

    /** Details on unsubscribed (undelivered) messages. */
    @RequiredArgsConstructor(onConstructor = @__(@Nonnull))
    @ToString
    public static final class ReturnedMessage {
        @Nonnull
        public final MagicBus bus;
        @Nonnull
        public final Object message;
    }

    /** Details on failed messages (dead letters). */
    @RequiredArgsConstructor(onConstructor = @__(@Nonnull))
    @ToString
    public static final class FailedMessage {
        @Nonnull
        public final MagicBus bus;
        @Nonnull
        public final Mailbox mailbox;
        @Nonnull
        public final Object message;
        @Nonnull
        public final Exception failure;
    }
}
