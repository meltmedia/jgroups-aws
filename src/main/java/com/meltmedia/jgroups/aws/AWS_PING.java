package com.meltmedia.jgroups.aws;

import org.jgroups.protocols.Discovery;

public class AWS_PING
  extends Discovery
{

	@Override
	public void sendGetMembersRequest() {
		throw new UnsupportedOperationException("This is not implemented yet.");
	}

	/**
	 * Returns the unique name for this protocol AWS_PING.
	 */
	@Override
	public String getName() {
		return "AWS_PING";
	}

}
