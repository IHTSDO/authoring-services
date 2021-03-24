# Changelog
All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
The change log format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

# 6.0.0 Release - Java 11 Upgrade

## Breaking
- Java 11 now required to build
- Java 11 now required to run
- Spring Boot 2.x upgrade (check configuration changes)


# 5.24.0 Release - 2021-01-13
Minor improvements and fixes.

## Improvements
- UI State files can now be stored locally (default) or in AWS S3 for stateless deployments.

## Fixes
- Fix assigning task reviewers when special character (umlaut) in reviewer name.
- Fix missing email for reviewer when doing batch import.
