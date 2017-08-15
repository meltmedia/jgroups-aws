package com.meltmedia.jgroups.aws;

import com.google.common.io.Resources;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static com.google.common.io.Resources.getResource;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstanceIdentityTest {
  @Test
  public void fromResponse() throws Exception {
    final HttpClient client = mock(HttpClient.class);
    final HttpResponse response = mock(HttpResponse.class);
    final StatusLine statusLine = mock(StatusLine.class);
    final HttpEntity responseEntity = mock(HttpEntity.class);

    when(responseEntity.getContent()).thenReturn(new ByteArrayInputStream(Resources.toByteArray(getResource("instance-identity.json"))));
    when(responseEntity.getContentLength()).thenReturn(-1L);
    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
    when(response.getStatusLine()).thenReturn(statusLine);
    when(response.getEntity()).thenReturn(responseEntity);
    when(client.execute(any())).thenReturn(response);

    InstanceIdentity instanceIdentity = InstanceIdentity.getIdentity(client);

    assertEquals("us-west-2b", instanceIdentity.availabilityZone);
    assertEquals("10.158.112.84", instanceIdentity.privateIp);
    assertEquals("i-1234567890abcdef0", instanceIdentity.instanceId);
    assertEquals("t2.micro", instanceIdentity.instanceType);
    assertEquals("ami-5fb8c835", instanceIdentity.imageId);
    assertEquals("x86_64", instanceIdentity.architecture);
    assertEquals("us-west-2", instanceIdentity.region);
  }
}