package com.meltmedia.jgroups.aws;

import com.amazonaws.internal.EC2ResourceFetcher;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  public static InstanceIdentity getIdentity(EC2ResourceFetcher ec2ResourceFetcher) throws IOException {
    return new ObjectMapper().readValue(getIdentityDocument(ec2ResourceFetcher), InstanceIdentity.class);
  }

  /**
   * Gets the body of the content returned from a GET request to uri.
   *
   * @return the body of the message returned from the GET request.
   * @throws IOException if there is an error encountered while getting the content.
   * @param ec2ResourceFetcher
   */
  private static String getIdentityDocument(EC2ResourceFetcher ec2ResourceFetcher) throws IOException {
    try {
      return ec2ResourceFetcher.readResource(INSTANCE_IDENTITY_URI);
    } catch (Exception e) {
      throw new IOException("failed to get instance identity", e);
    }
  }
}