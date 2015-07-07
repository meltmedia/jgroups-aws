/**
 *   Copyright 2012 meltmedia
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.meltmedia.jgroups.aws;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jgroups.PhysicalAddress;
import org.jgroups.logging.Log;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.Util;
import org.w3c.dom.Node;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.Request;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.RequestHandler;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.transform.Unmarshaller;
import com.amazonaws.util.TimingInfo;

/**
 * <p>
 * A discovery protocol that uses the AWS EC2 API to find cluster members.
 * Membership can be determined by a general filter and/or by nodes that have
 * similar tags. This discovery protocol is designed to work with the TCP
 * protocol.
 * </p>
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>The EC2 instances must be in the same region.</li>
 *   <li>The "ec2:Describe*" action must be accessable using either IAM credentials or an IAM instance profile.</li>
 *   <li>The security rules must allow TCP communication between the nodes that are discovered.</li>
 * </ul>
 *
 * <h3>Tag Matching</h3>
 * <p>
 * To use the tag matching feature, use the tags attribute to specify a comma delimited list of tags.  All of the nodes
 * with matching values for these tags will be discovered.
 * </p>
 * 
 * <blockquote>
 * <pre>
 * &lt;com.meltmedia.jgroups.aws.AWS_PING
 *   timeout="3000"
 *   port_number="7800"
 *   tags="Type,Environment"
 *   access_key="YOUR_AWS_ACCESS_KEY"
 *   secret_key="YOUR_AWS_SECRET_KEY"/&gt;
 * </pre>
 * </blockquote>
 *
 * <h3>EC2 Filters</h3>
 * <p>
 * To use EC2's filtering feature to discover nodes, specify the filters attribute.  The format for this attribute is:
 * </p>
 *
 * <blockquote>
 * <pre>
 * FILTERS ::= &lt;FILTER&gt; (';' &lt;FILTER&gt;)*
 * FILTER  ::= &lt;NAME&gt; '=' &lt;VALUE&gt; (',' &lt;VALUE&gt;)*
 * </pre>
 * </blockquote>
 *
 * <p>
 * EC2 instances that match all of the supplied filters will be returned.  For example,
 * if you wanted to cluster with all of the running, small instances in your account, you could specify:
 * </p>
 * <blockquote>
 * <pre>
 * &lt;com.meltmedia.jgroups.aws.AWS_PING
 *   timeout="3000"
 *   port_number="7800"
 *   filters="instance-state-name=running;instance-type=m1.small"
 *   access_key="YOUR_AWS_ACCESS_KEY"
 *   secret_key="YOUR_AWS_SECRET_KEY"/&gt;
 * </pre>
 * </blockquote>
 *
 * <h3>IAM Instance Profiles</h3>
 * <p>
 * Starting with version 1.1.0, instance profiles are supported by AWS_PING.  To use the instance profile associated with
 * an EC2 instance, simply omit the access_key and secret_key attributes:
 * </p>
 * <blockquote>
 * <pre>
 * &lt;com.meltmedia.jgroups.aws.AWS_PING
 *   timeout="3000"
 *   port_number="7800"
 *   tags="Type,Environment"/&gt;
 * </pre>
 * </blockquote>
 *
 * <h3>References</h3>
 * <ul>
 *   <li><a href="http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/Using_Filtering.html">EC2 Using Filtering</a></li>
 *   <li><a href="http://docs.amazonwebservices.com/AWSEC2/latest/CommandLineReference/ApiReference-cmd-DescribeInstances.html">EC2 Describe Instances</a></li>
 *   <li><a href="http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/AESDG-chapter-instancedata.html">EC2 Instance Metadata</a></li>
 * </ul>
 * 
 * @author Christian Trimble
 * @author John McEntire
 * 
 */
public class AWS_PING extends Discovery {
  private static String INSTANCE_METADATA_BASE_URI = "http://169.254.169.254/latest/meta-data/";
  private static String GET_INSTANCE_ID = INSTANCE_METADATA_BASE_URI
      + "instance-id";
  private static String GET_AVAILABILITY_ZONE = INSTANCE_METADATA_BASE_URI
      + "placement/availability-zone";

  static {
    ClassConfigurator.addProtocol((short) 600, AWS_PING.class); // ID needs to
                                                                // be unique
  }

  @Property(description = "The AWS Credentials Chain Class to use when searching for the account.")
  protected String credentials_provider_class = com.amazonaws.auth.DefaultAWSCredentialsProviderChain.class.getName();
  @Property(description = "The AWS Access Key for the account to search.")
  protected String access_key;
  @Property(description = "The AWS Secret Key for the account to search.")
  protected String secret_key;
  @Property(description = "A semicolon delimited list of filters to search on. (name1=value1,value2;name2=value1,value2)")
  protected String filters;
  @Property(description = "A list of tags that identify this cluster.")
  protected String tags;
  @Property(description = "Number of additional ports to be probed for membership. A port_range of 0 does not "
      + "probe additional ports. Example: initial_hosts=A[7800] port_range=0 probes A:7800, port_range=1 probes "
      + "A:7800 and A:7801")
  private int port_range = 50;
  @Property(description = "The port number being used for cluster membership.  The default is 7800.")
  private int port_number = 7800;
  @Property(description = "Turns on AWS error message logging.")
  private boolean log_aws_error_messages = false;

  /**
   * The id of the current instance. This is looked up from the instance
   * meta-data.
   */
  private String instanceId;

  /**
   * The availability zone of the current instance. This is looked up from the
   * instance meta-data.
   */
  private String availabilityZone;

  /**
   * The AWS endpoint for the region of the current instance. This is computed
   * based on the availability zone.
   */
  private String endpoint;

  /**
   * The collection of filters to run. This is created based on the value of
   * filters and tags.
   */
  private Collection<Filter> awsFilters;

  /**
   * The list of tag names. This is the parsed form of tags.
   */
  private List<String> awsTagNames;

  /**
   * The EC2 client used to look up cluster members.
   */
  private AmazonEC2 ec2;

  /**
   * Scans the environment for information about the AWS node that we are
   * currently running on and parses the filters and tags.
   */
  public void init() throws Exception {
    super.init();

    // get the instance id and availability zone.
    HttpClient client = null;
    try {
      client = new DefaultHttpClient();
      instanceId = getBody(client, GET_INSTANCE_ID);
      availabilityZone = getBody(client, GET_AVAILABILITY_ZONE);
    } finally {
      if(client != null) {
        client.getConnectionManager().shutdown();
      }
    }

    if (filters != null)
      awsFilters = parseFilters(filters);
    if (tags != null)
      awsTagNames = parseTagNames(tags);

    if (log.isDebugEnabled()) {
      if (filters != null)
        log.debug("\n\nConfigured with filters [" + awsFilters + "]\n\n");
      if (tags != null)
        log.debug("\n\nConfigured with tags [" + awsTagNames + "]\n\n");
    }
    // compute the EC2 endpoint based on the availability zone.
    endpoint = "ec2." + availabilityZone.replaceAll("(.*-\\d+)[^-\\d]+", "$1")
        + ".amazonaws.com";
  }

  /**
   * Starts this protocol.
   */
  public void start() throws Exception {
    super.start();

    // start up a new ec2 client with the region specific endpoint.
    if( access_key == null && secret_key == null ) {
      AWSCredentialsProvider awsCredentialsProvider = loadCredentialsProvider(credentials_provider_class, getClass(), log);
      ec2 = new AmazonEC2Client(awsCredentialsProvider);
    }
    else {
      ec2 = new AmazonEC2Client(new BasicAWSCredentials(access_key, secret_key));
    }
    ec2.setEndpoint(endpoint);
    
    //Lets do some good old reflection work to add a unmarshaller to the AmazonEC2Client just to log the exceptions from soap.
    if(log_aws_error_messages) {
      setupAWSExceptionLogging();
    }
  }

  /**
   * Sets up the AmazonEC2Client to log soap faults from the AWS EC2 api server.
   */
  private void setupAWSExceptionLogging() {
    boolean accessible = false;
    Field exceptionUnmarshallersField = null;
    try {
      exceptionUnmarshallersField = AmazonEC2Client.class.getDeclaredField("exceptionUnmarshallers");
      accessible = exceptionUnmarshallersField.isAccessible();
      exceptionUnmarshallersField.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<Unmarshaller<AmazonServiceException, Node>> exceptionUnmarshallers = (List<Unmarshaller<AmazonServiceException, Node>>)exceptionUnmarshallersField.get(ec2);
      exceptionUnmarshallers.add(0, new AWSFaultLogger());
      ((AmazonEC2Client)ec2).addRequestHandler((RequestHandler) exceptionUnmarshallers.get(0));
    } catch(Throwable t) {
      //I don't care about this.
    } finally {
      if(exceptionUnmarshallersField != null) {
        try {
          exceptionUnmarshallersField.setAccessible(accessible);
        } catch(SecurityException se){
          //I don't care about this.
        }
      }
    }
  }

  /**
   * Stops this protocol.
   */
  public void stop() {

    try {
      if (ec2 != null)
        ec2.shutdown();
    } finally {
      super.stop();
    }
  }

  /**
   * Fetches all of the cluster members found on EC2. The host portion of the
   * addresses are the private ip addresses of the matching nodes. The port
   * numbers of the addresses are set to the port number plus all the ports in
   * the range after that specified on this protocol.
   * 
   * @return the cluster members.
   */
  public Collection<PhysicalAddress> fetchClusterMembers(String cluster_name) {
    List<PhysicalAddress> clusterMembers = new ArrayList<PhysicalAddress>();
    for (String privateIpAddress : getPrivateIpAddresses()) {
      for (int i = port_number; i < port_number + port_range; i++) {
        try {
          clusterMembers.add(new IpAddress(privateIpAddress, i));
        } catch (UnknownHostException e) {
          log.warn("Could not create an IpAddress for " + privateIpAddress
              + ":" + i);
        }
      }
    }
    return clusterMembers;
  }

  /**
   * Gets the list of private IP addresses found in AWS based on the filters and
   * tag names defined.
   * 
   * @return the list of private IP addresses found on AWS.
   */
  protected List<String> getPrivateIpAddresses() {
    List<String> result = new ArrayList<String>();

    List<Filter> filters = new ArrayList<Filter>();

    // if there are aws tags defined, then look them up and create filters.
    if (awsTagNames != null) {
      Collection<Tag> instanceTags = getInstanceTags(ec2, instanceId);
      for (Tag instanceTag : instanceTags) {
        if (awsTagNames.contains(instanceTag.getKey())) {
          filters.add(new Filter("tag:" + instanceTag.getKey(), Arrays
              .asList(instanceTag.getValue())));
        }
      }

    }

    // if there are aws filters defined, then add them to the list.
    if (awsFilters != null) {
      filters.addAll(awsFilters);
    }
    DescribeInstancesRequest request = new DescribeInstancesRequest()
        .withFilters(filters);
    if (log.isDebugEnabled()) {
      log.debug("Describing AWS instances with the following filters ["
          + filters + "]");
      log.debug("Making AWS Request {" + request + "}");
    }
    // NOTE: the reservations group nodes together by when they were started. We
    // need to dig through all of the reservations.
    DescribeInstancesResult filterResult = ec2.describeInstances(request);
    for (Reservation reservation : filterResult.getReservations()) {
      for (Instance instance : reservation.getInstances()) {
        result.add(instance.getPrivateIpAddress());
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("Instances found [" + result + "]");
    }
    return result;
  }

  /**
   * Returns true.
   * 
   * @return true
   */
  @Override
  public boolean isDynamic() {
    return true;
  }

  /**
   * Returns true.
   * 
   * @return true
   */
  @Override
  public boolean sendDiscoveryRequestsInParallel() {
    return true;
  }

  /**
   * Returns the unique name for this protocol AWS_PING.
   */
  @Override
  public String getName() {
    return "AWS_PING";
  }

  /**
   * Parses a filter string into a list of Filter objects that is suitable for
   * the AWS describeInstances method call.
   * 
   * <h3>Format:</h3>
   * <p>
   * <blockquote>
   * 
   * <pre>
   *   FILTERS ::= &lt;FILTER&gt; ( ';' &lt;FILTER&gt; )*
   *   FILTER ::= &lt;NAME&gt; '=' &lt;VALUE&gt; (',' &lt;VALUE&gt;)*
   * </pre>
   * 
   * </blockquote>
   * </p>
   * 
   * @param filters
   *          the filter string to parse.
   * @return the list of filters that represent the filter string.
   */
  static List<Filter> parseFilters(String filters) {
    List<Filter> awsFilters = new ArrayList<Filter>();

    for (String filterString : filters.split("\\s*;\\s*")) {
      // clean up the filter, moving on if it is empty.
      String trimmed = filterString.trim();
      if (trimmed.length() == 0)
        continue;

      // isolate the key and the values, failing if there is a problem.
      String[] keyValues = trimmed.split("\\s*=\\s*");
      if (keyValues.length != 2 || keyValues[0].length() == 0
          || keyValues[1].length() == 0)
        throw new IllegalArgumentException("Could not process key value pair '"
            + filterString + "'");

      // create the filter and add it to the list.
      awsFilters.add(new Filter().withName(keyValues[0]).withValues(
          keyValues[1].split("\\s*,\\s*")));
    }
    return awsFilters;
  }

  /**
   * Parses a comma separated list of tag names.
   * 
   * @param tagNames
   *          a comma separated list of tag names.
   * @return the list of tag names.
   */
  static List<String> parseTagNames(String tagNames) {
    return Arrays.asList(tagNames.split("\\s*,\\s*"));
  }

  /**
   * Gets the body of the content returned from a GET request to uri.
   * 
   * @param client
   *          the HttpClient instance to use for the request.
   * @param uri
   *          the URI to contact.
   * @return the body of the message returned from the GET request.
   * @throws Exception
   *           if there is an error encounted while getting the content.
   */
  static String getBody(HttpClient client, String uri) throws Exception {
    HttpGet getInstance = new HttpGet();
    getInstance.setURI(new URI(uri));
    HttpResponse response = client.execute(getInstance);
    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
      throw new Exception("Could not get instance ID.");
    }
    return EntityUtils.toString(response.getEntity());
  }

  /**
   * Returns all of the tags defined on the EC2 instance with the specified
   * instanceId.
   * 
   * @param ec2
   *          the client to use when accessing Amazon.
   * @param instanceId
   *          the id of the instance to search for tags.
   * @return a list of the Tag objects that were found on the instance.
   */
  public static List<Tag> getInstanceTags(AmazonEC2 ec2, String instanceId) {
    List<Tag> tags = new ArrayList<Tag>();
    DescribeInstancesResult response = ec2
        .describeInstances(new DescribeInstancesRequest()
            .withInstanceIds(Arrays.asList(instanceId)));
    for (Reservation res : response.getReservations()) {
      for (Instance inst : res.getInstances()) {
        List<Tag> instanceTags = inst.getTags();
        if (instanceTags != null && instanceTags.size() > 0) {
          tags.addAll(instanceTags);
        }
      }
    }
    return tags;
  }
  
  /**
   * This class will log the request along with the response from the AWS ec2 service on fault only.
   * 
   * @author John McEntire
   *
   */
  private class AWSFaultLogger implements Unmarshaller<AmazonServiceException, Node>, RequestHandler {
    private final ThreadLocal<Request<?>> request = new ThreadLocal<Request<?>>();
    
    @Override
    public AmazonServiceException unmarshall(Node node) throws Exception {
      try {
        javax.xml.transform.TransformerFactory tfactory = javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer xform = tfactory.newTransformer();
        
        javax.xml.transform.Source src = new javax.xml.transform.dom.DOMSource(node);
        java.io.StringWriter writer = new java.io.StringWriter();
        javax.xml.transform.Result result = new javax.xml.transform.stream.StreamResult(writer);
        xform.transform(src, result);
        log.error("AWS Exception: [" + writer.toString()+"] For request ["+request.get()+"]");
      } catch(Throwable t) {
        log.debug("Failed to log xml soap fault message.", t);
      } finally {
        request.remove();
      }
      return null;
    }

    @Override
    public void afterError(Request<?> request, Exception e) {
      this.request.remove();
    }

    @Override
    public void afterResponse(Request<?> request, Object obj, TimingInfo timing) {
      this.request.remove();
    }

    @Override
    public void beforeRequest(Request<?> request) {
      this.request.set(request);
    }
  }
  
  /**
   * Loads a new instance of the credential provider, using the same class loading rules from org.jgroups.Util.loadClass(String, Class).
   */
  static AWSCredentialsProvider loadCredentialsProvider( String credentialProviderClass, Class<?> jgroupsClass, Log log )
    throws Exception
  {
    Class<?> credsProviderClazz = null;
    try {
      credsProviderClazz = Util.loadClass(credentialProviderClass, jgroupsClass);
    }
    catch( ClassNotFoundException e ) {
      throw new Exception("unable to load credentials provider class " + credentialProviderClass);
    }

    AWSCredentialsProvider awsCredentialsProvider = null;
    try {
      awsCredentialsProvider = (AWSCredentialsProvider) credsProviderClazz.newInstance();
    }
    catch( InstantiationException e ) {
      log.error("an instance of " + credentialProviderClass + " could not be created. Please check that it implements" +
                " interface AWSCredentialsProvider and that is has a public empty constructor !");
      throw e;
    }
    return awsCredentialsProvider;
  }
}
