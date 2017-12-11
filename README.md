AWS Auto Discovery for JGroups
==============================
Overview
--------
This package provides auto discovery for other cluster members on AWS using both tag matching and filters.  It is
a drop in replacement for TCPPING, allowing you to remove the definition of your initial members from your configuration
file.

Usage
-----
To use AWS auto discovery, you need to add a dependency to this package in your pom:

```
    <dependency>
      <groupId>com.meltmedia.jgroups</groupId>
      <artifactId>jgroups-aws</artifactId>
      <version>1.6.1</version>
    </dependency>
```

and then replace TCPPING in your stack with com.meltmedia.jgroups.aws.AWS_PING:

```
    <com.meltmedia.jgroups.aws.AWS_PING
         port_number="7800"
         tags="TAG1,TAG2"
         filters="NAME1=VALUE1,VALUE2;NAME2=VALUE3"
         access_key="AWS_ACCESS_KEY"
         secret_key="AWS_SECRET_KEY"/>
```

see the configuration section for information.  You can find an example stack in conf/aws_ping.xml.

This implementation will only work from inside EC2, since it uses environment information to auto wire itself.  See the
Setting Up EC2 section for more information.

SNAPSHOTs of the project are located in the Sonatype Nexus Snapshots repository.  You can use SNAPSHOTs by adding the following repository to your project:

```
    <repository>
      <releases>
        <enabled>false</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
      <id>sonatype-nexus-snapshots</id>
      <name>Sonatype Nexus Snapshots</name>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
```

And then update your dependency to the current SNAPSHOT version.

Configuration Options
---------------------
* port_number - the port number that the nodes will communicate over.  This needs to be the same on all nodes.  The default is 7800.
* port_range - the number of additional ports to be probed for membership. A port_range of 0 does not probe additional ports. Example: initial_hosts=A[7800] port_range=0 probes A:7800, port_range=1 probes A:7800 and A:7801.  The default is 50.
* tags - A comma delimited list of EC2 node tag names.  The current nodes values are matched against other nodes to find
cluster members.
* filters - A colon delimited list of filters.  Each filter defines a name and a comma delimited list of possible values.
All filters must match a node for it to be a cluster member.
* access_key and secret_key - the access key and secret key for an AWS user with permission to the "ec2:Describe*" action.  If both
of these attributes are not specified, then the instance profile for the EC2 instance will be used (Since version 1.1).
* credentials_provider_class - the fully qualified name of the com.amazonaws.auth.AWSCredentialsProvider to use (Since version 1.3).  This option can
only be used when the access_key and secret_key options are not provided.

Setting Up EC2
--------------
You will need to setup the following in EC2, before using this package:
* The EC2 instances will need permission to the "ec2:Describe*" action.  You can either create an IAM user with this permission
and pass the users credentials with the access_key and secret_key attributes or associate an instance profile with that permission
to the instances and not specify the access_key and secret_key attributes.
* In the EC2 console, you will need to create a security group for your instances.  This security group will need a TCP_ALL rule,
with itself as the source (put the security group's name in the source field.)  This will allow all of the nodes in that security
group to communicate with each other.
* Create two EC2 nodes, making sure to include the security group granting TCP communication.
* If you are going to use the tag matching feature, then define a few tags on the nodes with matching values.

Setting up JGroups Chat Demo
----------------------------
The JGroups project provides a chat application that is great for testing your configuration.  To set up the chat application,
first create two EC2 nodes, following the Setting Up EC2 instructions.  Once the nodes are created, SSH into each machine and
install the java 6 JDK, Maven 3, and Git.

```
sudo apt-get install openjdk-6-jdk
wget http://www.carfab.com/apachesoftware/maven/binaries/apache-maven-3.0.4-bin.tar.gz
sudo tar xzf apache-maven-3.0.4-bin.tar.gz /opt
ln -s /opt/maven /opt/apache-maven-3.0.4
echo "export PATH=/opt/maven/bin:$PATH" >> .profile
. ~/.profile
sudo apt-get install git
mkdir ~/git
```

Now your machine is ready to compile maven projects.

Next, clone and build this project.

```
cd ~/git
git clone git://github.com/meltmedia/jgroups-aws.git
cd jgroups-aws
mvn clean install
```

Finally, it is time to run the project.  You will need to edit the configuration in conf/aws_ping.xml.  Add values for the
tags, access_key and secret_key attributes.  Remove the filters attribute.  Then execute the following:

```
mvn exec:java -Dexec.mainClass="org.jgroups.demos.Chat" -Dexec.args="-props conf/aws_ping.xml" -Djava.net.preferIPv4Stack=true
```

Request timeout bug
-------------------
If you are seeing the following error in you logs. You may wish to upgrade to the 1.2.0 version, as this seems to fix the problem:

```
Status Code: 400, AWS Service: AmazonEC2, AWS Request ID: 6ec551f5-7f56-493f-a213-b8c5cb3e856d, AWS Error Code: RequestExpired, AWS Error Message: Request has expired.
```

