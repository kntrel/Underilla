package com.kntrel.mc.underilla.config.yaml;

import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.model.HorizontalBound;
import com.kntrel.mc.underilla.config.model.ReplacementRule;
import com.kntrel.mc.underilla.config.model.Selector;
import com.kntrel.mc.underilla.config.model.UnderillaConfig;
import com.kntrel.mc.underilla.config.model.WorldSource;
import com.kntrel.mc.underilla.config.util.TypeToken;
import com.kntrel.mc.underilla.core.api.ID;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.reader.UnicodeReader;

/**
 * Loads one YAML document into the immutable model. Missing settings inherit bundled YAML defaults;
 * explicit nulls, unknown keys, duplicate keys, and invalid values are rejected.
 * Nullable source paths/metadata may explicitly be null. Every selector is compiled before returning.
 * Instances and compiler registrations are immutable; each load creates its own parser.
 * Attached compilers own grammar/root availability and their evaluation thread safety.
 */
public final class YamlConfigLoader {
    private static final Set<String> SOURCE_KEYS = Set.of("world-path", "dimension", "region-path",
            "entities-path", "name", "min-height", "max-height");

    private final Map<Type, Compiler<?>> compilers;

    public YamlConfigLoader() { this(Map.of()); }

    private YamlConfigLoader(Map<Type, Compiler<?>> compilers) { this.compilers = Map.copyOf(compilers); }

    /** Returns a new loader using this compiler for selectors with the given context type. */
    public <C> YamlConfigLoader withCompiler(Class<C> context, Compiler<C> compiler) {
        return withCompiler(TypeToken.of(context), compiler);
    }

    public <C> YamlConfigLoader withCompiler(TypeToken<C> context, Compiler<C> compiler) {
        var copy = new HashMap<>(compilers);
        copy.put(Objects.requireNonNull(context, "context").type(), Objects.requireNonNull(compiler, "compiler"));
        return new YamlConfigLoader(copy);
    }

    /** Compiles the complete bundled config.yml with this loader's registered compilers. */
    public UnderillaConfig loadDefaults() { return map(null, defaultDocument()); }

    /** Loads a file and closes its stream. Relative configuration paths remain unresolved. */
    public UnderillaConfig load(Path file) throws IOException {
        try (var input = Files.newInputStream(Objects.requireNonNull(file, "file"))) {
            return load(input);
        }
    }

    /** Loads a caller-owned stream, recognizing the YAML byte-order mark; does not close it. */
    public UnderillaConfig load(InputStream input) {
        return load(new UnicodeReader(Objects.requireNonNull(input, "input")));
    }

    /** Loads a caller-owned reader without closing it. An empty document uses all defaults. */
    public UnderillaConfig load(Reader input) {
        return map(document(input), defaultDocument());
    }

    private static Object document(Reader input) {
        Objects.requireNonNull(input, "input");
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        var yaml = new Yaml(new SafeConstructor(options));
        Object document;
        try {
            document = yaml.load(input);
        } catch (YAMLException exception) {
            throw new ConfigMappingException("$", "Invalid YAML: " + exception.getMessage(), exception);
        }
        return document;
    }

    /** Loads YAML content, rather than a file name. */
    public UnderillaConfig load(String yaml) {
        return load(new StringReader(Objects.requireNonNull(yaml, "yaml")));
    }

    private static Object defaultDocument() {
        try (var input = YamlConfigLoader.class.getResourceAsStream("config.yml")) {
            if (input == null) throw new IllegalStateException("Bundled config.yml is missing");
            return document(new UnicodeReader(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled config.yml", exception);
        }
    }

    private UnderillaConfig map(Object document, Object defaults) {
        return (UnderillaConfig) convert(document == null ? Map.of() : document,
                UnderillaConfig.class, defaults, "$");
    }

    private Object convert(Object value, Type type, Object fallback, String path) {
        return convert(value, type, fallback, path, Map.of());
    }

    private Object convert(Object value, Type type, Object fallback, String path, Map<TypeVariable<?>, Type> variables) {
        type = resolve(type, variables);
        Class<?> raw = type instanceof ParameterizedType generic ? (Class<?>) generic.getRawType() : (Class<?>) type;
        if (raw == WorldSource.class && value == null) return null;
        if (value == null) throw error(path, "Value must not be null");
        try {
            if (raw == String.class) return text(value, path);
            if (raw == boolean.class) {
                if (value instanceof Boolean) return value;
                throw error(path, "Expected a boolean");
            }
            if (raw == int.class) return integer(value, path);
            if (raw == ID.class) return ID.of(text(value, path));
            if (raw == Path.class) return Path.of(text(value, path));
            if (raw == HorizontalBound.class) {
                if (value instanceof String text && text.equalsIgnoreCase("auto")) return HorizontalBound.auto();
                return HorizontalBound.fixed(integer(value, path));
            }
            if (raw == Selector.class && type instanceof ParameterizedType generic) {
                return selector(value, resolve(generic.getActualTypeArguments()[0], variables), path);
            }
            if (raw == WorldSource.class) return source(mapping(value, path), fallback, path);
            if (raw.isEnum()) return enumValue(value, raw, path);
            if (raw == List.class && type instanceof ParameterizedType generic) {
                if (!(value instanceof List<?> list)) throw error(path, "Expected a list");
                var result = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    result.add(convert(list.get(i), generic.getActualTypeArguments()[0], null, path + "[" + i + "]", variables));
                }
                return List.copyOf(result);
            }
            if (raw.isRecord()) return record(mapping(value, path), type, raw, fallback, path, variables);
        } catch (ConfigMappingException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigMappingException(path, exception.getMessage(), exception);
        }
        throw new IllegalStateException("Unsupported configuration model type: " + type);
    }

    private Object record(Map<String, Object> values, Type declaredType, Class<?> type, Object fallback,
                          String path, Map<TypeVariable<?>, Type> inherited) {
        var variables = new HashMap<>(inherited);
        if (declaredType instanceof ParameterizedType generic) {
            for (int i = 0; i < type.getTypeParameters().length; i++) {
                variables.put(type.getTypeParameters()[i], resolve(generic.getActualTypeArguments()[i], inherited));
            }
        }
        Map<String, Object> defaults = fallback == null ? Map.of() : mapping(fallback, path);
        RecordComponent[] components = type.getRecordComponents();
        var keys = Arrays.stream(components).map(component -> key(type, component)).toList();
        checkKeys(values, Set.copyOf(keys), path);
        var arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String key = keys.get(i);
            Object defaultValue = defaults.get(key);
            if (values.containsKey(key)) {
                arguments[i] = convert(values.get(key), component.getGenericType(), defaultValue, child(path, key), variables);
            } else if (defaults.containsKey(key)) {
                arguments[i] = convert(defaultValue, component.getGenericType(), null, child(path, key), variables);
            } else {
                throw error(child(path, key), "Missing required setting");
            }
        }
        try {
            Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            return type.getConstructor(parameterTypes).newInstance(arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new ConfigMappingException(path, cause.getMessage(), cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct configuration model " + type, exception);
        }
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> variables) {
        while (type instanceof TypeVariable<?> variable && variables.containsKey(variable)) type = variables.get(variable);
        return type;
    }

    private static String key(Class<?> type, RecordComponent component) {
        if (type == ReplacementRule.class && component.getName().equals("condition")) return "if";
        return component.getName().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private WorldSource source(Map<String, Object> values, Object fallback, String path) {
        checkKeys(values, SOURCE_KEYS, path);
        if (values.containsKey("region-path") && (values.containsKey("world-path") || values.containsKey("dimension"))) {
            throw error(path, "region-path cannot be combined with world-path or dimension");
        }
        var effective = new LinkedHashMap<>(fallback == null ? Map.<String, Object>of() : mapping(fallback, path));
        if (values.containsKey("region-path")) {
            effective.remove("world-path");
            effective.remove("dimension");
        } else if (values.containsKey("world-path") || values.containsKey("dimension")) {
            effective.remove("region-path");
        }
        effective.putAll(values);
        WorldSource.Metadata metadata = metadata(effective, WorldSource.Metadata.inferred(), path);
        Path entities = optionalPath(effective.get("entities-path"), child(path, "entities-path"));
        if (effective.containsKey("region-path")) {
            return new WorldSource.RegionDirectory((Path) convert(effective.get("region-path"), Path.class, null,
                    child(path, "region-path")), entities, metadata);
        }
        if (effective.containsKey("world-path")) {
            Path worldPath = (Path) convert(effective.get("world-path"), Path.class, null, child(path, "world-path"));
            ID dimension = effective.containsKey("dimension")
                    ? (ID) convert(effective.get("dimension"), ID.class, null, child(path, "dimension")) : ID.of("overworld");
            return new WorldSource.SavedWorld(worldPath, dimension, entities, metadata);
        }
        throw error(path, "Expected world-path or region-path");
    }

    private static WorldSource.Metadata metadata(Map<String, Object> values, WorldSource.Metadata fallback, String path) {
        String name = fallback.name();
        Integer min = fallback.minHeight();
        Integer max = fallback.maxHeight();
        if (values.containsKey("name")) name = values.get("name") == null ? null : text(values.get("name"), child(path, "name"));
        if (values.containsKey("min-height")) {
            min = values.get("min-height") == null ? null : integer(values.get("min-height"), child(path, "min-height"));
        }
        if (values.containsKey("max-height")) {
            max = values.get("max-height") == null ? null : integer(values.get("max-height"), child(path, "max-height"));
        }
        return new WorldSource.Metadata(name, min, max);
    }

    private Path optionalPath(Object value, String path) {
        return value == null ? null : (Path) convert(value, Path.class, null, path);
    }

    private Selector<?> selector(Object value, Type context, String path) {
        if (value instanceof List<?> list) {
            var selectors = new ArrayList<Selector<Object>>();
            for (int i = 0; i < list.size(); i++) {
                String entryPath = path + "[" + i + "]";
                selectors.add(castSelector(compileSelector(context, expression(list.get(i), entryPath), entryPath)));
            }
            return Selector.anyOf(selectors);
        }
        return compileSelector(context, expression(value, path), path);
    }

    // Each element is compiled with the same resolved context type before OR composition.
    @SuppressWarnings("unchecked")
    private static Selector<Object> castSelector(Selector<?> selector) { return (Selector<Object>) selector; }

    private static String expression(Object value, String path) {
        String expression;
        if (value instanceof String text) expression = text;
        else if (value instanceof Boolean bool) expression = bool.toString();
        else throw error(path, "Expected a UCEL expression string or boolean");
        if (expression.isBlank()) throw error(path, "Selector expressions must not be blank");
        return expression;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Object value, Class<?> type, String path) {
        String text = text(value, path).strip().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf((Class<? extends Enum>) type, text);
        } catch (IllegalArgumentException exception) {
            throw error(path, "Expected one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    private static Map<String, Object> mapping(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw error(path, "Expected a mapping");
        var result = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw error(path, "Mapping keys must be strings");
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static String text(Object value, String path) {
        if (value instanceof String text) return text;
        throw error(path, "Expected a string");
    }

    private static int integer(Object value, String path) {
        try {
            if (value instanceof BigInteger integer) return integer.intValueExact();
            if (value instanceof Long integer) return Math.toIntExact(integer);
            if (value instanceof Integer integer) return integer;
        } catch (ArithmeticException exception) {
            throw error(path, "Integer is outside the 32-bit range");
        }
        throw error(path, "Expected an integer");
    }

    private static void checkKeys(Map<String, Object> values, Set<String> allowed, String path) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) throw error(child(path, key), "Unknown setting");
        }
    }

    private static String child(String path, String key) { return path.equals("$") ? key : path + "." + key; }

    private static ConfigMappingException error(String path, String message) { return new ConfigMappingException(path, message); }

    private Selector<?> compileSelector(Type context, String expression, String path) {
        Compiler<?> compiler = compilers.get(context);
        if (compiler == null) throw new ConfigMappingException(path, "No DSL compiler registered for " + context.getTypeName());
        try {
            return Selector.compile(compiler, expression);
        } catch (IllegalArgumentException exception) {
            throw new ConfigMappingException(path, "Invalid selector: " + exception.getMessage(), exception);
        }
    }
}
