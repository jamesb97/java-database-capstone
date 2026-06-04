# Schema Architecture

## Section 1: Architecture Summary

This Spring Boot application uses both MVC and REST controllers. Thymeleaf templates are used for the Admin and Doctor dashboards, while the REST APIs serve the remaining modules. The application interacts with two databases:

- MySQL (for patient, doctor, appointment, and admin data)
- MongoDB (for prescriptions).

All controllers route requests through a common service layer, which delegates to the appropriate repositories. MySQL contains JPA entities while MongoDB uses document models.

## Section 2: Numbered flow of data and control

1. The user interface layer supports multiple interaction patterns and types that include a **Thymeleaf-based web dashboards**, such as the `AdminDashboard` and `DoctorDashboard`, and **REST API clients** that interact with the backend via HTTP in receiving JSON responses.
2. Backend **Controller** based interactivity, which serve as the entry points into the backend application, gets routed based on the URL path and HTTP method when a user submits a form or clicks on a button. The requests that handle Thymeleaf Controllers server-rendered views, return `.html` templates filled with dynamic data rendered in the browser. Requests from API consumers are handled by REST controllers, that process input, call backend logic, and return responses in a JSON format.
3. The business rules and logic apply to the **Service Layer**, which act as the backend for the system in ensuring a clean separation between the controller and data access.
4. Service Layer communicates directly with the **Repository Layer** in order to perform data access operations that includes the **MySQL** Repositories to manage structured relational data to include patients, doctors, admin records, and appointments. The **MongoDB** repository also performs operations using Spring Data MongoDB in managing document-based records to include prescriptions.
5. The repository layer directly interfaces with the underlying database engine, where MySQL stores all the core entities such as the users, roles, and appointments, and MongoDB stores the flexible nested data structure that includes prescriptions
6. After the data get retrieved from the database, it then gets mapped into the Java model classes, in a process known as **model binding**. For MySQL, the data gets converted into a JPA entity, which represents rows in relational tables and get annotated with `@Entity`. For MongoDB, the data gets loaded onto **document objects**, typically annotated with `@Document`.
7. The final step in the process includes the bound models: MVC Flows and REST Flows. **MVC Flows**, which are models passed from the controller to the Thymeleaf templates, in which they get rendered as dynamic HTML for the browser. **REST Flows** gets serialized into JSON and get sent back to the client as part of the HTTP response.
