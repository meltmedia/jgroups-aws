package com.meltmedia.jgroups.aws;

import com.amazonaws.services.ec2.model.Tag;
import org.junit.Test;

import java.util.List;

import static com.meltmedia.jgroups.aws.Mocks.ec2Mock;
import static org.junit.Assert.*;

public class TagUtilsTest {
  private static InstanceIdentity instanceIdentity = new InstanceIdentity(
      "zone",
      "1.2.3.4",
      "instance_id",
      "instance_type",
      "image_id",
      "architecture",
      "region");

  @Test
  public void canHandleNullTagString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceIdentity, null);
    assertFalse(tagsUtils.getAwsTagNames().isPresent());
  }

  @Test
  public void canHandleEmptyTagString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceIdentity, "");
    assertFalse(tagsUtils.getAwsTagNames().isPresent());
  }

  @Test
  public void willParseConfiguredTags() {
    final Tag instanceTag1 = new Tag("tag1", "value1");
    final Tag instanceTag2 = new Tag("tag2", "value2");

    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2), instanceIdentity, "tag1, tag2").validateTags();
    assertTrue(tagsUtils.getAwsTagNames().isPresent());
    tagsUtils.getAwsTagNames().ifPresent(tags -> {
      assertEquals(2, tags.size());
      assertArrayEquals(new String[]{"tag1", "tag2"}, tags.toArray());
    });
  }

  @Test
  public void canGetInstanceTags() {
    final Tag instanceTag1 = new Tag("instanceTag1", "value1");
    final Tag instanceTag2 = new Tag("instanceTag2", "value2");

    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2), instanceIdentity, null).validateTags();
    final List<Tag> tags = tagsUtils.getInstanceTags();

    assertEquals(2, tags.size());
    Tag tag1 = tags.get(0);
    Tag tag2 = tags.get(1);

    assertEquals("instanceTag1", tag1.getKey());
    assertEquals("value1", tag1.getValue());
    assertEquals("instanceTag2", tag2.getKey());
    assertEquals("value2", tag2.getValue());
  }

  @Test(expected = IllegalStateException.class)
  public void missingInstanceTagsShouldThrowException() {
    final Tag instanceTag1 = new Tag("tag1", "value1");
    final Tag instanceTag2 = new Tag("tag2", "value2");

    new TagsUtils(ec2Mock(instanceTag1, instanceTag2), instanceIdentity, "tag1, tag2, tag3").validateTags();
  }
}
