package com.meltmedia.jgroups.aws;

import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.paginators.DescribeInstancesIterable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagsUtils {
  private final Ec2Client ec2;
  private final EC2MetadataUtils.InstanceInfo instanceInfo;
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private final Optional<List<String>> awsTagNames;

  public TagsUtils(final Ec2Client ec2, final EC2MetadataUtils.InstanceInfo instanceInfo, final String configuredTags) {
    this.ec2 = ec2;
    this.instanceInfo = instanceInfo;
    this.awsTagNames = Optional.ofNullable(configuredTags)
        .map(String::trim)
        .filter(tags -> !tags.isEmpty())
        .map(TagsUtils::parseTagNames);
  }

  /**
   * @return an optional list of parsed tag names
   */
  public Optional<List<String>> getAwsTagNames() {
    return awsTagNames;
  }

  /**
   * Returns all of the tags defined on the EC2 current instance
   * instanceId.
   *
   * @return a list of the Tag objects that were found on the instance.
   */
  public List<Tag> getInstanceTags() {
    final DescribeInstancesIterable response = ec2
            .describeInstancesPaginator(b -> b.instanceIds(instanceInfo.getInstanceId()));

    return response.reservations().stream()
        .flatMap(reservation -> reservation.instances().stream())
        .flatMap(instance -> instance.tags().stream())
        .collect(Collectors.toList());

  }

  /**
   * Configured tags will be validated against the instance tags.
   * If one or more tags are missing on the instance, an exception will be thrown.
   *
   * @throws IllegalStateException
   */
  public TagsUtils validateTags() {
    final List<String> instanceTags = getInstanceTags().stream()
        .map(Tag::key)
        .collect(Collectors.toList());

    final List<String> missingTags = getAwsTagNames().map(List::stream).orElse(Stream.empty())
        .filter(configuredTag -> !instanceTags.contains(configuredTag))
        .collect(Collectors.toList());

    if(!missingTags.isEmpty()) {
      throw new IllegalStateException("expected instance tag(s) missing: " + String.join(", ", missingTags));
    }

    return this;
  }

  /**
   * Parses a comma separated list of tag names.
   *
   * @param tagNames a comma separated list of tag names.
   * @return the list of tag names.
   */
  private static List<String> parseTagNames(String tagNames) {
    return Arrays.stream(tagNames.split("\\s*,\\s*"))
        .map(String::trim)
        .collect(Collectors.toList());
  }
}
