package com.meltmedia.jgroups.aws;

import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Tag;
import org.junit.Test;

import java.util.List;

import static com.meltmedia.jgroups.aws.Mocks.ec2Mock;
import static org.junit.Assert.*;

public class FilterUtilsTest {
  private static InstanceIdentity instanceIdentity = new InstanceIdentity(
      "zone",
      "1.2.3.4",
      "instance_id",
      "instance_type",
      "image_id",
      "architecture",
      "region");

  @Test
  public void canHandleNullFilterString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceIdentity, null);
    final FilterUtils filterUtils = new FilterUtils(null, tagsUtils);

    assertFalse(filterUtils.getAwsFilters().isPresent());
  }

  @Test
  public void canHandleEmptyFilterString() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceIdentity, null);
    final FilterUtils filterUtils = new FilterUtils(" ", tagsUtils);

    assertFalse(filterUtils.getAwsFilters().isPresent());
  }

  @Test
  public void canParseAwsFilters() {
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(), instanceIdentity, null);
    final FilterUtils filterUtils = new FilterUtils("filter1=valueA, valueB; filter2=valueC", tagsUtils);

    assertTrue(filterUtils.getAwsFilters().isPresent());
    filterUtils.getAwsFilters().ifPresent(filters -> {
      assertEquals(2, filters.size());
      Filter filter1 = filters.get(0);
      assertEquals("filter1", filter1.getName());
      assertEquals(2, filter1.getValues().size());
      assertEquals("valueA", filter1.getValues().get(0));
      assertEquals("valueB", filter1.getValues().get(1));

      Filter filter2 = filters.get(1);
      assertEquals("filter2", filter2.getName());
      assertEquals(1, filter2.getValues().size());
      assertEquals("valueC", filter2.getValues().get(0));
    });
  }

  @Test
  public void canCreateFiltersFromMatchingTags() {
    final Tag instanceTag1 = new Tag("tag1", "value1");
    final Tag instanceTag2 = new Tag("tag2", "value2");
    final Tag instanceTag3 = new Tag("tag3", "value3");
    final TagsUtils tagsUtils = new TagsUtils(ec2Mock(instanceTag1, instanceTag2, instanceTag3), instanceIdentity, "tag2, tag3");
    final FilterUtils filterUtils = new FilterUtils(null, tagsUtils);

    final List<Filter> filters = filterUtils.instanceTagNamesToFilters();

    assertEquals(2, filters.size());
    Filter filter1 = filters.get(0);
    assertEquals("tag:tag2", filter1.getName());
    assertEquals(1, filter1.getValues().size());
    assertEquals("value2", filter1.getValues().get(0));

    Filter filter2 = filters.get(1);
    assertEquals("tag:tag3", filter2.getName());
    assertEquals(1, filter2.getValues().size());
    assertEquals("value3", filter2.getValues().get(0));
  }
}
