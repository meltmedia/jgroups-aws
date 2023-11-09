package com.meltmedia.jgroups.aws;

import com.amazonaws.internal.EC2ResourceFetcher;
import com.google.common.io.Resources;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static com.google.common.io.Resources.getResource;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstanceIdentityTest {
  @Test
  public void fromResponse() throws Exception {
    EC2ResourceFetcher ec2ResourceFetcher = mock(EC2ResourceFetcher.class);

    when(ec2ResourceFetcher.readResource(any()))
            .thenReturn(Resources.toString(getResource("instance-identity.json"), StandardCharsets.UTF_8));

    InstanceIdentity instanceIdentity = InstanceIdentity.getIdentity(ec2ResourceFetcher);

    assertEquals("us-west-2b", instanceIdentity.availabilityZone);
    assertEquals("10.158.112.84", instanceIdentity.privateIp);
    assertEquals("i-1234567890abcdef0", instanceIdentity.instanceId);
    assertEquals("t2.micro", instanceIdentity.instanceType);
    assertEquals("ami-5fb8c835", instanceIdentity.imageId);
    assertEquals("x86_64", instanceIdentity.architecture);
    assertEquals("us-west-2", instanceIdentity.region);
  }
}