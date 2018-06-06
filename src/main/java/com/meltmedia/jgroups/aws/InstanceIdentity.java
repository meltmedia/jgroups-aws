package com.meltmedia.jgroups.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceIdentity {

  private static final String INSTANCE_DATA  = System.getProperty("instance.data", "169.254.169.254");
  private static final String INSTANCE_IDENTITY_URL = String.format("http://%s/latest/dynamic/instance-identity/document", INSTANCE_DATA);

  private static URI INSTANCE_IDENTITY_URI;

  static {
    try {
      INSTANCE_IDENTITY_URI = new URI(INSTANCE_IDENTITY_URL);
    } catch (URISyntaxException e) {
      throw new RuntimeException("this should never happen");
    }
  }


  public final String availabilityZone;
  public final String privateIp;
  public final String instanceId;
  public final String instanceType;
  public final String imageId;
  public final String architecture;
  public final String region;

  public InstanceIdentity(
      @JsonProperty("availabilityZone") final String availabilityZone,
      @JsonProperty("privateIp") final String privateIp,
      @JsonProperty("instanceId") final String instanceId,
      @JsonProperty("instanceType") final String instanceType,
      @JsonProperty("imageId") final String imageId,
      @JsonProperty("architecture") final String architecture,
      @JsonProperty("region") final String region) {
    this.availabilityZone = Objects.requireNonNull(availabilityZone, "availabilityZone cannot be null");
    this.privateIp = Objects.requireNonNull(privateIp, "privateIp cannot be null");
    this.instanceId = Objects.requireNonNull(instanceId, "instanceId cannot be null");
    this.instanceType = Objects.requireNonNull(instanceType, "instanceType cannot be null");
    this.imageId = Objects.requireNonNull(imageId, "imageId cannot be null");
    this.architecture = Objects.requireNonNull(architecture, "architecture cannot be null");
    this.region = Objects.requireNonNull(region, "region cannot be null");
  }

  public static InstanceIdentity getIdentity(final HttpClient client) throws IOException {
    return new ObjectMapper().readValue(getIdentityDocument(client), InstanceIdentity.class);
  }

  /**
   * Gets the body of the content returned from a GET request to uri.
   *
   * @param client
   * @return the body of the message returned from the GET request.
   * @throws IOException if there is an error encountered while getting the content.
   */
  private static String getIdentityDocument(final HttpClient client) throws IOException {
    try {
      final HttpGet getInstance = new HttpGet();
      getInstance.setURI(INSTANCE_IDENTITY_URI);
      final HttpResponse response = client.execute(getInstance);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new IOException("failed to get instance identity, tried: " + INSTANCE_IDENTITY_URL + ", response: " + response.getStatusLine().getReasonPhrase());
      }
      return EntityUtils.toString(response.getEntity());
    } catch (Exception e) {
      throw new IOException("failed to get instance identity", e);
    }
  }
}