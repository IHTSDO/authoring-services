## Setup Guide

This is work in progress, contributions welcome!

- Create a blank MySQL database called ts_review
  - Add connection details to the _spring.datasource_ section of the configuration
  - The schema will be created the first time the application starts
- Authoring Services must be set up as an Application Link within Jira
  - This allows Authoring Services to impersonate any user in Jira
  - An SSH key pair must be generated. The Public Key is installed into Jira during the Application Link setup.
  - The Private Key file should be deployed with Authoring Services with the name in configuration as _jira.privateKeyName_. 
- Validation Resource files are maintained within Snomed International S3 storage but a copy is available [here](https://github.com/IHTSDO/validation-resources)
