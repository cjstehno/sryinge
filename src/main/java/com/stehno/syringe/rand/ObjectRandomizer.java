package com.stehno.syringe.rand;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.Character.toLowerCase;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;

public final class ObjectRandomizer<T> implements Randomizer<T> {

    private final Class<T> type;
    private final RandomizerConfigImpl randomizerConfig;

    private ObjectRandomizer(final Class<T> type, final RandomizerConfigImpl randomizerConfig) {
        this.type = type;
        this.randomizerConfig = randomizerConfig;
    }

    /**
     * Used to configure a Randomizer which will produce random objects built using the specified `RandomizerConfig`.
     *
     * @param type the type of object to be created and randomly populated.
     * @param config the randomizer configuration
     * @param <T> the type of the randomized object
     * @return the Randomizer which will produce random instances of the target class.
     */
    public static <T> Randomizer<T> randomize(final Class<T> type, final Consumer<RandomizerConfig> config) {
        final RandomizerConfigImpl randomizerConfig = new RandomizerConfigImpl();
        config.accept(randomizerConfig);
        return new ObjectRandomizer<>(type, randomizerConfig);
    }

    @Override public T one() {
        try {
            final T instance = type.newInstance();

            for (final Method setter : findSetters(type)) {
                final String propName = propertyName(setter);

                final Optional<Randomizer<?>> propRandomizer = randomizerConfig.propertyRandomizer(propName);
                final Optional<Randomizer<?>> propTypeRandomizer = randomizerConfig.propertyTypeRandomizer(setter.getParameterTypes()[0]);

                if (propRandomizer.isPresent()) {
                    setter.invoke(instance, propRandomizer.get().one());
                } else if (propTypeRandomizer.isPresent()) {
                    setter.invoke(instance, propTypeRandomizer.get().one());
                }
            }

            for (final Field field : findFields(type)) {
                final Optional<Randomizer<?>> fieldRandomizer = randomizerConfig.fieldRandomizer(field.getName());
                final Optional<Randomizer<?>> fieldTypeRandomizer = randomizerConfig.fieldTypeRandomizer(field.getType());

                if (fieldRandomizer.isPresent()) {
                    field.setAccessible(true);
                    field.set(instance, fieldRandomizer.get().one());
                } else if (fieldTypeRandomizer.isPresent()) {
                    field.setAccessible(true);
                    field.set(instance, fieldTypeRandomizer.get().one());
                }
            }

            return instance;

        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private static String propertyName(final Method method) {
        final String name = method.getName().substring(3);
        return toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // FIXME: these should be moved to a utility
    private static List<Method> findSetters(final Class<?> inClass) {
        final List<Method> setters = stream(inClass.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("set") && method.getParameterCount() == 1)
            .collect(Collectors.toList());

        Class<?> superclass = inClass.getSuperclass();
        while (superclass != Object.class) {
            stream(superclass.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("set") && method.getParameterCount() == 1)
                .forEach(setters::add);

            superclass = superclass.getSuperclass();
        }

        return setters;
    }

    private static List<Field> findFields(final Class<?> inClass) {
        final List<Field> fields = stream(inClass.getDeclaredFields()).collect(Collectors.toList());

        Class<?> superclass = inClass.getSuperclass();
        while (superclass != Object.class) {
            fields.addAll(asList(superclass.getDeclaredFields()));
            superclass = superclass.getSuperclass();
        }

        return fields;
    }
}
