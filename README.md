# Authoring Services
## Overview
Authoring Services is a component of the Snomed Authoring Platform. 

This REST API has been designed to provide a number of key functions related to workflow and UI support 
which are not suitable for implementation within a general purpose terminology server.

## Capabilities
- Support for Tasks and Projects on top of the Terminology Server branches
  - Workflow and Transitions (via Atlassian Jira)
  - Task lists
    - Classification status from Terminology Service 
    - RVF task branch Validation status (via Orchestration Service)
- Task Review Functionality
  - Lightweight user messaging system (backed by MySQL)
- Interface to the Release Validation Service
- Branch status monitoring
- User Interface Notifications
- UI State Persistence (to support the Authoring-UI component)
- US / GB translation automation

## Quick Start
Use Maven to build the executable jar and run:
```bash
mvn clean package
java -Xmx1g -jar target/authoring-services*.jar
```
Access the service **API documentation** at [http://localhost:8081/authoring-services](http://localhost:8081/authoring-services).

## Setup
### Configuration options
The default configuration of this Spring Boot application can be found in [application.properties](blob/master/src/main/resources/application.properties). The defaults can be overridden using command line arguments, for example set a different HTTP port:
```bash
java -Xmx1g -jar target/authoring-services*.jar --server.port=8099
```
For other options see [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html).

The default username and password (user:password) can be changed using the _security.user.name_ and _security.user.password_ properties.

### Setup Guide
See the [setup guide](docs/setup-guide.md) for more information.
