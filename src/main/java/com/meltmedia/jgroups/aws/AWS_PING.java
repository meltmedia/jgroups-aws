package com.meltmedia.jgroups.aws;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jgroups.PhysicalAddress;
import org.jgroups.ViewId;
import org.jgroups.protocols.Discovery;
import org.jgroups.util.Promise;
import org.jgroups.annotations.Property;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

/**
 * 
 * References
 * http://docs.amazonwebservices.com/AWSEC2/latest/CommandLineReference/ApiReference-cmd-DescribeInstances.html
 * http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/AESDG-chapter-instancedata.html
 * @author ctrimble
 *
 */
public class AWS_PING
  extends Discovery
{
	private static String INSTANCE_METADATA_BASE_URI = "http://169.254.169.254/latest/meta-data/";
	private static String GET_INSTANCE_ID = INSTANCE_METADATA_BASE_URI+"instance-id";
	private static String GET_LOCAL_ADDR = INSTANCE_METADATA_BASE_URI+"local-ipv4s";
	
	@Property(description="The AWS Access Key for the account to search.")
	protected String access_key;
	@Property(description="The AWS Secret Key for the account to search.")
	protected String secret_key;
	@Property(description="The URL of the AWS endpoint to use.")
	protected String endpoint;
	@Property(description="A semicolon delimited list of filters to search on. (name1=value1,value2;name2=value1,value2)")
	protected String filters;
	
	private String instanceId;
	private String localAddress;
	private Collection<Filter> awsFilters;
	private AmazonEC2 ec2;
	
	public void init() throws Exception {
		super.init();
		
		// get the instance id and private IP address.
		HttpClient client = null;
		try {
			client = new DefaultHttpClient();
			instanceId = getUrl(client, INSTANCE_METADATA_BASE_URI);
			localAddress = getUrl(client, INSTANCE_METADATA_BASE_URI);
		}
		finally {
			HttpClientUtils.closeQuietly(client);
		}
		
		awsFilters = parseFilters(filters);
		ec2 = new AmazonEC2Client(new BasicAWSCredentials(access_key, secret_key));
		
	}
	
    public void start() throws Exception {
        super.start();
    }

    public void stop() {
        super.stop();
    }
	
	@Override
	public Collection<PhysicalAddress> fetchClusterMembers(String cluster_name) {
		
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		request.setFilters(awsFilters);
		
		DescribeInstancesResult result = ec2.describeInstances(request);
		
		// NOTE: the reservations group nodes together by when they were started.  We need to dig through all of the reservations.
		List<String> privateIpAddresses = new ArrayList<String>();
		for( Reservation reservation : result.getReservations() ) {
			for( Instance instance : reservation.getInstances() ) {
				// TODO: Filter out the ip of this machine.
		        privateIpAddresses.add(instance.getPrivateIpAddress());
			}
		}
		
		for( String privateIpAddress : privateIpAddresses ) {
		}
		
		return null;
		
	}


	@Override
	public boolean isDynamic() {
		return true;
	}
	
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
	
	static Collection<Filter> parseFilters( String filters ) {
		List<Filter> awsFilters = new ArrayList<Filter>();
		
		String[] filterArray = filters.split("\\w*;\\w*");
		for( String filterString : filterArray ) {
			// clean up the filter, moving on if it is empty.
			String trimmed = filterString.trim();
			if( trimmed.length() == 0 ) continue;
			
			// isolate the key and the values, failing if there is a problem.
			String[] keyValues = trimmed.split("\\w*=\\w*");
			if( keyValues.length != 2 || keyValues[0].length() == 0 || keyValues[1].length() == 0 )
				throw new RuntimeException("Could not process key value pair '"+filterString+"'");
			
			// create the filter and add it to the list.
			awsFilters.add(new Filter().withName(keyValues[0]).withValues(keyValues[1].split("\\w*,\\w")));
		}
		return awsFilters;
	}
	
	static String getUrl( HttpClient client, String uri )
	  throws Exception
	{
	    HttpGet getInstance = new HttpGet();
	    getInstance.setURI(new URI(uri));
	    HttpResponse response = client.execute(getInstance);
	    if( response.getStatusLine().getStatusCode() != HttpStatus.SC_OK ) {
	    	throw new Exception("Could not get instance ID.");
	    }
	    return EntityUtils.toString(response.getEntity());
	}




}
