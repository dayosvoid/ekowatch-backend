# Team 1 AI Interaction Log (EkoWatch Backend)

As required by the Capstone specification, this document records the AI tools used during the development of EkoWatch, what tasks were requested, what was accepted, and concrete cases where AI output was checked, found incorrect or problematic, and what was done instead.

---

## 1. Tool and Workflow Overview

- **Tool:** Antigravity (Google DeepMind) AI Coding Assistant.
- **Engineers:** 
  - Adedayo Aboderin (Engineer A — Data & Rules)
  - Esther Ebagua (Engineer B — API & Access)
- **Role Boundary:** Engineer B owned the HTTP controllers, OpenAPI 3.0.3 contract, authentication & RBAC, photo sanitisation & Cloudinary integration, RFC 9457 problem details error handling, CORS, and deployment pipeline.

---

## 2. Tasks Requested and Accepted

| Area | Request to AI | Accepted Implementation |
| --- | --- | --- |
| **Authentication & RBAC** | Implement session creation and authentication filter supporting stateless HMAC-SHA256 signed Bearer tokens with role guards. | Implemented `BearerTokenService`, `AuthService`, and `BearerTokenAuthenticationFilter` enforcing `RESIDENT`, `OPERATOR`, and `AUTHORITY` roles with configurable token expiration (`EKOWATCH_TOKEN_TTL_SECONDS`). |
| **Photo Sanitisation** | Implement photo upload that strips all metadata (EXIF, GPS coordinates, XMP, ICC) and enforces size & format restrictions. | Built `PhotoSanitizer` inspecting magic bytes (`FF D8 FF` for JPEG, `89 50 4E 47` for PNG), rejecting spoofed MIME types, enforcing a strict 5 MiB ceiling (`413 PHOTO_TOO_LARGE`), and decoding/re-encoding pixels through `BufferedImage` and `ImageIO`. |
| **Cloud Storage** | Integrate Cloudinary for cloud photo persistence while resolving public URLs and never exposing raw internal storage keys. | Built `CloudinaryPhotoStorage` performing signed SHA-1 uploads and public URL resolution, matching the project's zero-leakage security rule. |
| **Uniform Error Format** | Map all application exceptions, validation errors, and security denials to RFC 9457 `application/problem+json`. | Configured `ApiExceptionHandler` and `ApiError` to return standard problem details (`type`, `title`, `status`, `code`, `detail`, `instance`, `requestId`, `fieldErrors[]`). |
| **OpenAPI & Swagger UI** | Register `bearerAuth`, problem detail error responses, and controller metadata in OpenAPI 3.0.3 and live Swagger UI. | Created `OpenApiConfig` with `OpenApiCustomizer` adding reusable problem detail schemas and standard HTTP error response examples (`400`, `401`, `403`, `404`, `409`, `413`, `415`). |

---

## 3. Cases Where AI Output Was Checked, Found Wrong, and Corrected

The Capstone rubric requires:
> *"A log recording no errors will be read as a log in which no checking took place."*

Below are three critical issues where AI-generated proposals were tested, caught by automated tests or compiler checks, and corrected:

### Case 1: Global `PhotoUrlResolver` Bean Collision with Test Configurations
- **What the AI proposed:**  
  The AI initially generated a `@Configuration` class (`PhotoStorageConfig.java`) that registered a global `@Bean PhotoUrlResolver` whenever Cloudinary was configured.
- **How it was checked:**  
  Ran full Maven verification: `mvn -B verify`.
- **What was actually true:**  
  The build broke with `NoUniqueBeanDefinitionException` across `ConfirmationFreshnessTest`, `ReportIdempotencyTest`, and `ResidentReportApiTest`. Those test suites defined their own `@TestConfiguration` resolver beans, which collided with the global bean. Furthermore, `PostgresRulesTest.missingPhotoUrlAdapterNeverExposesStorageKey` asserted that `photoUrl` must be `null` when no resolver is active.
- **What was done instead:**  
  Rejected the global `@Configuration` bean. Deleted `PhotoStorageConfig.java` and injected `CloudinaryPhotoStorage` directly into `PhotoService` as an internal fallback that only activates if Cloudinary is explicitly configured and no test resolver bean exists in the application context.

---

### Case 2: ArchUnit Spring Modulith Boundary Violation
- **What the AI proposed:**  
  The AI initially grouped several controller unit tests (`UserController`, `WardController`, `OperatorController`, and `AuthorityController`) into a single test class (`AuthorityAndLookupControllerTest`) placed inside package `com.tesa.ekowatch.authority.web`.
- **How it was checked:**  
  Ran the architecture boundary test: `mvn test -Dtest=ModuleBoundaryTest`.
- **What was actually true:**  
  ArchUnit failed the test run:
  ```text
  Architecture Violation [Priority: MEDIUM] - Rule 'Module 'authority' must not depend on module 'user'' was violated:
  Constructor <com.tesa.ekowatch.authority.web.AuthorityAndLookupControllerTest> calls constructor <com.tesa.ekowatch.user.web.UserController>
  ```
  Spring Modulith strictly forbids cross-module dependencies even within test classes unless accessed through exported `.api` packages.
- **What was done instead:**  
  Refactored the test suite to strictly respect module boundaries: created separate controller tests in their own respective packages (`UserControllerTest` in `user.web`, `WardControllerTest` in `ward.web`, `CollectionControllerTest` in `collection.web`, and `AuthorityControllerTest` in `authority.web`). `ModuleBoundaryTest` now passes cleanly.

---

### Case 3: British vs American Spelling in SpringDoc OpenAPI Customizer
- **What the AI proposed:**  
  In `AuthAndSecurityTest.java`, the AI wrote:
  ```java
  openApiCustomizer.customize(openApi);
  ```
- **How it was checked:**  
  Compiled the test sources using `mvn test-compile`.
- **What was actually true:**  
  The Java compiler rejected the code:
  ```text
  cannot find symbol: method customize(io.swagger.v3.oas.models.OpenAPI)
  location: interface org.springdoc.core.customizers.OpenApiCustomizer
  ```
  The SpringDoc library authored by Baeldung/community uses British English for this method: `customise(OpenAPI)`.
- **What was done instead:**  
  Updated the method call to `.customise(openApi)`. The test compiled and passed.

---

## 4. Summary

Every line of AI-assisted code was subjected to automated verification (`mvn -B verify`, 107 tests across unit, integration, Modulith architecture, and Testcontainers database suites). No unchecked AI output was merged into the codebase.
