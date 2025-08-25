# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot email service sample project using Java 8. It's currently a basic Spring Boot application structure without email functionality implemented yet.

## Build and Run Commands

### Build the project
```bash
# Windows
./mvnw.cmd clean package

# Unix/Mac
./mvnw clean package
```

### Run the application
```bash
# Windows
./mvnw.cmd spring-boot:run

# Unix/Mac
./mvnw spring-boot:run
```

### Run tests
```bash
# Windows
./mvnw.cmd test

# Unix/Mac
./mvnw test
```

### Run a single test class
```bash
# Windows
./mvnw.cmd test -Dtest=EmailServiceSampleApplicationTests

# Unix/Mac
./mvnw test -Dtest=EmailServiceSampleApplicationTests
```

## Project Structure

This is a Maven-based Spring Boot 2.7.9 application with the following key components:

- **Main Application**: `EmailServiceSampleApplication.java` - Spring Boot entry point
- **Configuration**: `application.properties` - Spring configuration
- **Dependencies**: Lombok is included for reducing boilerplate code
- **Java Version**: Java 8

## Development Guidelines

### Email Service Implementation
When implementing email functionality:
1. Consider adding Spring Boot Starter Mail dependency to `pom.xml`
2. Create service classes in `com.igsl.group.email_service_sample.service` package
3. Create configuration classes for email settings
4. Use Lombok annotations to reduce boilerplate code

### Testing
- Use `@SpringBootTest` for integration tests
- Create unit tests for individual components
- Test naming convention: `*Tests.java` for test classes