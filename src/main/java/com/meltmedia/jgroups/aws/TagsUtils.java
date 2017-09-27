package com.meltmedia.jgroups.aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Tag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagsUtils {
  private final AmazonEC2 ec2;
  private final InstanceIdentity instanceIdentity;
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private final Optional<List<String>> awsTagNames;

  public TagsUtils(final AmazonEC2 ec2, final InstanceIdentity instanceIdentity, final String configuredTags) {
    this.ec2 = ec2;
    this.instanceIdentity = instanceIdentity;
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
    final DescribeInstancesResult response = ec2
        .describeInstances(new DescribeInstancesRequest()
            .withInstanceIds(Collections.singletonList(instanceIdentity.instanceId)));

    return response.getReservations().stream()
        .flatMap(reservation -> reservation.getInstances().stream())
        .flatMap(instance -> instance.getTags().stream())
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
        .map(Tag::getKey)
        .collect(Collectors.toList());

    final List<String> missingTags = getAwsTagNames().map(List::stream).orElse(Stream.empty())
        .filter(configuredTag -> !instanceTags.contains(configuredTag))
        .collect(Collectors.toList());

    if(!missingTags.isEmpty()) {
      throw new IllegalStateException("expected instance tag(s) missing: " + missingTags.stream().collect(Collectors.joining(", ")));
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
