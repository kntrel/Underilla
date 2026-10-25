package com.kntrel.mc.underilla.config.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.entity.World;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.kntrel.mc.underilla.config.property.Prop;
import com.kntrel.mc.underilla.config.property.Properties;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

public class PropertiesTest {

    @Test
    void discoversInheritedImplementedAndDefaultGetters() {
        List<Property<Sample, ?>> properties = Properties.of(Sample.class);

        assertTrue(names(properties).containsAll(Set.of("inherited", "interfaceName", "defaultName")));
        assertEquals("parent", property(properties, "inherited").get(new Sample()));
        assertEquals("implemented", property(properties, "interfaceName").get(new Sample()));
        assertEquals("default", property(properties, "defaultName").get(new Sample()));
        assertSame(Sample.class, property(properties, "interfaceName").objectType());
    }

    @Test
    void infersNamesAndHonorsExplicitValues() {
        var properties = Properties.of(Sample.class);

        assertTrue(names(properties).containsAll(Set.of("name", "uRL", "get", "plain", "Custom Name")));
        assertEquals("named", property(properties, "name").get(new Sample()));
        assertEquals("renamed", property(properties, "Custom Name").get(new Sample()));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_ERR)
    void skipsInvalidGettersAndLogsWarnings() {
        PrintStream original = System.err;
        ByteArrayOutputStream warnings = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(warnings, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            assertTrue(Properties.of(Invalid.class).isEmpty());
        } finally {
            System.setErr(original);
        }
        String log = warnings.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("WARN"), log);
        assertTrue(log.contains("withParameter"), log);
        assertTrue(log.contains("no parameters"), log);
        assertTrue(log.contains("withoutValue"), log);
        assertTrue(log.contains("must not return void"), log);
    }

    @Test
    void excludesUnannotatedAndNonPublicMethods() {
        Set<String> names = names(Properties.of(Sample.class));

        assertFalse(names.contains("unannotated"));
        assertFalse(names.contains("privateName"));
        assertFalse(names.contains("protectedName"));
    }

    @Test
    void invokesPrimitiveStaticAndNullReturningGetters() {
        var properties = Properties.of(Sample.class);

        assertEquals(42, property(properties, "count").get(new Sample()));
        assertSame(int.class, property(properties, "count").valueType());
        assertEquals("static", property(properties, "staticName").get(new Sample()));
        assertNull(property(properties, "nullable").get(new Sample()));
    }

    @Test
    void ignoresBridgeMethodsAndKeepsConcreteReturnTypes() {
        var properties = Properties.of(GenericSample.class);

        assertEquals(1, properties.size());
        assertSame(String.class, property(properties, "value").valueType());
        assertEquals("generic", property(properties, "value").get(new GenericSample()));
    }

    @Test
    void allowsPublicGettersOnNonPublicClasses() {
        var properties = Properties.of(HiddenSample.class);

        assertEquals("hidden", property(properties, "value").get(new HiddenSample()));
    }

    @Test
    void preservesUncheckedFailuresAndWrapsCheckedFailures() {
        var properties = Properties.of(FailingSample.class);
        FailingSample sample = new FailingSample();

        assertSame(sample.runtime, assertThrows(IllegalArgumentException.class,
                () -> property(properties, "runtime").get(sample)));
        assertSame(sample.error, assertThrows(AssertionError.class,
                () -> property(properties, "error").get(sample)));
        assertSame(sample.checked, assertThrows(IllegalStateException.class,
                () -> property(properties, "checked").get(sample)).getCause());
    }

    @Test
    void returnsImmutableResultsAndRejectsNullTypes() {
        assertThrows(UnsupportedOperationException.class, () -> Properties.of(Sample.class).clear());
        assertThrows(NullPointerException.class, () -> Properties.of(null));
        assertTrue(Properties.of(Object.class).isEmpty());
    }

    @Test
    void feedsDiscoveredPropertiesDirectlyIntoARepository() {
        PropertyRepository repository = PropertyRepository.from(Properties.of(Sample.class));

        assertEquals("implemented", repository.get(Sample.class, "interfaceName", String.class)
                .orElseThrow().get(new Sample()));
        assertEquals(42, repository.get(Sample.class, "count", int.class)
                .orElseThrow().get(new Sample()));
    }

    @Test
    void discoversAnnotatedEntityInterfaceGetters() {
        var properties = Properties.of(World.class);

        assertEquals(Set.of("dimension", "seed", "minY", "maxY"), names(properties));
        assertSame(World.class, property(properties, "seed").objectType());
        assertSame(long.class, property(properties, "seed").valueType());
    }

    private static Set<String> names(List<? extends Property<?, ?>> properties) {
        return properties.stream().map(Property::name).collect(Collectors.toSet());
    }

    private static <T> Property<T, ?> property(List<Property<T, ?>> properties, String name) {
        return properties.stream()
                .filter(property -> property.name().equals(name)).findFirst().orElseThrow();
    }

    public interface Named {
        @Prop
        String getInterfaceName();
        @Prop default String getDefaultName() { return "default"; }
    }

    public static class Parent {
        @Prop public String getInherited() { return "parent"; }
    }

    public static class Sample extends Parent implements Named {
        @Override public String getInterfaceName() { return "implemented"; }
        @Override public String getInherited() { return "parent"; }
        @Prop public String getName() { return "named"; }
        @Prop public String getURL() { return "url"; }
        @Prop public String get() { return "get"; }
        @Prop public String plain() { return "plain"; }
        @Prop("Custom Name") public String getRenamed() { return "renamed"; }
        @Prop public int getCount() { return 42; }
        @Prop public static String getStaticName() { return "static"; }
        @Prop public String nullable() { return null; }
        public String unannotated() { return "ignored"; }
        @Prop private String getPrivateName() { return "ignored"; }
        @Prop protected String getProtectedName() { return "ignored"; }
    }

    public static class Invalid {
        @Prop public String withParameter(String value) { return value; }
        @Prop public void withoutValue() {}
    }

    public interface Generic<T> {
        @Prop T getValue();
    }

    public static class GenericSample implements Generic<String> {
        @Override public String getValue() { return "generic"; }
    }

    private static class HiddenSample {
        @Prop public String getValue() { return "hidden"; }
    }

    public static class FailingSample {
        final IllegalArgumentException runtime = new IllegalArgumentException("runtime");
        final AssertionError error = new AssertionError("error");
        final IOException checked = new IOException("checked");
        @Prop public String runtime() { throw runtime; }
        @Prop public String error() { throw error; }
        @Prop public String checked() throws IOException { throw checked; }
    }
}
