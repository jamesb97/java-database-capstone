# Smart Clinic Management System

## System Overview

This lab contains contents for the smart clinic management system based from the Java Database Capstone Project course in Coursera. This system contains two databases, MySQL and MongoDB through service and repository layers. It uses both Spring MVC with Thymeleaf for admin/doctor dashboards and REST APIs for other modules.

## Architecture Overview

The Smart Clinic Management System follows a **three-tier web architecture**, which separates the system into three distinct layers:

1. **Presentation Tier** - The user interface, consisting of Thymeleaf templates and REST API consumers.
2. **Application Tier** - The Spring Boot backend that contains the controllers, services, and business logic
3. **Data Tier** - The databases: MySQL for structured data and MongoDB for flexible, document-based data.

This structure improves scalability, maintainability, and deployment flexibility. Each tier can be independently developed, tested, or scaled without impacting the others directly.

### Why this tech stack was chosen?

This tech stack consisting of Spring Boot as the foundation simplifies backend development while enforcing best practices. It supports:

- **Spring MVC** for server-rendered admin and doctor dashboards
- **REST APIs** for modular and scalable client-server communication
- **Spring Data JPA** for interacting with a MySQL database
- **Spring Data MongoDB** for storing flexible prescription records

Spring Boot is developer-friendly, production-ready, and integrates easily with testing, validation, and containerization tools.

#### REST APIs for scalable integration

For modules like appointments, patient dashboards, and patient records, we expose **RESTful APIs** instead of using server-side views. REST allows external clients - such as mobile apps or future web apps - in order to communicate with the backend via lightweight HTTP and JSON.
This makes the system more extensible and interoperable, supporting real-time client applications, third-party integrations, and cross-platform access.

#### Deployability and CI/CD compatibility

Spring Boot applications are easily containerized using Docker, making them ideal for deployment in cloud-native environments like Kubernetes or virtual machines. They start quickly, run reliably across systems, and scale horizontally as needed.

The system also integrates well with modern **CI/CD pipelines** (such as GitHub Actions, Jenkins, or GitLab CI), enabling automated builds, tests, and deployments. This improves release speed, reduces manual errors, and supports continuous delivery of new features and fixes in production.

##### Reference Diagram

This is a simplified architecture diagram of the Smart Clinic Management System. The system

- Renders HTML via Thymeleaf for the admin and doctor dashboards
- Exposes JSON-based REST APIs for appointment and patient modules
- Connects to **MySQL** for structured data (Patient, Doctor, Appointment, Admin)
- Connects to **MongoDB** for unstructured document-based data (Prescription)

![architecture design](https://cf-courses-data.s3.us.cloud-object-storage.appdomain.cloud/0K_HGm3MtJodh-DlzytS2Q/architecture-diagram.png)

### Architecture Walkthrough

This section provides a detailed walkthrough of how the Smart Clinic application is structured, how requests flow through it, and how different technologies interact across the layers. The architecture follows a clean separation of concerns and adheres to Spring Boot best practices.

Each step in the diagram represents a specific tier or logical component in the application and plays a key role in processing requests, applying business logic, and persisting or retrieving data.

#### 1. User Interface Layer

The system supports multiple user types and interaction patterns. Users can access the application through

- **Thymeleaf-based web dashboards** such as `AdminDashboard` and `DoctorDashboard`. These are traditional HTML pages rendered on the server and delivered to the browser.
- **REST API clients** like mobile apps or frontend modules (e.g., `Appointments`, `PatientDashboard`, and `PatientRecord`) that interact with the backend via HTTP and recieve JSON responses.
  This separation allows the system to support both interactive browser views and scalable API-based integrations.

#### 2. Controller Layer

When a user interacts with the application (e.g., clicking a button or submitting a form), the request is routed to a backend **controller** based on the URL path and the HTTP method.

- Requests for server-rendered views are handled by **Thymeleaf Controllers**, which return `.html` templates that will be filled with dynamic data and rendered in the browser.
- Requests from API consumers are handled by **REST Controllers**, which process the input, call backend logic, and return responses in JSON format.

These controllers serve as the entry points into the backend application logic, enforcing request validation and coordinating the request/response flow.

#### 3. Service Layer

All controllers delegate logic to the **Service Layer**,which acts as the heart of the backend system. This layer:

- Applies business rules and validations
- Coordinates workflows across multiple entities (e.g., checking doctor availability before scheduling an appointment)
- Ensures a clean separation between controller logic and data access

By isolating business logic, the application becomes more maintainable, testable, and also easier to scale.

#### 4. Repository Layer

The service layer communicates with the **Repository Layer** to perform data access operations. This layer includes two types of repositories:

- **MySQL Repositories**, which uses Spring Data JPA to manage structured relational data like patients, doctors, appointments, and admin records.
- **MongoDB Repository**, which uses Spring Data MongoDB to manage document-based records like prescriptions.

Repositories abstract the database access logic and expose a simple, declarative interface for fetching and persisting data.

#### 5. Database access

Each repository interfaces directly with the underlying database engine:

- **MySQL** stores all core entities that benefit from a normalized relational schema and constraints - such as users, roles, and appointments.
- **MongoDB** stores flexible and nested data structures, such as prescriptions, which can vary in format to allow for a rapid schema evolution.

This dual-database setup leverages the strengths of both structured and unstructured data storage approaches.

#### 6. Model binding

Once data is retrieved from the database, it is mapped into Java model classes that the application can work with. This process is known as **model binding**.

- In the case of MySQL, data is converted into **JPA entities**, which represent rows in relational tables and are annotated with `@Entity`.
- For MongoDB, data is loaded into **document objects**, typically annotated with `@Document`, which map to BSON/JSON structures in collections.

These model classes provide a consistent, object-oriented representation of the data across the application layers.

#### 7. Application models in use

Finally, the bound models are used in the response layer

- In **MVC flows**, models are passed from the controller to Thymeleaf templates, where they are rendered as dynamic HTML for the browser.
- In **REST flows**, the same models (or transformed DTOs) are serialized into **JSON** and sent back to the client as part of an HTTP response.

This marks the end of the request-response cycle, delivering either a full web page or structured API data, depending on the consumer.
