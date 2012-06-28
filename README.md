AWS Auto Discovery for JGroups
==============================
Overview
--------
This package provides auto discovery for other custer members on AWS using both tag matching and filters.  It is
a drop in replacement for TCPPING, allowing you to remove the definition of your initial members from your configuraiton
file.

Usage
-----
To use AWS auto discovery, you need to add a dependency to this package in your pom:
```
    <dependency>
      <groupId>com.meltmedia.jgroups.aws</groupId>
      <artifactId>jgroups-aws</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>
```
and then replace TCPPING in your stack with com.meltmedia.jgroups.aws.AWS_PING:
```
    <com.meltmedia.jgroups.aws.AWS_PING
         timeout="3000"
         port_number="7800"
         tags="TAG1,TAG2"
         filters="NAME1=VALUE1,VALUE2;NAME2=VALUE3"
         access_key="AWS_ACCESS_KEY"
         secret_key="AWS_SECRET_KEY"/>
```
see the configuration section for information.  You can find an [example stack](./conf/aws_ping.xml) in the config directory.

Configuration Options
---------------------
* timeout - the timeout in milliseconds
* port_number - the port number that the nodes will communicate over.  This needs to be the same on all nodes.  The default is 7800.
* tags - A comma delimited list of EC2 node tag names.  The current nodes values are matched against other nodes to find
cluster members.
* filters - A colon delimited list of filters.  Each filter defines a name and a comma delimited list of possible values.
All filters mutch mach a node for it to be a cluster member.
* access_key (required) - the access key for an AWS user with permission to the "ec2:Describe*" action.
* secret_key (required) - the secret key for the AWS user.

