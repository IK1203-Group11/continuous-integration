# Continous integration 
This project is a custom-built Continuous Integration (CI) server developed for the DD2480 Software Engineering course at KTH Royal Institute of Technology. The server automates the build process by listening for GitHub webhooks, triggering automated compilation and testing upon every push. It provides immediate feedback to developers by updating commit statuses directly on GitHub.

---

## Tech Stack & Dependencies

This project was built using a lightweight Java stack, centered around an embedded Jetty server for high performance and Jackson for efficient data handling.

## Environment
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

---

## How to run the program 





---

## Core CI Features Implementation 

This section details how the server fulfills the core requirements for compilation and automated testing as defined in the project assessment.

### Core CI Feature #1 - Compilation
* **Implementation**: The server identifies the push event and extracts the branch name and clone URL using `GitHubPayloadParser.java`. 
* **Workspace Isolation**: To ensure a clean build environment, the server creates an isolated temporary directory for every build using `Files.createTempDirectory`.
* **Execution**: The `BuildExecutor.java` uses `ProcessBuilder` to execute `git clone` and `git checkout` on the specified branch. It then executes `mvn test`, which implicitly performs a compilation and static syntax check of the Java 17 source code.
* **Trigger**: The process is triggered via a GitHub Webhook configured to send a POST request to the `ContinuousIntegrationServer.java`.
* **Verification**: The grader can observe the compilation progress in the server console, as all process output is redirected to `System.out` via a `BufferedReader`.

### Core CI Feature #2 - Testing
* **Implementation**: Once the project is cloned and checked out, `BuildExecutor.java` invokes the command `mvn test`.
* **Automated Feedback**: The server captures the exit code of the Maven process using `p.waitFor()`. An exit code of `0` indicates success, while any other value (such as a failed test assertion) is interpreted as a failure.
* **Assessment Strategy**: To verify this, the grader can change an assertion oracle in the `assessment` branch. The CI server will detect the failure via the exit code and print "Tests failed" to the console.
* **Internal Testing**: The parsing logic in `GitHubPayloadParser.java` and the command execution logic in `BuildExecutor.java` are themselves unit-tested to ensure the CI server operates reliably.

### Core CI Feature #3 - Notification


---

## Documentation & generation 



--- 


## Contribution 

| Team member | GitHub username | Responsibility | Tasks |
| :--- | :--- | :--- | :--- |
| **Dawa** | `Dawacode` | Initial integration | Set up GitHub Webhooks, ngrok tunnel, initial repo structure and initial Jetty server request handling. Create ReadMe instructions on how to setup the environment |
| **Amanda** | `Amanda-zakir` | Logic and Test | Developed `dummy.java` and core application logic for the demo and its subsequent tests. |
| **Edvin** | `Edvin-Livak` |  Parser | Implemented JSON parsing to extract branch info and commit SHAs and created the test for the parsing. |
| **Yusuf** | `yusufcanekin` | Automation | Built the `ProcessBuilder` logic to automate `git clone`, `git checkout`, and `mvn test` execution. |
| **Jafar** | `sund02` | Notification | Implemented the GitHub Commit Status REST API to send Success/Failure results back to the repo. |
| **Dawa** | `Dawacode`| Quality & SEMAT | Managed Javadoc generation, project licensing, README maintenance, and the SEMAT Team evaluation. |

---



---

## Essence evaluation 

Our team has reached the Adjourned state. Having successfully completed the project and delivered all final requirements, we have fulfilled the team mission defined in the Seeded phase and handed over all responsibilities, satisfying the primary criteria for this final state. We worked through the Seeded and Formed state by holding initial meetings where we lay the ground work for the work distribution as well as group commitment. 
We effectively transitioned from the Collaborating state through the Performing state by consistently having checkups on work progress and meeting commitments and addressing technical hurdles without external help. 
All team members have now completed their individual tasks. There are no remaining obstacles to reaching a further state, as Adjourned is the final stage of the Team alpha. However, the final step in our process is a formal retrospective to archive the "lessons learned" before we officially cease all effort on this specific mission.

--- 

## License 

This project is licensed under the MIT License — see the LICENSE file.
