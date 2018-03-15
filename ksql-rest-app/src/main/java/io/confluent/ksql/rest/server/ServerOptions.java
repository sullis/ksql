/**
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.ksql.rest.server;

import com.google.common.collect.ImmutableSet;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.restrictions.Once;
import com.github.rvesse.airline.annotations.restrictions.Required;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import javax.inject.Inject;

import io.confluent.ksql.rest.util.OptionsParser;

@Command(name = "server", description = "KSQL Cluster")
public class ServerOptions {


  static final String QUERIES_FILE_CONFIG = "ksql.queries.file";
  private final Set<String> systemPropertyExclusions
      = ImmutableSet.of("java.",
      "os.",
      "sun.",
      "user.",
      "line.separator",
      "path.separator",
      "file.separator");

  // Only here so that the help message generated by Help.help() is accurate
  @Inject
  public HelpOption help;

  @Once
  @Required
  @Arguments(
      title = "config-file",
      description = "A file specifying configs for the KSQL Server, KSQL, "
          + "and its underlying Kafka Streams instance(s). Refer to KSQL "
          + "documentation for a list of available configs.")
  private String propertiesFile;

  @Option(
      name = "--queries-file",
      description = "Path to the query file on the local machine.")
  private String queriesFile;


  public Properties loadProperties(final Supplier<Properties> overridePropertySupplier)
      throws IOException {

    final Properties properties = new Properties();
    try (final FileInputStream inputStream = new FileInputStream(propertiesFile)) {
      properties.load(inputStream);
    }

    final Properties sysProperties = overridePropertySupplier.get();
    sysProperties.stringPropertyNames()
        .stream()
        .filter(key ->
            systemPropertyExclusions.stream().noneMatch(key::startsWith))
        .forEach(key -> properties.put(key, sysProperties.getProperty(key)));
    return properties;
  }

  public Optional<String> getQueriesFile(final Properties properties) {
    if (queriesFile != null) {
      return Optional.of(queriesFile);
    }

    return Optional.ofNullable(properties.getProperty(QUERIES_FILE_CONFIG));
  }

  public static ServerOptions parse(final String...args) throws IOException {
    return OptionsParser.parse(args, ServerOptions.class);
  }
}
