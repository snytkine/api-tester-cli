# Test Suite Configuration

A test suite is defined in YAML format with a top-level structure, HTTP request definitions, and assertions.

## Top-level fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `name` | **Yes** | String | Display name for the test suite |
| `description` | No | String | Human-readable description of what the suite tests |
| `rest-client` | Yes¹ | Object | Single default HTTP client (base URL, timeout, default headers, auth) |
| `rest-clients` | Yes¹ | List | Multiple named HTTP clients, each with an `id` |
| `variables` | No | Map | Suite-level template variables; supports Thymeleaf expressions |
| `tests` | **Yes** | List | Ordered list of test cases |

¹ Exactly one of `rest-client` (singular) or `rest-clients` (plural) must be present.
Declaring neither — or both — is a validation error.

## HTTP clients: `rest-client` / `rest-clients`

Every suite must declare its HTTP client(s) using **exactly one** of two mutually
exclusive keys.

### `rest-client` (singular) — one default client

Shorthand for a suite that talks to a single service. No `id` is needed; this block is
always the default client used by every request.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `base-url` | String | `""` (empty) | Base URL prepended to all relative test URLs. Test URLs are used as-is when empty. |
| `connect-timeout` | Integer | `30000` | Connection timeout in milliseconds. |
| `headers` | Map | (none) | Default HTTP headers sent with every request. Per-test headers take precedence for same-named keys. |
| `auth` | Object | (none) | Optional HTTP Basic Auth applied as default to all requests in the suite. See "Authentication" section below. |

```yaml
rest-client:
  base-url: "https://api.example.com"
  connect-timeout: 60000
  headers:
    x-api-key: "test-key-123"
    Accept: "application/json"
```

### `rest-clients` (plural) — multiple named clients

For suites that call more than one service or environment. Each entry supports all the
fields above **plus** an `id`. A request selects a client with its own `rest-client`
property; requests without one use the client whose `id` is `default`.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique client id. **Required when more than one client is configured.** Optional (implicitly `default`) when the list has exactly one entry. |

```yaml
rest-clients:
  - id: default
    base-url: "https://api.example.com"
    connect-timeout: 30000

  - id: payments
    base-url: "https://payments.example.com"
    connect-timeout: 10000

tests:
  - name: "Pay invoice"
    request:
      rest-client: payments   # selects the 'payments' client by id
      method: "POST"
      url: "/invoices/pay"
    assertions:
      - type: "status_code"
        expected: 200

  - name: "List users"
    request:
      method: "GET"           # no rest-client → uses the 'default' client
      url: "/users"
    assertions:
      - type: "status_code"
        expected: 200
```

**Validation rules** (checked before execution, at the same stage as the unique
test-name check):

1. Exactly one of `rest-client` / `rest-clients` must be present — *"Test suite must
   define exactly one of 'rest-client' or 'rest-clients', but found neither / …found both"*.
2. `id` values in `rest-clients` must be unique — *"Duplicate rest-client id: '<id>'"*.
3. When multiple clients are configured, every entry must have an `id` — *"rest-client at
   index <n> is missing required 'id' (required when multiple rest-clients are configured)"*.
4. A request's `rest-client` id must reference a defined client — *"Test '<name>'
   references unknown rest-client id: '<id>'"*.

The per-request `rest-client` selector is only meaningful with the plural `rest-clients`
form; when the singular `rest-client` form is used it is ignored (a warning is logged) and
the default client is used.

### Authentication

Declare HTTP Basic Auth on a client to apply credentials to every request that uses it:

```yaml
rest-client:
  base-url: "https://api.example.com"
  auth:
    type: "basic"
    username: "[[${env.API_USER}]]"
    password: "[[${env.API_PASSWORD}]]"
```

**Best Practice for Credentials:**

Never hardcode usernames and passwords directly in your test suite YAML. Instead:

1. **Use a `.env` file** — Create a `.env` file in the same directory as your suite YAML:

   ```dotenv
   API_USER=myuser
   API_PASSWORD=mypassword
   ADMIN_USER=admin
   ADMIN_PASSWORD=adminpass
   ```

   Then reference them in your suite:

   ```yaml
   rest-client:
     auth:
       type: "basic"
       username: "[[${env.API_USER}]]"
       password: "[[${env.API_PASSWORD}]]"
   ```

2. **Use environment variables** — Export them before running the CLI:

   ```bash
   export API_USER=myuser
   export API_PASSWORD=mypassword
   java -jar api-tester-cli-0.1.1-SNAPSHOT.jar run-suite --suite ./suite.yml
   ```

   Then reference them the same way: `[[${env.API_USER}]]`

3. **Use CI/CD secrets** — In GitHub Actions or other CI systems, set environment variables from secrets and reference them the same way.

#### Authentication Precedence

When multiple authentication methods are specified, they follow this precedence (lowest to highest):

1. **Client-level default** (`rest-client.auth`) — applied to all requests using that client
2. **Per-request override** (`request.auth`) — overrides the client's auth for that test
3. **Explicit header** (`request.headers.Authorization`) — always takes precedence

Example demonstrating precedence:

```yaml
rest-client:
  base-url: "https://api.example.com"
  auth:
    type: "basic"
    username: "[[${env.API_USER}]]"
    password: "[[${env.API_PASSWORD}]]"

tests:
  - name: "Test with suite-level auth"
    request:
      method: "GET"
      url: "/data"
    # Uses rest-client.auth credentials automatically

  - name: "Test with per-request auth override"
    request:
      method: "GET"
      url: "/admin/users"
      auth:
        type: "basic"
        username: "[[${env.ADMIN_USER}]]"
        password: "[[${env.ADMIN_PASSWORD}]]"
    # Uses request.auth credentials (overrides suite default)

  - name: "Test with explicit Authorization header"
    request:
      method: "GET"
      url: "/special"
      headers:
        Authorization: "Bearer [[${env.CUSTOM_TOKEN}]]"
    # Uses the explicit Authorization header (highest precedence)
    # request.auth (if present) would be ignored
```

## `variables` block (optional)

Suite-level key/value pairs available as `[[${suite.my_var}]]` in test case URLs, headers, and body content.

Variables support Thymeleaf expressions and can reference `[[${cli.*}]]` and `[[${env.*}]]` values:

```yaml
variables:
  api_base_url: "[[${cli.api_url}]]"
  admin_system: "[[${cli.admin_system}]]"
  last_updated: "[[${#temporals.format(#temporals.createToday(), 'yyyy-MM-dd')}]]"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"
  environment: "[[${cli.env != null ? cli.env : 'staging'}]]"
```

## Test case fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `name` | **Yes** | String | Unique identifier within the suite; displayed in terminal UI and JSON output |
| `description` | No | String | What this test verifies |
| `tag` | No | String or List | Single tag or list of tags for filtering with `--tag`. Supports Thymeleaf. |
| `skip` | No | String | When non-blank, the test is skipped and this value is recorded as the skip reason. Supports Thymeleaf. |
| `variables` | No | Map | Per-test-case key/value pairs available as `[[${test.my_var}]]` |
| `request` | **Yes** | Object | HTTP request definition |
| `assertions` | **Yes** | List | One or more assertion definitions to evaluate against the response |

## Request definition

### Methods with request body

For POST, PUT, PATCH, DELETE:

```yaml
request:
  method: "POST"           # required
  url: "/users"            # required
  headers:                 # optional
    Content-Type: "application/json"
    x-request-id: "[[${suite.request_id}]]"
  body:                    # optional
    # (body definition below)
```

### Methods without request body

For GET, HEAD, OPTIONS, TRACE:

```yaml
request:
  method: "GET"            # required
  url: "/users/123"        # required
  headers:                 # optional
    Accept: "application/json"
```

## Body definition

### Inline string (shorthand)

```yaml
body: '{"name": "Alice", "age": 30}'
```

Equivalent to:

```yaml
body:
  type: "string"
  content: '{"name": "Alice", "age": 30}'
```

### Object form with inline string

```yaml
body:
  type: "string"
  content: '{"username": "[[${test.username}]]", "password": "[[${test.password}]]"}'
```

### File reference

Load the body from a file relative to the suite YAML directory:

```yaml
body:
  type: "file"
  content: "request-bodies/login.json"
```

The file content is processed as a Thymeleaf template, so expressions like `[[${test.username}]]` and `[[${suite.request_id}]]` are resolved:

**request-bodies/login.json:**
```json
{
  "username": "[[${test.username}]]",
  "password": "[[${test.password}]]",
  "requestId": "[[${suite.request_id}]]"
}
```

## Assertions

A test must include at least one assertion. Assertions are evaluated after the HTTP response is received.

```yaml
assertions:
- type: "status_code"
  expected: 200
- type: "json_match"
  path: "response.body.json"
  expected:
    type: "inline"
    content: '{"message": "success"}'
- type: "response_time"
  milliseconds: 5000
```

For `json_match` and `json_schema`, the `expected` field also accepts a plain string as a shorthand for `type: inline`, mirroring the request `body` shorthand above:

```yaml
- type: "json_match"
  path: "response.body.json"
  expected: '{"message": "success"}'
```

See [Assertions](assertions.md) for the full list of 25+ assertion types and examples.

## Complete example

```yaml
name: "User Management API Test Suite"
description: "Test create, read, update, delete user operations"

rest-client:
  base-url: "https://api.example.com"
  connect-timeout: 30000
  headers:
    Accept: "application/json"
  auth:
    type: "basic"
    username: "[[${env.API_USER}]]"
    password: "[[${env.API_PASSWORD}]]"

variables:
  api_base_url: "https://api.example.com"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"
  timestamp: "[[${#temporals.format(#temporals.createToday(), 'yyyy-MM-dd')}]]"

tests:
- name: "Create new user"
  description: "Verify user creation with valid payload"
  variables:
    username: "testuser"
    email: "test@example.com"
  request:
    method: "POST"
    url: "/users"
    headers:
      Content-Type: "application/json"
      x-request-id: "[[${suite.request_id}]]"
    body:
      type: "file"
      content: "payloads/create-user.json"
  assertions:
  - type: "status_code"
    expected: 201
  - type: "json_match"
    path: "response.body.json"
    expected:
      type: "file"
      content: "expected-responses/create-user.json"
      ignore:
        - "id"
        - "createdAt"
  - type: "response_time"
    milliseconds: 1000

- name: "Fetch user by ID"
  description: "Retrieve user details by ID"
  request:
    method: "GET"
    url: "/users/123"
    headers:
      x-request-id: "[[${suite.request_id}]]"
  assertions:
  - type: "status_code"
    expected: 200
  - type: "json_schema"
    path: "response.body.json"
    expected:
      type: "file"
      content: "schemas/user-schema.json"
  - type: "not_null"
    path: "response.body.json.id"

- name: "List protected admin data"
  description: "Fetch data with different admin credentials"
  request:
    method: "GET"
    url: "/admin/data"
    auth:
      type: "basic"
      username: "[[${env.ADMIN_USER}]]"
      password: "[[${env.ADMIN_PASSWORD}]]"
  assertions:
  - type: "status_code"
    expected: 200
  - type: "array_is_not_empty"
    path: "response.body.json"
```

---

See [Templating](templating.md) to learn how to use the four variable namespaces (`cli`, `env`, `suite`, `test`) and available template utilities.
