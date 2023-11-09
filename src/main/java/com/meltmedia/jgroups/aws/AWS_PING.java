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

import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.annotations.Property;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.protocols.Discovery;
import org.jgroups.protocols.PingData;
import org.jgroups.protocols.PingHeader;
import org.jgroups.stack.IpAddress;
import org.jgroups.util.NameCache;
import org.jgroups.util.Responses;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
 * &lt;com.schibsted.publishing.jgroups.aws.AWS_PING
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
 * &lt;com.schibsted.publishing.jgroups.aws.AWS_PING
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
  static {
    ClassConfigurator.addProtocol((short) 600, AWS_PING.class); // ID needs to be unique
  }

  @Property(description = "The AWS Credentials Chain Class to use when searching for the account.")
  protected String credentials_provider_class = DefaultCredentialsProvider.class.getName();
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
      + "A:7800 and A:7801.  The default is 50.")
  protected int port_range = 50;
  @Property(description = "The port number being used for cluster membership.  The default is 7800.")
  protected int port_number = 7800;
  @Property(description = "Turns on AWS error message logging.")
  private boolean log_aws_error_messages = false;

  /**
   * This is looked up using the endpoint http://instance-data/latest/dynamic/instance-identity/document
   */
  private EC2MetadataUtils.InstanceInfo instanceInfo;

  /**
   * The Service for all AWS related stuff
   */
  private Ec2Client ec2;

  /**
   * Utility for expanding one ip address + port and range to multiple address:port
   */
  private IPAddressUtils ipAddressUtils;

  /**
   * Utility for working with tags
   */
  private TagsUtils tagUtils;

  /**
   * Utility for working with filters
   */
  private FilterUtils filterUtils;

  /**
   * Scans the environment for information about the AWS node that we are
   * currently running on and parses the filters and tags.
   */
  public void init() throws Exception {
    super.init();

    //get the instance identity
    this.instanceInfo = EC2MetadataUtils.getInstanceInfo();

    //setup ec2 client
    this.ec2 = EC2Factory.create(
        instanceInfo,
        access_key,
        secret_key,
        credentials_provider_class,
        new CredentialsProviderFactory());

    this.ipAddressUtils = new IPAddressUtils(port_number, port_range);
    this.tagUtils = new TagsUtils(ec2, instanceInfo, tags).validateTags();
    this.filterUtils = new FilterUtils(filters, tagUtils);

    log.info("Configured for instance: " + instanceInfo.getInstanceId());
    filterUtils.getAwsFilters().ifPresent(f -> log.info("Configured with filters [%s]", f));
    tagUtils.getAwsTagNames().ifPresent(t -> log.info("Configured with tags [%s]", t));
  }

  /**
   * Stops this protocol.
   */
  @Override
  public void stop() {
    try {
      if (ec2 != null) {
        ec2.close();
      }
    } finally {
      super.stop();
    }
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
   * Fetches all of the cluster members found on EC2. The host portion of the
   * addresses are the private ip addresses of the matching nodes. The port
   * numbers of the addresses are set to the port number plus all the ports in
   * the range after that specified on this protocol.
   */
  @Override
  protected void findMembers(final List<Address> members, boolean initial_discovery, final Responses responses) {
    final IpAddress physical_addr = (IpAddress) down(new Event(Event.GET_PHYSICAL_ADDRESS, local_addr));
    final PingData data = new PingData(local_addr, false, NameCache.get(local_addr), physical_addr);
    final PingHeader hdr = new PingHeader(PingHeader.GET_MBRS_REQ).clusterName(cluster_name);
    final List<IpAddress> clusterMembers = ipAddressUtils.expandClusterMemberPorts(getPrivateIpAddresses());

    clusterMembers.stream()
        .filter(Objects::nonNull) //guard against nulls
        .filter(address -> address.compareTo(physical_addr) != 0) //filter out self
        .map(address -> new Message(address)
            .setFlag(Message.Flag.INTERNAL, Message.Flag.DONT_BUNDLE, Message.Flag.OOB)
            .putHeader(this.id, hdr).setBuffer(marshal(data)))
        .forEach(message -> {
          if(async_discovery_use_separate_thread_per_request) {
            log.trace("%s: sending async discovery request to %s", local_addr, message.getDest());
            down_prot.down(message);
          } else {
            log.trace("%s: sending discovery request to %s", local_addr, message.getDest());
            down_prot.down(message);
          }
        });
  }

  /**
   * Gets the list of private IP addresses found in AWS based on the filters and
   * tag names defined.
   *
   * @return the list of private IP addresses found on AWS.
   */
  private List<String> getPrivateIpAddresses() {
    // if there are aws tags configured, then look them up and create filters.
    final List<Filter> filters = filterUtils.instanceTagNamesToFilters();

    // if there are aws filters defined, add them to the list.
    filterUtils.getAwsFilters().ifPresent(filters::addAll);

    final DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(filters).build();

    if (log.isDebugEnabled()) {
      log.debug("Describing AWS instances with the following filters [%s]", filters);
      log.debug("Making AWS Request {%s}", request);
    }

    // NOTE: the reservations group nodes together by when they were started. We
    // need to dig through all of the reservations.
    final List<String> result = ec2.describeInstancesPaginator(request).reservations().stream()
        .flatMap(reservation -> reservation.instances().stream())
        .map(Instance::privateIpAddress)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug("Instances found [%s]", result);
    }

    return result;
  }
}
