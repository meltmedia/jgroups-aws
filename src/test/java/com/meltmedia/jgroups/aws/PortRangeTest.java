package com.meltmedia.jgroups.aws;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@RunWith(Parameterized.class)
public class PortRangeTest {
  
  @Parameters(name="{0}")
  public static Collection<Object[]> parameters() throws UnknownHostException {
    return Arrays.asList(new Object[][] {
      { 
        "range 0",
        awsPing(7800, 0),
        matchClusterPorts(7800)
      },
      { "range 1",
        awsPing(7800, 1),
        matchClusterPorts(7800, 7801)
      },
      { 
        "range 2",
        awsPing(7800, 2),
        matchClusterPorts(7800, 7801, 7802)
      },
      { 
        "different range start",
        awsPing(7803, 3),
        matchClusterPorts(7803, 7804, 7805, 7806)
      },
      { 
        "default port and range",
        awsPingDefaultPortAndRange(),
        Matchers.hasSize(51)
      }
    });
  }
  
  private AWS_PING ping;
  private Matcher<List<PhysicalAddress>> matcher;

  public PortRangeTest( String name, AWS_PING ping, Matcher<List<PhysicalAddress>> matcher ) {
    this.ping = ping;
    this.matcher = matcher;
  }
  
  @Test
  public void correctClusterMembers() {
    assertThat(ping.expandClusterMemberPorts(Collections.singletonList("localhost")), matcher);
  }
  
  public static AWS_PING awsPing( int port_number, int port_range ) {
    AWS_PING ping = new AWS_PING();
    ping.port_number = port_number;
    ping.port_range = port_range;
    return ping;
  }
  
  public static AWS_PING awsPingDefaultPortAndRange() {
    return new AWS_PING();
  }

  public static Matcher<Iterable<? extends PhysicalAddress>> matchClusterPorts( int... ports ) throws UnknownHostException {
    PhysicalAddress[] addresses = new PhysicalAddress[ports.length];
    for( int i = 0; i < ports.length; i++ ) {
      addresses[i] = new IpAddress("localhost", ports[i]);
    }
    return containsInAnyOrder(addresses);
  }
}
