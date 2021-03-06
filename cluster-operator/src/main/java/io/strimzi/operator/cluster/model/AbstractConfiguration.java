/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.strimzi.operator.cluster.model;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Abstract class for processing and generating configuration passed by the user.
 */
public abstract class AbstractConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AbstractConfiguration.class.getName());

    private final Properties options;

    /**
     * Constructor used to instantiate this class from String configuration. Should be used to create configuration
     * from the Assembly.
     *
     * @param configuration     Configuration in String format. Should contain zero or more lines with with key=value
     *                          pairs.
     * @param forbiddenOptions   List with configuration keys which are not allowed. All keys which start with one of
     *                           these keys will be ignored.
     */
    public AbstractConfiguration(String configuration, List<String> forbiddenOptions) {
        Properties options = new Properties();

        try (StringReader reader = new StringReader(configuration)) {
            options.load(reader);
        } catch (IOException | IllegalArgumentException e)   {
            log.error("Failed to read the configuration from String", e);
        }

        this.options = filterForbidden(options, forbiddenOptions);
    }

    /**
     * Converts the values from the configuration Json object to string or logs error if they have unsupported type
     *
     * @param json  JSON object with the configuration
     * @return  Map with configuration values as String
     */
    private Map<String, String> convertToStrings(JsonObject json)  {
        Map<String, String> map = new HashMap<>();

        for (String key : json.fieldNames())    {
            Object value = json.getValue(key);

            if (value instanceof String)    {
                map.put(key, (String) value);
            } else if (value instanceof Integer || value instanceof Long || value instanceof Boolean || value instanceof Double || value instanceof Float)    {
                map.put(key, String.valueOf(value));
            } else  {
                log.error("Unsupported type {} in configuration for key {}", value.getClass(), key);
            }
        }

        return map;
    }

    /**
     * Constructor used to instantiate this class from JsonObject. Should be used to create configuration from
     * ConfigMap / CRD.
     *
     * @param jsonOptions     Json object with configuration options as key ad value pairs.
     * @param forbiddenOptions   List with configuration keys which are not allowed. All keys which start with one of
     *                           these keys will be ignored.
     */
    public AbstractConfiguration(JsonObject jsonOptions, List<String> forbiddenOptions) {
        Properties options = new Properties();
        options.putAll(convertToStrings(jsonOptions));
        this.options = filterForbidden(options, forbiddenOptions);
    }

    /**
     * Filters forbidden values from the configuration.
     *
     * @param options   Properties object with configuration options
     * @param forbiddenOptions  List with configuration keys which are not allowed. All keys which start with one of
     *                          these keys will be ignored.
     * @return  New Properties object which contains only allowed options.
     */
    private Properties filterForbidden(Properties options, List<String> forbiddenOptions)   {
        Properties filtered = new Properties();

        Map<String, String> m = options.entrySet().stream()
            .filter(e -> !forbiddenOptions.stream().anyMatch(s -> {
                boolean forbidden = ((String) e.getKey()).toLowerCase(Locale.ENGLISH).startsWith(s);
                if (forbidden) {
                    log.warn("Configuration option \"{}\" is forbidden and will be ignored", e.getKey());
                } else {
                    log.trace("Configuration option \"{}\" is allowed and will be passed to the assembly", e.getKey());
                }
                return forbidden;
            }))
            .collect(Collectors.toMap(
                v -> (String) v.getKey(), v -> (String) v.getValue()
            ));

        filtered.putAll(m);

        return filtered;
    }

    /**
     * Generate configuration file in String format.
     *
     * @return  String with one or more lines containing key=value pairs with the configuration options.
     */
    public String getConfiguration() {
        String configuration = "";

        try (StringWriter writer = new StringWriter()) {
            options.store(writer, null);
            configuration = stripComments(writer.toString());
        } catch (IOException e) {
            log.error("Failed to store configuration into String", e);
        }

        return configuration;
    }

    /**
     * Strip comments from configuration string. Comments are lines starting with #. The default comment with timestamp
     * which is always added by Proeprties class is otherwise triggering rolling update when used in EnvVar.
     *
     * @param configuration String with configuration which should be strip of comments (lines starting with #)
     * @return  String with configuration without comments
     */
    private String stripComments(String configuration)  {
        StringBuilder builder = new StringBuilder();
        String[] lines = configuration.split("\n");

        for (String line : lines)   {
            if (!line.startsWith("#"))  {
                builder.append(line + "\n");
            }
        }

        return builder.toString();
    }
}
