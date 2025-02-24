/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <em>Consider this class private.</em>
 * <p>
 * Provides utility methods for managing {@link Properties} instances as well as access to the global configuration
 * system. Properties by default are loaded from the system properties, system environment, and a classpath resource
 * file named {@value #LOG4J_PROPERTIES_FILE_NAME}. Additional properties can be loaded by implementing a custom
 * {@link PropertySource} service and specifying it via a {@link ServiceLoader} file called
 * {@code META-INF/services/org.apache.logging.log4j.util.PropertySource} with a list of fully qualified class names
 * implementing that interface.
 * </p>
 *
 * @see PropertySource
 */
public final class PropertiesUtil {

    private static final String LOG4J_PROPERTIES_FILE_NAME = "log4j2.component.properties";
    private static final String LOG4J_SYSTEM_PROPERTIES_FILE_NAME = "log4j2.system.properties";
    private static final PropertiesUtil LOG4J_PROPERTIES = new PropertiesUtil(LOG4J_PROPERTIES_FILE_NAME, false);

    private final Environment environment;

    /**
     * Constructs a PropertiesUtil using a given Properties object as its source of defined properties.
     *
     * @param props the Properties to use by default
     */
    public PropertiesUtil(final Properties props) {
        this(new PropertiesPropertySource(props));
    }

    /**
     * Constructs a PropertiesUtil for a given properties file name on the classpath. The properties specified in this
     * file are used by default. If a property is not defined in this file, then the equivalent system property is used.
     *
     * @param propertiesFileName the location of properties file to load
     */
    public PropertiesUtil(final String propertiesFileName) {
        this(propertiesFileName, true);
    }

    private PropertiesUtil(final String propertiesFileName, final boolean useTccl) {
        this(new PropertyFilePropertySource(propertiesFileName, useTccl));
    }

    /**
     * Constructs a PropertiesUtil for a give property source as source of additional properties.
     * @param source a property source
     */
    PropertiesUtil(final PropertySource source) {
        this.environment = new Environment(source);
    }

    /**
     * Loads and closes the given property input stream. If an error occurs, log to the status logger.
     *
     * @param in     a property input stream.
     * @param source a source object describing the source, like a resource string or a URL.
     * @return a new Properties object
     */
    static Properties loadClose(final InputStream in, final Object source) {
        final Properties props = new Properties();
        if (null != in) {
            try {
                props.load(in);
            } catch (final IOException e) {
                LowLevelLogUtil.logException("Unable to read " + source, e);
            } finally {
                try {
                    in.close();
                } catch (final IOException e) {
                    LowLevelLogUtil.logException("Unable to close " + source, e);
                }
            }
        }
        return props;
    }

    /**
     * Returns the PropertiesUtil used by Log4j.
     *
     * @return the main Log4j PropertiesUtil instance.
     */
    public static PropertiesUtil getProperties() {
        return LOG4J_PROPERTIES;
    }

    /**
     * Returns {@code true} if the specified property is defined, regardless of its value (it may not have a value).
     *
     * @param name the name of the property to verify
     * @return {@code true} if the specified property is defined, regardless of its value
     */
    public boolean hasProperty(final String name) {
        return environment.containsKey(name);
    }

    /**
     * Gets the named property as a boolean value. If the property matches the string {@code "true"} (case-insensitive),
     * then it is returned as the boolean value {@code true}. Any other non-{@code null} text in the property is
     * considered {@code false}.
     *
     * @param name the name of the property to look up
     * @return the boolean value of the property or {@code false} if undefined.
     */
    public boolean getBooleanProperty(final String name) {
        return getBooleanProperty(name, false);
    }

    /**
     * Gets the named property as a boolean value.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the boolean value of the property or {@code defaultValue} if undefined.
     */
    public boolean getBooleanProperty(final String name, final boolean defaultValue) {
        final String prop = getStringProperty(name);
        return prop == null ? defaultValue : "true".equalsIgnoreCase(prop);
    }

    /**
     * Gets the named property as a boolean value.
     *
     * @param name                  the name of the property to look up
     * @param defaultValueIfAbsent  the default value to use if the property is undefined
     * @param defaultValueIfPresent the default value to use if the property is defined but not assigned
     * @return the boolean value of the property or {@code defaultValue} if undefined.
     */
    public boolean getBooleanProperty(final String name, final boolean defaultValueIfAbsent,
                                      final boolean defaultValueIfPresent) {
        final String prop = getStringProperty(name);
        return prop == null ? defaultValueIfAbsent
            : prop.isEmpty() ? defaultValueIfPresent : "true".equalsIgnoreCase(prop);
    }

    /**
     * Retrieves a property that may be prefixed by more than one string.
     * @param prefixes The array of prefixes.
     * @param key The key to locate.
     * @param supplier The method to call to derive the default value. If the value is null, null will be returned
     * if no property is found.
     * @return The value or null if it is not found.
     * @since 2.13.0
     */
    public Boolean getBooleanProperty(final String[] prefixes, String key, Supplier<Boolean> supplier) {
        for (String prefix : prefixes) {
            if (hasProperty(prefix + key)) {
                return getBooleanProperty(prefix + key);
            }
        }
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Gets the named property as a Charset value.
     *
     * @param name the name of the property to look up
     * @return the Charset value of the property or {@link Charset#defaultCharset()} if undefined.
     */
    public Charset getCharsetProperty(final String name) {
        return getCharsetProperty(name, Charset.defaultCharset());
    }

    /**
     * Gets the named property as a Charset value. If we cannot find the named Charset, see if it is mapped in
     * file {@code Log4j-charsets.properties} on the class path.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the Charset value of the property or {@code defaultValue} if undefined.
     */
    public Charset getCharsetProperty(final String name, final Charset defaultValue) {
        final String charsetName = getStringProperty(name);
        if (charsetName == null) {
            return defaultValue;
        }
        if (Charset.isSupported(charsetName)) {
            return Charset.forName(charsetName);
        }
        final ResourceBundle bundle = getCharsetsResourceBundle();
        if (bundle.containsKey(name)) {
            final String mapped = bundle.getString(name);
            if (Charset.isSupported(mapped)) {
                return Charset.forName(mapped);
            }
        }
        LowLevelLogUtil.log("Unable to get Charset '" + charsetName + "' for property '" + name + "', using default "
            + defaultValue + " and continuing.");
        return defaultValue;
    }

    /**
     * Gets the named property as a double.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the parsed double value of the property or {@code defaultValue} if it was undefined or could not be parsed.
     */
    public double getDoubleProperty(final String name, final double defaultValue) {
        final String prop = getStringProperty(name);
        if (prop != null) {
            try {
                return Double.parseDouble(prop);
            } catch (final Exception ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * Gets the named property as an integer.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the parsed integer value of the property or {@code defaultValue} if it was undefined or could not be
     * parsed.
     */
    public int getIntegerProperty(final String name, final int defaultValue) {
        final String prop = getStringProperty(name);
        if (prop != null) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (final Exception ignored) {
                // ignore
            }
        }
        return defaultValue;
    }

    /**
     * Retrieves a property that may be prefixed by more than one string.
     * @param prefixes The array of prefixes.
     * @param key The key to locate.
     * @param supplier The method to call to derive the default value. If the value is null, null will be returned
     * if no property is found.
     * @return The value or null if it is not found.
     * @since 2.13.0
     */
    public Integer getIntegerProperty(final String[] prefixes, String key, Supplier<Integer> supplier) {
        for (String prefix : prefixes) {
            if (hasProperty(prefix + key)) {
                return getIntegerProperty(prefix + key, 0);
            }
        }
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Gets the named property as a long.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the parsed long value of the property or {@code defaultValue} if it was undefined or could not be parsed.
     */
    public long getLongProperty(final String name, final long defaultValue) {
        final String prop = getStringProperty(name);
        if (prop != null) {
            try {
                return Long.parseLong(prop);
            } catch (final Exception ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * Retrieves a property that may be prefixed by more than one string.
     * @param prefixes The array of prefixes.
     * @param key The key to locate.
     * @param supplier The method to call to derive the default value. If the value is null, null will be returned
     * if no property is found.
     * @return The value or null if it is not found.
     * @since 2.13.0
     */
    public Long getLongProperty(final String[] prefixes, String key, Supplier<Long> supplier) {
        for (String prefix : prefixes) {
            if (hasProperty(prefix + key)) {
                return getLongProperty(prefix + key, 0);
            }
        }
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Retrieves a Duration where the String is of the format nnn[unit] where nnn represents an integer value
     * and unit represents a time unit.
     * @param name The property name.
     * @param defaultValue The default value.
     * @return The value of the String as a Duration or the default value, which may be null.
     * @since 2.13.0
     */
    public Duration getDurationProperty(final String name, Duration defaultValue) {
        final String prop = getStringProperty(name);
        if (prop != null) {
            return TimeUnit.getDuration(prop);
        }
        return defaultValue;
    }

    /**
     * Retrieves a property that may be prefixed by more than one string.
     * @param prefixes The array of prefixes.
     * @param key The key to locate.
     * @param supplier The method to call to derive the default value. If the value is null, null will be returned
     * if no property is found.
     * @return The value or null if it is not found.
     * @since 2.13.0
     */
    public Duration getDurationProperty(final String[] prefixes, String key, Supplier<Duration> supplier) {
        for (String prefix : prefixes) {
            if (hasProperty(prefix + key)) {
                return getDurationProperty(prefix + key, null);
            }
        }
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Retrieves a property that may be prefixed by more than one string.
     * @param prefixes The array of prefixes.
     * @param key The key to locate.
     * @param supplier The method to call to derive the default value. If the value is null, null will be returned
     * if no property is found.
     * @return The value or null if it is not found.
     * @since 2.13.0
     */
    public String getStringProperty(final String[] prefixes, String key, Supplier<String> supplier) {
        for (String prefix : prefixes) {
            String result = getStringProperty(prefix + key);
            if (result != null) {
                return result;
            }
        }
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Gets the named property as a String.
     *
     * @param name the name of the property to look up
     * @return the String value of the property or {@code null} if undefined.
     */
    public String getStringProperty(final String name) {
        return environment.get(name);
    }

    /**
     * Gets the named property as a String.
     *
     * @param name         the name of the property to look up
     * @param defaultValue the default value to use if the property is undefined
     * @return the String value of the property or {@code defaultValue} if undefined.
     */
    public String getStringProperty(final String name, final String defaultValue) {
        final String prop = getStringProperty(name);
        return prop == null ? defaultValue : prop;
    }

    /**
     * Return the system properties or an empty Properties object if an error occurs.
     *
     * @return The system properties.
     */
    public static Properties getSystemProperties() {
        try {
            return new Properties(System.getProperties());
        } catch (final SecurityException ex) {
            LowLevelLogUtil.logException("Unable to access system properties.", ex);
            // Sandboxed - can't read System Properties
            return new Properties();
        }
    }

    /**
     * Reloads all properties. This is primarily useful for unit tests.
     *
     * @since 2.10.0
     */
    public void reload() {
        environment.reload();
    }

    /**
     * Provides support for looking up global configuration properties via environment variables, property files,
     * and system properties, in three variations:
     * <p>
     * Normalized: all log4j-related prefixes removed, remaining property is camelCased with a log4j2 prefix for
     * property files and system properties, or follows a LOG4J_FOO_BAR format for environment variables.
     * <p>
     * Legacy: the original property name as defined in the source pre-2.10.0.
     * <p>
     * Tokenized: loose matching based on word boundaries.
     *
     * @since 2.10.0
     */
    private static class Environment {

        private final Set<PropertySource> sources = new TreeSet<>(new PropertySource.Comparator());
        /**
         * Maps a key to its value in the lowest priority source that contains it.
         */
        private final Map<String, String> literal = new ConcurrentHashMap<>();
        /**
         * Maps a key to the value associated to its normalization in the lowest
         * priority source that contains it.
         */
        private final Map<String, String> normalized = new ConcurrentHashMap<>();
        private final Map<List<CharSequence>, String> tokenized = new ConcurrentHashMap<>();

        private Environment(final PropertySource propertySource) {
            PropertyFilePropertySource sysProps = new PropertyFilePropertySource(LOG4J_SYSTEM_PROPERTIES_FILE_NAME, false);
            try {
                sysProps.forEach((key, value) -> {
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                });
            } catch (SecurityException ex) {
                // Access to System Properties is restricted so just skip it.
            }
            sources.add(propertySource);
            // We don't log anything on the status logger.
            ServiceLoaderUtil.loadServices(PropertySource.class, MethodHandles.lookup(), false, false)
                    .forEach(sources::add);

            reload();
        }

        private synchronized void reload() {
            literal.clear();
            normalized.clear();
            tokenized.clear();
            // 1. Collects all property keys from enumerable sources.
            final Set<String> keys = new HashSet<>();
            sources.stream()
                   .map(PropertySource::getPropertyNames)
                   .reduce(keys, (left, right) -> {
                       left.addAll(right);
                       return left;
                   });
            // 2. Fills the property caches. Sources with higher priority values don't override the previous ones.
            keys.stream()
                .filter(Objects::nonNull)
                .forEach(key -> {
                    final List<CharSequence> tokens = PropertySource.Util.tokenize(key);
                    sources.forEach(source -> {
                        final String value = source.getProperty(key);
                        if (value != null) {
                            literal.putIfAbsent(key, value);
                            if (!tokens.isEmpty()) {
                                tokenized.putIfAbsent(tokens, value);
                            }
                        }
                        final CharSequence normalKey = source.getNormalForm(tokens);
                        if (normalKey != null) {
                            final String normalValue = source.getProperty(normalKey.toString());
                            if (normalValue != null) {
                                normalized.putIfAbsent(key, normalValue);
                            }
                        }
                    });
                });
        }

        private String get(final String key) {
            if (normalized.containsKey(key)) {
                return normalized.get(key);
            }
            if (literal.containsKey(key)) {
                return literal.get(key);
            }
            final List<CharSequence> tokens = PropertySource.Util.tokenize(key);
            for (final PropertySource source : sources) {
                final String normalKey = Objects.toString(source.getNormalForm(tokens), null);
                if (normalKey != null && source.containsProperty(normalKey)) {
                    final String normalValue = source.getProperty(normalKey);
                    // Caching previously unknown keys breaks many tests which set and unset system properties
                    // normalized.put(key, normalValue);
                    return normalValue;
                }
                if (source.containsProperty(key)) {
                    final String value = source.getProperty(key);
                    // literal.put(key, value);
                    return value;
                }
            }
            return tokenized.get(tokens);
        }

        private boolean containsKey(final String key) {
            List<CharSequence> tokens = PropertySource.Util.tokenize(key);
            return normalized.containsKey(key) ||
                   literal.containsKey(key) ||
                   tokenized.containsKey(tokens) ||
                   sources.stream().anyMatch(s -> {
                        final CharSequence normalizedKey = s.getNormalForm(tokens);
                        return s.containsProperty(key) || normalizedKey != null && s.containsProperty(normalizedKey.toString());
                   });
        }
    }

    /**
     * Extracts properties that start with or are equals to the specific prefix and returns them in a new Properties
     * object with the prefix removed.
     *
     * @param properties The Properties to evaluate.
     * @param prefix     The prefix to extract.
     * @return The subset of properties.
     */
    public static Properties extractSubset(final Properties properties, final String prefix) {
        final Properties subset = new Properties();

        if (prefix == null || prefix.length() == 0) {
            return subset;
        }

        final String prefixToMatch = prefix.charAt(prefix.length() - 1) != '.' ? prefix + '.' : prefix;

        final List<String> keys = new ArrayList<>();

        for (final String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefixToMatch)) {
                subset.setProperty(key.substring(prefixToMatch.length()), properties.getProperty(key));
                keys.add(key);
            }
        }
        for (final String key : keys) {
            properties.remove(key);
        }

        return subset;
    }

    static ResourceBundle getCharsetsResourceBundle() {
        return ResourceBundle.getBundle("Log4j-charsets");
    }

    /**
     * Partitions a properties map based on common key prefixes up to the first period.
     *
     * @param properties properties to partition
     * @return the partitioned properties where each key is the common prefix (minus the period) and the values are
     * new property maps without the prefix and period in the key
     * @since 2.6
     */
    public static Map<String, Properties> partitionOnCommonPrefixes(final Properties properties) {
        return partitionOnCommonPrefixes(properties, false);
    }

    /**
     * Partitions a properties map based on common key prefixes up to the first period.
     *
     * @param properties properties to partition
     * @param includeBaseKey when true if a key exists with no '.' the key will be included.
     * @return the partitioned properties where each key is the common prefix (minus the period) and the values are
     * new property maps without the prefix and period in the key
     * @since 2.17.2
     */
    public static Map<String, Properties> partitionOnCommonPrefixes(final Properties properties,
            final boolean includeBaseKey) {
        final Map<String, Properties> parts = new ConcurrentHashMap<>();
        for (final String key : properties.stringPropertyNames()) {
            final int idx = key.indexOf('.');
            if (idx < 0) {
                if (includeBaseKey) {
                    if (!parts.containsKey(key)) {
                        parts.put(key, new Properties());
                    }
                    parts.get(key).setProperty("", properties.getProperty(key));
                }
                continue;
            }
            final String prefix = key.substring(0, idx);
            if (!parts.containsKey(prefix)) {
                parts.put(prefix, new Properties());
            }
            parts.get(prefix).setProperty(key.substring(idx + 1), properties.getProperty(key));
        }
        return parts;
    }

    /**
     * Returns true if system properties tell us we are running on Windows.
     *
     * @return true if system properties tell us we are running on Windows.
     */
    public boolean isOsWindows() {
        return getStringProperty("os.name", "").startsWith("Windows");
    }

    private enum TimeUnit {
        NANOS("ns,nano,nanos,nanosecond,nanoseconds", ChronoUnit.NANOS),
        MICROS("us,micro,micros,microsecond,microseconds", ChronoUnit.MICROS),
        MILLIS("ms,milli,millis,millsecond,milliseconds", ChronoUnit.MILLIS),
        SECONDS("s,second,seconds", ChronoUnit.SECONDS),
        MINUTES("m,minute,minutes", ChronoUnit.MINUTES),
        HOURS("h,hour,hours", ChronoUnit.HOURS),
        DAYS("d,day,days", ChronoUnit.DAYS);

        private final String[] descriptions;
        private final ChronoUnit timeUnit;

        TimeUnit(String descriptions, ChronoUnit timeUnit) {
            this.descriptions = descriptions.split(",");
            this.timeUnit = timeUnit;
        }

        ChronoUnit getTimeUnit() {
            return this.timeUnit;
        }

        static Duration getDuration(String time) {
            String value = time.trim();
            TemporalUnit temporalUnit = ChronoUnit.MILLIS;
            long timeVal = 0;
            for (TimeUnit timeUnit : values()) {
                for (String suffix : timeUnit.descriptions) {
                    if (value.endsWith(suffix)) {
                        temporalUnit = timeUnit.timeUnit;
                        timeVal = Long.parseLong(value.substring(0, value.length() - suffix.length()));
                    }
                }
            }
            return Duration.of(timeVal, temporalUnit);
        }
    }
}
