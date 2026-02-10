# Code Review - Simple Java Project

A simple Java project demonstrating basic Java application structure with Maven build configuration.

## Project Structure

```
codeReview/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── quider/
    │               └── codereview/
    │                   └── HelloWorld.java
    └── test/
        └── java/
            └── com/
                └── quider/
                    └── codereview/
                        └── HelloWorldTest.java
```

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Building the Project

To compile the project:

```bash
mvn compile
```

## Running the Application

To run the HelloWorld application:

```bash
mvn exec:java -Dexec.mainClass="com.quider.codereview.HelloWorld"
```

Or build the JAR and run it:

```bash
mvn package
java -jar target/codereview-1.0.0.jar
```

## Running Tests

To run all tests:

```bash
mvn test
```

## Features

- Simple HelloWorld application with a main method
- JUnit 5 test cases
- Maven build configuration
- Proper Java package structure
