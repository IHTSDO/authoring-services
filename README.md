# Authoring Services

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
