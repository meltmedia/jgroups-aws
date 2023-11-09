package com.meltmedia.jgroups.aws;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;


/**
 * A factory for AmazonEC2 instances.
 */
@SuppressWarnings("deprecation")
public class EC2Factory {
  public static Ec2Client create(
      final EC2MetadataUtils.InstanceInfo instanceInfo,
      final String accessKey,
      final String secretKey,
      final String credentialsProviderClass,
      final CredentialsProviderFactory credentialsProviderFactory
  ) throws Exception {

    return setupEC2Client(
        instanceInfo.getRegion(),
        accessKey,
        secretKey,
        credentialsProviderClass,
        credentialsProviderFactory);
  }

  private static Ec2Client setupEC2Client(
      final String region,
      final String accessKey,
      final String secretKey,
      final String credentialsProviderClass,
      final CredentialsProviderFactory credentialsProviderFactory) throws Exception {

    final AwsCredentialsProvider credentialsProvider = accessKey == null && secretKey == null ?
        credentialsProviderFactory.createCredentialsProvider(credentialsProviderClass) :
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

    return Ec2Client.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .build();
  }

}
