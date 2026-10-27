package com.kntrel.mc.underilla.config.ucel;

import com.kntrel.mc.underilla.config.context.*;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.entity.*;
import com.kntrel.mc.underilla.config.property.Properties;
import com.kntrel.mc.underilla.config.property.Property;
import com.kntrel.mc.underilla.config.property.PropertyRepository;
import com.kntrel.mc.underilla.config.util.TypeToken;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Entry point for Underilla Configuration Expression Language grammars and compilers. */
public final class UCEL {
    private UCEL() {}

    /** Builds UCEL using the supplied repository for context roots and property chains. */
    public static <C> Grammar<C> grammarWith(TypeToken<C> contextType, PropertyRepository propertyRepository) {
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(propertyRepository, "propertyRepository");
        return Grammar.with(contextType)
                .standard()
                .literal(new IdLiteral())
                .operation(new IdentityEqualityOperation())
                .operation(new IdentityEqualityOperation(true))
                .operation(new MatchOperation())
                .operation(new IdentityMatchOperation())
                .operation(new IdentifiableMatchOperation())
                .operation(new TaggedOperation())
                .operation(new TaggedAnyOperation())
                .operation(new TaggedAllOperation())
                .runtime(new ContextRuntimeValue<>(contextClass(contextType), propertyRepository))
                .done();
    }

    public static <C> Grammar<C> grammarWith(Class<C> contextType, PropertyRepository propertyRepository) {
        return grammarWith(TypeToken.of(contextType), propertyRepository);
    }

    public static <C> Grammar<C> grammarWith(TypeToken<C> contextType) {
        return grammarWith(contextType, properties());
    }

    public static <C> Grammar<C> grammarWith(Class<C> contextType) {
        return grammarWith(contextType, properties());
    }

    public static <C> Compiler<C> compilerWith(TypeToken<C> contextType, PropertyRepository propertyRepository) {
        return Compiler.of(grammarWith(contextType, propertyRepository));
    }

    public static <C> Compiler<C> compilerWith(Class<C> contextType, PropertyRepository propertyRepository) {
        return Compiler.of(grammarWith(contextType, propertyRepository));
    }

    public static <C> Compiler<C> compilerWith(TypeToken<C> contextType) {
        return compilerWith(contextType, properties());
    }

    public static <C> Compiler<C> compilerWith(Class<C> contextType) {
        return compilerWith(contextType, properties());
    }

    /**
     * Immutable properties for every built-in context and entity, including structure origins
     * and bounds. Dual contexts expose bare world/biome/block roots from their source side;
     * chunk also refers to the source's evaluated chunk.
     */
    public static PropertyRepository properties() {
        return Defaults.PROPERTIES;
    }

    // PropertyRepository keys are raw classes, including for parameterized context tokens.
    @SuppressWarnings("unchecked")
    private static <C> Class<C> contextClass(TypeToken<C> contextType) {
        Type type = contextType.type();
        if (type instanceof ParameterizedType parameterized) type = parameterized.getRawType();
        if (type instanceof Class<?> raw) return (Class<C>) raw;
        throw new IllegalArgumentException("Context must have a concrete raw class: " + contextType);
    }

    private static final class Defaults {
        private static final PropertyRepository PROPERTIES = createProperties();

        private static PropertyRepository createProperties() {
            List<Property<?, ?>> properties = new ArrayList<>();
            for (Class<?> type : List.of(WorldContext.class, BiomeContext.class, BlockContext.class,
                    StructureContext.class, EntityContext.class, DualWorldContext.class,
                    DualBiomeContext.class, DualBlockContext.class, World.class, Chunk.class,
                    Biome.class, Block.class, Structure.class, Entity.class,
                    Structure.Origin.class, Structure.Bounds.class)) {
                properties.addAll(Properties.of(type));
            }
            worldAliases(properties, DualWorldContext.class, DualWorldContext::source);
            worldAliases(properties, DualBiomeContext.class, DualBiomeContext::source);
            worldAliases(properties, DualBlockContext.class, DualBlockContext::source);
            properties.add(Property.from("biome", DualBiomeContext.class, Biome.class, context -> context.source().biome()));
            properties.add(Property.from("biome", DualBlockContext.class, Biome.class, context -> context.source().biome()));
            properties.add(Property.from("block", DualBlockContext.class, Block.class, context -> context.source().block()));
            return PropertyRepository.from(properties);
        }

        private static <C> void worldAliases(List<Property<?, ?>> properties, Class<C> type,
                                             Function<C, ? extends WorldContext> source) {
            properties.add(Property.from("world", type, World.class, context -> source.apply(context).world()));
            properties.add(Property.from("chunk", type, Chunk.class, context -> source.apply(context).chunk()));
        }
    }
}
