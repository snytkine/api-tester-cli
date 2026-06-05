# Test Suite Configuration

A test suite is defined in YAML format with a top-level structure, HTTP request definitions, and assertions.

## Top-level fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `name` | **Yes** | String | Display name for the test suite |
| `description` | No | String | Human-readable description of what the suite tests |
| `rest_client` | No | Object | HTTP client configuration (base URL, timeout, default headers) |
| `variables` | No | Map | Suite-level template variables; supports Thymeleaf expressions |
| `tests` | **Yes** | List | Ordered list of test cases |

## `rest_client` block (optional)

Provide suite-level HTTP client defaults:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `base_url` | String | `""` (empty) | Base URL prepended to all relative test URLs. Test URLs are used as-is when empty. |
| `connect_timeout` | Integer | `30000` | Connection timeout in milliseconds. |
| `headers` | Map | (none) | Default HTTP headers sent with every request. Per-test headers take precedence for same-named keys. |

Example:

```yaml
rest_client:
  base_url: "https://api.example.com"
  connect_timeout: 60000
  headers:
    x-api-key: "test-key-123"
    Accept: "application/json"
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

See [Assertions](assertions.md) for the full list of 25+ assertion types and examples.

## Complete example

```yaml
name: "User Management API Test Suite"
description: "Test create, read, update, delete user operations"

rest_client:
  base_url: "https://api.example.com"
  connect_timeout: 30000
  headers:
    Accept: "application/json"

variables:
  api_base_url: "[[${suite.rest_client.base_url}]]"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"
  timestamp: "[[${#temporals.format(#temporals.createToday(), 'yyyy-MM-dd')}]]"

tests:
- name: "Create new user"
  description: "Verify user creation with valid payload"
  variables:
    username: "testuser"
    email: "test@example.com"
    password: "SecurePass123"
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
```

---

See [Templating](templating.md) to learn how to use the four variable namespaces (`cli`, `env`, `suite`, `test`) and available template utilities.
