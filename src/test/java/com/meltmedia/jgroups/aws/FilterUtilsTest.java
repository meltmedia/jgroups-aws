package com.meltmedia.jgroups.aws;

import org.junit.Test;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.util.List;

import static com.meltmedia.jgroups.aws.Mocks.ec2Mock;
import static org.junit.Assert.*;

public class FilterUtilsTest {
  static final EC2MetadataUtils.InstanceInfo instanceInfo = new EC2MetadataUtils.InstanceInfo(
      "pending_time",
      "instance_type",
      "image_id",
      "instance_id",
      new String[]{"billing_products"},
      "architecture",
      "account_id",
      "kernel_id",
      "ramdisk_id",
      "region",
      "version",
      "zone",
      "1.2.3.4",
      new String[]{"dev_product_codes"},
      new String[]{"market_place_product_codes"}
  );

  @Test
  public void canHandleNullFilterString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceInfo, null);
    final FilterUtils filterUtils = new FilterUtils(null, tagsUtils);

    assertFalse(filterUtils.getAwsFilters().isPresent());
  }

  @Test
  public void canHandleEmptyFilterString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceInfo, null);
    final FilterUtils filterUtils = new FilterUtils(" ", tagsUtils);

    assertFalse(filterUtils.getAwsFilters().isPresent());
  }

  @Test
  public void canParseAwsFilters() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceInfo, null);
    final FilterUtils filterUtils = new FilterUtils("filter1=valueA, valueB; filter2=valueC", tagsUtils);

    assertTrue(filterUtils.getAwsFilters().isPresent());
    filterUtils.getAwsFilters().ifPresent(filters -> {
      assertEquals(2, filters.size());
      Filter filter1 = filters.get(0);
      assertEquals("filter1", filter1.name());
      assertEquals(2, filter1.values().size());
      assertEquals("valueA", filter1.values().get(0));
      assertEquals("valueB", filter1.values().get(1));

      Filter filter2 = filters.get(1);
      assertEquals("filter2", filter2.name());
      assertEquals(1, filter2.values().size());
      assertEquals("valueC", filter2.values().get(0));
    });
  }

  @Test
  public void canCreateFiltersFromMatchingTags() {
    final Tag instanceTag1 = Tag.builder().key("tag1").value("value1").build();
    final Tag instanceTag2 = Tag.builder().key("tag2").value("value2").build();
    final Tag instanceTag3 = Tag.builder().key("tag3").value("value3").build();
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2, instanceTag3), instanceInfo, "tag2, tag3");
    final FilterUtils filterUtils = new FilterUtils(null, tagsUtils);

    final List<Filter> filters = filterUtils.instanceTagNamesToFilters();

    assertEquals(2, filters.size());
    Filter filter1 = filters.get(0);
    assertEquals("tag:tag2", filter1.name());
    assertEquals(1, filter1.values().size());
    assertEquals("value2", filter1.values().get(0));

    Filter filter2 = filters.get(1);
    assertEquals("tag:tag3", filter2.name());
    assertEquals(1, filter2.values().size());
    assertEquals("value3", filter2.values().get(0));
  }
}
