package com.meltmedia.jgroups.aws;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jgroups.PhysicalAddress;
import org.jgroups.stack.IpAddress;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

@RunWith(Parameterized.class)
public class PortRangeTest {

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() throws UnknownHostException {
    return Arrays.asList(new Object[][]{
        {
            "range 0",
            new IPAddressUtils(7800, 0),
            matchClusterPorts(7800)
        },
        {
            "range 1",
            new IPAddressUtils(7800, 1),
            matchClusterPorts(7800, 7801)
        },
        {
            "range 2",
            new IPAddressUtils(7800, 2),
            matchClusterPorts(7800, 7801, 7802)
        },
        {
            "different range start",
            new IPAddressUtils(7803, 3),
            matchClusterPorts(7803, 7804, 7805, 7806)
        },
        {
            "default port and range",
            new IPAddressUtils(new AWS_PING().port_number, new AWS_PING().port_range),
            Matchers.hasSize(51)
        }
    });
  }

  private final IPAddressUtils ipAddressUtils;
  private Matcher<List<IpAddress>> matcher;

  public PortRangeTest(String name, IPAddressUtils ipAddressUtils, Matcher<List<IpAddress>> matcher) {
    this.ipAddressUtils = ipAddressUtils;
    this.matcher = matcher;
  }

  @Test
  public void correctClusterMembers() {
    assertThat(ipAddressUtils.expandClusterMemberPorts(Collections.singletonList("localhost")), matcher);
  }

  public static Matcher<Iterable<? extends PhysicalAddress>> matchClusterPorts(int... ports) throws UnknownHostException {
    PhysicalAddress[] addresses = new PhysicalAddress[ports.length];
    for (int i = 0; i < ports.length; i++) {
      addresses[i] = new IpAddress("localhost", ports[i]);
    }
    return containsInAnyOrder(addresses);
  }
}
