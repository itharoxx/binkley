package hm.binkley.util.function;

import org.junit.Test;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static hm.binkley.util.function.Matching.matching;
import static hm.binkley.util.function.MatchingTest.B.B;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * {@code MatchingTest} tests {@code Matching}.
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 */
public final class MatchingTest {
    @Test
    public void shouldMatchWithFunction() {
        assertThat(matching(Integer.class, Object.class).
                when(is(2)).thenThrow(RuntimeException::new).
                when(is(0)).then(x -> 1).
                apply(0).get(), equalTo(1));
    }

    @Test
    public void shouldDefaultValue() {
        assertThat(matching(Integer.class, Object.class).
                none().then(1).
                apply(0).get(), equalTo(1));
    }

    @Test
    public void shouldDefaultSuppliedValue() {
        assertThat(matching(Integer.class, Object.class).
                none().then(() -> 1).
                apply(0).get(), equalTo(1));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrow() {
        matching(Integer.class, Object.class).
                none().thenThrow(RuntimeException::new).
                apply(0);
    }

    @Test
    public void shouldNotMatch() {
        assertFalse(matching(Integer.class, Integer.class).
                apply(0).isPresent());
    }

    @Test
    public void shouldType() {
        assertThat(matching(C.class, C.class).
                when(c -> c instanceof A).then(identity()).
                when(c -> c instanceof B).then(identity()).
                apply(B).get(), equalTo(B));
    }

    @Test
    public void shouldSideEffect() {
        final AtomicInteger i = new AtomicInteger();
        matching(Integer.class, Void.class).
                when(is(1)).then(i::set).
                apply(1);
        assertThat(i.get(), equalTo(1));
    }

    @Test
    public void shouldDoNothingWithoutApply() {
        matching(Object.class, Void.class).
                none().thenThrow(RuntimeException::new);
    }

    public static void main(final String... args) {
        asList(0, 1, 2, 3, 13, 14, null, -1).stream().
                peek(n -> out.print(format("%d -> ", n))).
                map(matching(Integer.class, Object.class).
                        when(Objects::isNull).then(n -> "!").
                        when(is(0)).then(nil()).
                        when(is(1)).then("one").
                        when(is(13)).then(() -> "unlucky").
                        when(is(14)).then(printIt()).
                        when(even()).then(scaleBy(3)).
                        when(gt(2)).then(dec()).
                        none().then("no match")).
                map(MatchingTest::toString).
                forEach(out::println);
        out.flush(); // Avoid mixing sout and serr

        matching(Integer.class, Void.class).
                none().thenThrow(RuntimeException::new).
                apply(0);
    }

    private static Predicate<Integer> is(final int n) {
        return m -> n == m;
    }

    private static <U> Supplier<U> nil() {
        return () -> null;
    }

    private static Consumer<Integer> printIt() {
        return out::println;
    }

    private static Predicate<Integer> even() {
        return n -> 0 == n % 2;
    }

    private static Function<Integer, Integer> scaleBy(final int factor) {
        return n -> n * factor;
    }

    private static Predicate<Integer> gt(final int b) {
        return n -> b < n;
    }

    private static Function<Integer, Integer> dec() {
        return n -> n - 1;
    }

    private static String toString(final Optional<Object> o) {
        return format("%s (%s)", o, o.map(Object::getClass));
    }

    interface C {
    }

    enum A
            implements C {
        A
    }

    enum B
            implements C {
        B
    }
}
