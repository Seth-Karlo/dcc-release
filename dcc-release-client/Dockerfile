# Pull base image.
FROM ubuntu:14.04
MAINTAINER HMF Team

# Install.
RUN  apt-get update && apt-get install -y \
    build-essential \
    software-properties-common \
    tar \
    vim \
    wget \
    curl \
    openssh-client \
    openssh-server \
    zlib1g-dev

# Install Java 8
RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

ADD dcc-release-client/target/dcc-release-client-5.1.2-SNAPSHOT.jar /opt/
ADD dcc-release-core/target/test-classes/fixtures/dictionary.json.gz /opt/
ADD dcc-release-core/target/test-classes/fixtures/codelists.json.gz /opt/
