# Templating

The tool uses **Thymeleaf** in TEXT mode to process YAML files. Any string value in the YAML can include template expressions that are resolved before test execution.

## Syntax

Use `[[${expression}]]` to embed expressions in YAML strings:

```yaml
url: "[[${cli.api_base_url}]]/users"
request_id: "[[${#strings.randomAlphanumeric(12)}]]"
username: "[[${test.username != null ? test.username : 'guest'}]]"
```

## Four variable namespaces

All four are `Map<String, String>` and are available in template expressions.

| Namespace | Access | Source |
|-----------|--------|--------|
| `cli` | `[[${cli.my_var}]]` | Command-line positional arguments passed as `key=value` pairs. Example: `rs --suite=... api_url=https://api.example.com` → `[[${cli.api_url}]]` resolves to `https://api.example.com` |
| `env` | `[[${env.MY_VAR}]]` | Environment variables from the `.env` file (in the suite directory) merged with process environment variables. System environment variables take precedence over `.env` entries with the same name. |
| `suite` | `[[${suite.my_var}]]` | Suite-level `variables` block after resolution. Available **only in test case fields** due to two-pass processing (see below). |
| `test` | `[[${test.my_var}]]` | Per-test-case `variables` block. Available **only during test execution** when resolving request URLs, headers, and body content. |

## Two-pass resolution

Template processing happens in two passes so that suite variables can reference CLI and environment variables, and test cases can reference fully-resolved suite variables.

**Step 1** — Suite variable resolution:
- The full YAML is processed with `cli`, `env`, and empty `suite` map
- The resolved `variables` block is extracted
- Suite variables can reference `[[${cli.*}]]` and `[[${env.*}]]` but NOT `[[${suite.*}]]` (empty at this stage)

**Step 2** — Test case resolution:
- The full YAML is processed again with `suite` set to the resolved variables from Step 1
- Test cases now can reference `[[${suite.*}]]` with fully-resolved values
- Each test case further resolves `[[${test.*}]]` from its own `variables` block

**Consequence:** Suite variables cannot reference other suite variables. Test cases can reference suite and test variables but not vice versa.

Example:

```yaml
variables:
  # Step 1 resolves these with cli + env only
  api_url: "[[${cli.api_url}]]"
  admin_system: "[[${cli.admin}]]"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"

tests:
- name: "Login"
  variables:
    # Step 2 resolves these per-test
    username: "testuser"
    password: "secret"
  request:
    # Can use all four namespaces here
    url: "[[${suite.api_url}]]/login"
    body:
      type: "file"
      content: "login.json"

# In login.json:
# {
#   "username": "[[${test.username}]]",
#   "password": "[[${test.password}]]",
#   "requestId": "[[${suite.request_id}]]"
# }
```

## Missing key behavior

If a map key is referenced but doesn't exist, the expression resolves to an empty string (no exception):

```yaml
url: "[[${cli.missing_key}]]"  # Resolves to "" if cli doesn't have missing_key
```

## Elvis operator not supported

The Elvis operator `?:` is not supported in OGNL (Thymeleaf's expression language). Use a full ternary instead:

```yaml
# WRONG — will not work:
api_url: "[[${cli.api_url ?: 'https://localhost:8080'}]]"

# CORRECT — use full ternary:
api_url: "[[${cli.api_url != null ? cli.api_url : 'https://localhost:8080'}]]"
```

## Available utilities

Thymeleaf provides standard utility objects. Common ones used in test suites:

### Date/time utilities

```yaml
last_updated: "[[${#temporals.format(#temporals.createToday(), 'yyyy-MM-dd')}]]"
# Output: 2026-06-05

timestamp: "[[${#temporals.format(#temporals.now(), 'yyyy-MM-dd HH:mm:ss')}]]"
# Output: 2026-06-05 14:30:45
```

### String utilities

```yaml
request_id: "[[${#strings.randomAlphanumeric(12)}]]"
# Output: aBcD1234XyZw

uppercase: "[[${#strings.toUpperCase(test.username)}]]"
lowercase: "[[${#strings.toLowerCase(test.username)}]]"
contains: "[[${#strings.contains(response_body, 'success')}]]"
```

All other standard Thymeleaf utilities (`#numbers`, `#lists`, `#maps`, `#objects`, etc.) are available.

## `.env` file support

Place a `.env` file in the same directory as your test suite YAML file. Its entries are loaded and merged with process environment variables:

**.env file (in suite directory):**
```
API_KEY=secret-key-123
DB_PASSWORD=my-secure-password
```

In your YAML:

```yaml
request:
  headers:
    Authorization: "Bearer [[${env.API_KEY}]]"

variables:
  db_password: "[[${env.DB_PASSWORD}]]"
```

**Priority:** System environment variables take precedence. If both the `.env` file and a system environment variable define the same key, the system environment variable is used.

**Recommended use:** Store secrets (API keys, passwords, tokens) in `.env` so they don't appear in the YAML or command line, and never commit `.env` to version control.

**Example: HTTP Basic Auth with .env:**

Create a `.env` file in your suite directory:

```
API_USER=myusername
API_PASSWORD=mypassword
```

Then reference them in your suite:

```yaml
rest-client:
  auth:
    type: "basic"
    username: "[[${env.API_USER}]]"
    password: "[[${env.API_PASSWORD}]]"
```

Or at the CLI:

```bash
export API_USER=myusername
export API_PASSWORD=mypassword
rs --suite=suite.yml
```

This way, credentials never appear in your test suite file or command-line history.

## Complete templating examples

### Example 1: CLI variables + suite variables

```yaml
name: "Login Flow"
variables:
  # References CLI argument
  api_url: "[[${cli.api_url}]]"
  # Generates a random request ID
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"

tests:
- name: "Login"
  request:
    # References suite variable
    url: "[[${suite.api_url}]]/login"
    headers:
      x-request-id: "[[${suite.request_id}]]"
```

Run with:
```bash
rs --suite=suite.yml api_url=https://api.example.com
```

### Example 2: All four namespaces + `.env`

**.env file:**
```
API_KEY=my-secret-key
```

**suite.yml:**
```yaml
name: "Full Example"
variables:
  api_url: "[[${cli.api_url}]]"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"

tests:
- name: "Create user"
  variables:
    username: "alice"
    email: "alice@example.com"
  request:
    url: "[[${suite.api_url}]]/users"
    headers:
      Authorization: "Bearer [[${env.API_KEY}]]"
      x-request-id: "[[${suite.request_id}]]"
    body:
      type: "string"
      content: |
        {
          "username": "[[${test.username}]]",
          "email": "[[${test.email}]]",
          "createdAt": "[[${#temporals.format(#temporals.createToday(), 'yyyy-MM-dd')}]]"
        }
```

### Example 3: Optional values with ternary

```yaml
name: "Environment-aware"
variables:
  # Default to staging if no cli.env provided
  environment: "[[${cli.env != null ? cli.env : 'staging'}]]"
  # Default timeout if not provided
  timeout_ms: "[[${cli.timeout != null ? cli.timeout : '30000'}]]"
```

Run with optional values:
```bash
rs --suite=suite.yml env=production timeout=60000
```

Or use defaults:
```bash
rs --suite=suite.yml
# environment → staging, timeout_ms → 30000
```
