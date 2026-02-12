# Continous integration 
This project is a custom-built Continuous Integration (CI) server developed for the DD2480 Software Engineering course at KTH Royal Institute of Technology. The server automates the build process by listening for GitHub webhooks, triggering automated compilation and testing upon every push. It provides immediate feedback to developers by updating commit statuses directly on GitHub.

---

## Tech Stack & Dependencies

This project was built using a lightweight Java stack, centered around an embedded Jetty server for high performance and Jackson for efficient data handling.

### Environment
* **Runtime:** Java JDK 17 (LTS)
* **Build Tool:** Maven 

### Core Dependencies
* **Web Server:** [Jetty Server/Servlet](https://www.eclipse.org/jetty/) (v${jetty.version}) - Used as the embedded container.
* **Servlet API:** [Java Servlet API](https://javaee.github.io/servlet-spec/) (v3.1.0) - Standard interface for web components.
* **JSON Processor:** [Jackson Databind](https://github.com/FasterXML/jackson) (v2.17.1) - Handles data binding and JSON serialization.

### Development & Testing
* **Testing Framework:** [JUnit 4](https://junit.org/junit4/) (v4.13.2) - Utilized for unit testing and ensuring code reliability during the Performing state.

---
## CI Server Setup


### 1. Start the Server
Compile and launch the Java Jetty server:
```bash
mvn compile exec:java
```


### 2. Install ngrok
To bridge the gap between GitHub and your local Mac (the "Cloud Server" from our plan), you need to install ngrok. This tool creates a secure tunnel to your local port :
```bash
brew install ngrok/ngrok/ngrok


```


### 3. Authenticate your Account
Before you can use the tunnel, you must link your local installation to your ngrok account.
Create a ngrok account if you dont already have one. Replace <YOUR_TOKEN_HERE> with the token from your ngrok dashboard:
```bash


ngrok config add-authtoken <YOUR_TOKEN_HERE>


```


### 4. Launch your tunnel
Finally, start the tunnel to make your local server visible to the internet:
```bash


ngrok http 8080


```


### 4. Setup the webbhook
Copy the Forwarding URL from your ngrok terminal (e.g., https://xxxx.ngrok-free.dev).


Go to your GitHub repository: Settings > Webhooks > Add webhook.


Paste the URL into the Payload URL field.


Set Content type to application/json.


Click Add webhook.


### 5. Persistant logs 
The persistance log can be found in the following link : https://flukeless-horacio-unhawked.ngrok-free.dev/builds 



---

## Core CI Features Implementation 

This section details how the server fulfills the core requirements for compilation and automated testing as defined in the project assessment.

### 1. Core CI Feature - Compilation
* **Implementation**: The server identifies the push event and extracts the branch name and clone URL using `GitHubPayloadParser.java`. 
* **Workspace Isolation**: To ensure a clean build environment, the server creates an isolated temporary directory for every build using `Files.createTempDirectory`.
* **Execution**: The `BuildExecutor.java` uses `ProcessBuilder` to execute `git clone` and `git checkout` on the specified branch. It then executes `mvn test`, which implicitly performs a compilation and static syntax check of the Java 17 source code.
* **Trigger**: The process is triggered via a GitHub Webhook configured to send a POST request to the `ContinuousIntegrationServer.java`.
* **Verification**: The grader can observe the compilation progress in the server console, as all process output is redirected to `System.out` via a `BufferedReader`.

### 2. Core CI Feature - Testing
* **Implementation**: Once the project is cloned and checked out, `BuildExecutor.java` invokes the command `mvn test` which both compiles and tests the demo project.
* **Automated Feedback**: The server captures the exit code of the Maven process using `p.waitFor()`. An exit code of `0` indicates success, while any other value (such as a failed test assertion) is interpreted as a failure. We are therfore able to capture error in both the test as well as any compilation failures.
* **Assessment Strategy**: To verify this, the grader can change an assertion oracle in the `assessment` branch. The CI server will detect the failure via the exit code and print "Tests failed" to the console.
* **Internal Testing**: The parsing logic in `GitHubPayloadParser.java` and the command execution logic in `BuildExecutor.java` are themselves unit-tested to ensure the CI server operates reliably.

### 3. Core CI Feature - Notification

* **Implementation**: The server utilizes the `GitHubStatusNotifier.java` class to communicate build results back to GitHub via the REST API.
* **Authentication**: The system authenticates requests using a Personal Access Token (PAT) stored in the `GITHUB_TOKEN` environment variable.
* **Feedback Loop**: Upon completion of the build and test process, the server sends a POST request to GitHub's statuses endpoint (`/repos/{owner}/{repo}/statuses/{sha}`).
* **State Mapping**: The server maps the build outcome to GitHub states: a successful Maven execution results in a `"success"` status, while a failed execution or test assertion sends a `"failure"` status.
* **Deep Linking (Details Link)**: If a `CI_PUBLIC_URL` is configured, the notification includes a `target_url` that creates a "Details" button in the GitHub UI.
* **Log Accessibility**: Clicking "Details" redirects the developer to the CI server's log endpoint (`/build/<buildId>`), allowing for immediate inspection of build logs.



##### Unit Testing the Notification
The notification logic is verified in `GitHubStatusNotifierTest.java` using a "safe behavior" strategy to ensure reliability without making real network calls:
* **Console Capture**: The tests use `ByteArrayOutputStream` to capture and verify `System.out` log messages produced by the notifier.
* **Safe Execution**: Tests verify that the system handles missing environment variables (like `GITHUB_TOKEN`) gracefully without throwing exceptions.
* **Input Validation**: Unit tests exercise early-return paths for invalid data, such as improperly formatted repository names or missing commit SHAs.
* **Environment Awareness**: Uses `Assume.assumeTrue` to skip tests that might trigger real HTTP calls if a production token is detected in the local environment.
---

## Documentation & generation

The project utilizes **Javadoc** to maintain formal technical documentation of the CI server's internal architecture, utility classes, and API endpoints.

### 1. Generating Javadoc
Documentation generation is automated via the `maven-javadoc-plugin`. To generate the HTML documentation set, execute the following command in your terminal:

```bash
mvn javadoc:javadoc
```

### 2. View Javadoc

* **Location**: The generated HTML files are stored in the target/site/apidocs/ directory. 
* **Entry Point**: Open index.html in any web browser to explore the class hierarchies, method descriptions, and parameter requirements. 


## Contribution 

| Team member | GitHub username | Responsibility | Tasks |
| :--- | :--- | :--- | :--- |
| **Dawa** | `Dawacode` | Initial integration | Set up GitHub Webhooks, ngrok tunnel, initial repo structure and initial Jetty server request handling. Create ReadMe instructions on how to setup the environment |
| **Amanda** | `Amanda-zakir` | Logic and Test | Developed `dummy.java` and core application logic for the demo and its subsequent tests. |
| **Edvin** | `Edvin-Livak` |  Parser | Implemented JSON parsing to extract branch info and commit SHAs and created the test for the parsing. |
| **Yusuf** | `yusufcanekin` | Automation | Built the `ProcessBuilder` logic to automate `git clone`, `git checkout`, and `mvn test` execution. |
| **Jafar** | `sund02` | Notification | Implemented the GitHub Commit Status REST API to send Success/Failure results back to the repo; created PR and issue templates; implemented webhook signature verification (HMAC SHA-256); and implemented persistent build log history with build listing and per-build log URLs. |
| **Dawa** | `Dawacode`| Quality & SEMAT | Managed Javadoc generation, project licensing, README maintenance, and the SEMAT Team evaluation. |




---

## Essence evaluation 

Our team has reached the Adjourned state. Having successfully completed the project and delivered all final requirements, we have fulfilled the team mission defined in the Seeded phase and handed over all responsibilities, satisfying the primary criteria for this final state. We worked through the Seeded and Formed state by holding initial meetings where we lay the ground work for the work distribution as well as group commitment. 
We effectively transitioned from the Collaborating state through the Performing state by consistently having checkups on work progress and meeting commitments and addressing technical hurdles without external help. 
All team members have now completed their individual tasks. There are no remaining obstacles to reaching a further state, as Adjourned is the final stage of the Team alpha. However, the final step in our process is a formal retrospective to archive the "lessons learned" before we officially cease all effort on this specific mission.

--- 

## License 

This project is licensed under the MIT License — see the LICENSE file.
