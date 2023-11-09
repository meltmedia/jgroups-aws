package com.meltmedia.jgroups.aws;

import org.junit.Test;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.List;

import static com.meltmedia.jgroups.aws.Mocks.ec2Mock;
import static org.junit.Assert.*;

public class TagUtilsTest {

  @Test
  public void canHandleNullTagString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), FilterUtilsTest.instanceInfo, null);
    assertFalse(tagsUtils.getAwsTagNames().isPresent());
  }

  @Test
  public void canHandleEmptyTagString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), FilterUtilsTest.instanceInfo, "");
    assertFalse(tagsUtils.getAwsTagNames().isPresent());
  }

  @Test
  public void willParseConfiguredTags() {
    final Tag instanceTag1 = Tag.builder().key("tag1").value("value1").build();
    final Tag instanceTag2 = Tag.builder().key("tag2").value("value2").build();

    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2), FilterUtilsTest.instanceInfo, "tag1, tag2").validateTags();
    assertTrue(tagsUtils.getAwsTagNames().isPresent());
    tagsUtils.getAwsTagNames().ifPresent(tags -> {
      assertEquals(2, tags.size());
      assertArrayEquals(new String[]{"tag1", "tag2"}, tags.toArray());
    });
  }

  @Test
  public void canGetInstanceTags() {
    final Tag instanceTag1 = Tag.builder().key("instanceTag1").value("value1").build();
    final Tag instanceTag2 = Tag.builder().key("instanceTag2").value("value2").build();

    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2), FilterUtilsTest.instanceInfo, null).validateTags();
    final List<Tag> tags = tagsUtils.getInstanceTags();

    assertEquals(2, tags.size());
    Tag tag1 = tags.get(0);
    Tag tag2 = tags.get(1);

    assertEquals("instanceTag1", tag1.key());
    assertEquals("value1", tag1.value());
    assertEquals("instanceTag2", tag2.key());
    assertEquals("value2", tag2.value());
  }

  @Test(expected = IllegalStateException.class)
  public void missingInstanceTagsShouldThrowException() {
    final Tag instanceTag1 = Tag.builder().key("tag1").value("value1").build();
    final Tag instanceTag2 = Tag.builder().key("tag2").value("value2").build();

    new TagsUtils(ec2Mock(instanceTag1, instanceTag2), FilterUtilsTest.instanceInfo, "tag1, tag2, tag3").validateTags();
  }
}
