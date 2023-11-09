package com.meltmedia.jgroups.aws;

import org.jgroups.util.Tuple;
import software.amazon.awssdk.services.ec2.model.Filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FilterUtils {
  private final TagsUtils tagsUtils;
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private final Optional<List<Filter>> awsFilters;

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public FilterUtils(final String configuredFilters, final TagsUtils tagsUtils) {
    this.tagsUtils = tagsUtils;
    this.awsFilters = Optional.ofNullable(configuredFilters)
        .map(String::trim)
        .filter(filters -> !filters.isEmpty())
        .map(FilterUtils::parseFilters);
  }

  /**
   * @return an optional list of parsed tag names
   */
  public Optional<List<Filter>> getAwsFilters() {
    return awsFilters;
  }

  /**
   * Takes the list of configured tag names and compares it with tagsUtils on the ec2 instance.
   * FilterUtils (tag:key=value) will be created for all matches.
   *
   * @return a list of filters for instances that share common tags with
   *   this instance.
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public List<Filter> instanceTagNamesToFilters() {
    return tagsUtils.getAwsTagNames().map(tagNames ->
            tagsUtils.getInstanceTags().stream()
            .filter(tag -> tagNames.contains(tag.key()))
            .map(tag -> Filter.builder().name("tag:" + tag.key()).values(tag.value()).build())
            .collect(Collectors.toList())
    ).orElseGet(Collections::emptyList);
  }

  /**
   * Parses a filter string into a list of Filter objects that is suitable for
   * the AWS describeInstances method call.
   * <p>
   * <h3>Format:</h3>
   * <p>
   * <blockquote>
   * <p>
   * <pre>
   *   FILTERS ::= &lt;FILTER&gt; ( ';' &lt;FILTER&gt; )*
   *   FILTER ::= &lt;NAME&gt; '=' &lt;VALUE&gt; (',' &lt;VALUE&gt;)*
   * </pre>
   * <p>
   * </blockquote>
   * </p>
   *
   * @param filters the filter string to parse.
   * @return the list of filters that represent the filter string.
   */
  private static List<Filter> parseFilters(String filters) {
    return Arrays.stream(filters.split("\\s*;\\s*"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(FilterUtils::splitToTuple)
        .map(keyAndValue -> Filter.builder()
                .name(keyAndValue.getVal1())
                .values(splitValues(keyAndValue.getVal2()))
                .build()
        )
        .collect(Collectors.toList());
  }

  private static Tuple<String, String> splitToTuple(String filter) {
    final String[] keyValues = filter.split("\\s*=\\s*");
    if (keyValues.length != 2 || keyValues[0].length() == 0 || keyValues[1].length() == 0) {
      throw new IllegalArgumentException("Could not process key value pair '" + filter + "'");
    }
    return new Tuple<>(keyValues[0].trim(), keyValues[1].trim());
  }

  private static List<String> splitValues(String filterValues) {
    return Arrays.stream(filterValues.split("\\s*,\\s*"))
        .map(String::trim)
        .collect(Collectors.toList());
  }
}
