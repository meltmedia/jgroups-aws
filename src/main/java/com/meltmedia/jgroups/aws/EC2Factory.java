package com.meltmedia.jgroups.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.RequestHandler;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.transform.Unmarshaller;
import org.w3c.dom.Node;

import java.lang.reflect.Field;
import java.util.List;


/**
 * A factory for AmazonEC2 instances.
 */
@SuppressWarnings("deprecation")
public class EC2Factory {
  private static String EC2_ENDPOINT_TEMPLATE = "ec2.{REGION}.amazonaws.com";

  public static AmazonEC2 create(
      final InstanceIdentity instanceIdentity,
      final String accessKey,
      final String secretKey,
      final String credentialsProviderClass,
      final CredentialsProviderFactory credentialsProviderFactory,
      final Boolean logAwsErrorMessages) throws Exception {

    final AmazonEC2 ec2 = setupEC2Client(
        instanceIdentity.region,
        accessKey,
        secretKey,
        credentialsProviderClass,
        credentialsProviderFactory);

    //Lets do some good old reflection work to add a unmarshaller to the AmazonEC2Client just to log the exceptions from soap.
    if (logAwsErrorMessages) {
      setupAWSExceptionLogging(ec2);
    }
    return ec2;
  }

  private static AmazonEC2 setupEC2Client(
      final String region,
      final String accessKey,
      final String secretKey,
      final String credentialsProviderClass,
      final CredentialsProviderFactory credentialsProviderFactory) throws Exception {

    final String endpoint = EC2_ENDPOINT_TEMPLATE.replace("{REGION}", region);
    final AWSCredentialsProvider credentialsProvider = accessKey == null && secretKey == null ?
        credentialsProviderFactory.createCredentialsProvider(credentialsProviderClass) :
        new StaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));

    final AmazonEC2 ec2 = new AmazonEC2Client(credentialsProvider);
    ec2.setEndpoint(endpoint);
    return ec2;
  }

  /**
   * Sets up the AmazonEC2Client to log soap faults from the AWS EC2 api server.
   */
  private static void setupAWSExceptionLogging(AmazonEC2 ec2) {
    boolean accessible = false;
    Field exceptionUnmarshallersField = null;
    try {
      exceptionUnmarshallersField = AmazonEC2Client.class.getDeclaredField("exceptionUnmarshallers");
      accessible = exceptionUnmarshallersField.isAccessible();
      exceptionUnmarshallersField.setAccessible(true);
      @SuppressWarnings("unchecked") final List<Unmarshaller<AmazonServiceException, Node>> exceptionUnmarshallers = (List<Unmarshaller<AmazonServiceException, Node>>) exceptionUnmarshallersField.get(ec2);
      exceptionUnmarshallers.add(0, new AWSFaultLogger());
      ((AmazonEC2Client) ec2).addRequestHandler((RequestHandler) exceptionUnmarshallers.get(0));
    } catch (Throwable t) {
      //I don't care about this.
    } finally {
      if (exceptionUnmarshallersField != null) {
        try {
          exceptionUnmarshallersField.setAccessible(accessible);
        } catch (SecurityException se) {
          //I don't care about this.
        }
      }
    }
  }

}
