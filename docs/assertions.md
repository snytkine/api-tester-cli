# Assertions

Assertions are evaluated after the HTTP response is received. Each test must include at least one assertion. When an assertion fails, the test is marked as failed, but execution continues to evaluate all assertions (soft assertions).

## Path syntax.

Most assertions reference a location in the response using a `path` field. Paths start with `response.`:

| Path | Resolves to |
|------|-------------|
| `response.statusCode` | HTTP status code (integer) |
| `response.headers.x-request-id` | Header value (header names are case-insensitive) |
| `response.body.text` | Raw response body (string) |
| `response.body.json` | Full parsed JSON body (as a Map/List/primitive) |
| `response.body.json.user.name` | JSONPath-style nested access |
| `response.body.json[0]` | Array element access |
| `response.body.json.users[?(@.role == 'admin')]` | JSONPath filter expressions |

## Status code assertions.

### `status_code`

Verify the HTTP status code matches an exact value.

| Field | Type | Description |
|-------|------|-------------|
| `expected` | Integer | Expected status code |

```yaml
assertions:
- type: "status_code"
  expected: 200
```

### `status_in`

Verify the HTTP status code is one of several acceptable values.

| Field | Type | Description |
|-------|------|-------------|
| `expected` | List of integers | Acceptable status codes |

```yaml
assertions:
- type: "status_in"
  expected: [200, 201, 204]
```

## String assertions

### `string_match`

Verify an exact string match (with optional case sensitivity).

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | String | Expected value |
| `case_sensitive` | Boolean | Default true; when false, comparison is case-insensitive |

```yaml
assertions:
- type: "string_match"
  path: "response.body.json.status"
  expected: "success"
- type: "string_match"
  path: "response.headers.content-type"
  expected: "application/json"
  case_sensitive: false
```

### `string_contains`

Verify a substring exists.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | String | Substring to find |
| `case_sensitive` | Boolean | Default true |

```yaml
assertions:
- type: "string_contains"
  path: "response.body.text"
  expected: "User created successfully"
```

### `starts_with`

Verify a string starts with a prefix.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | String | Expected prefix |

```yaml
assertions:
- type: "starts_with"
  path: "response.body.json.message"
  expected: "Error:"
```

### `ends_with`

Verify a string ends with a suffix.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | String | Expected suffix |

```yaml
assertions:
- type: "ends_with"
  path: "response.body.json.filename"
  expected: ".json"
```

### `regex_match`

Verify a full regex match (equivalent to `^...$`).

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | String | Regex pattern |

```yaml
assertions:
- type: "regex_match"
  path: "response.body.json.email"
  expected: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
```

## Null and presence assertions

### `not_null`

Verify a value exists and is not null.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |

```yaml
assertions:
- type: "not_null"
  path: "response.body.json.userId"
```

### `is_null`

Verify a value is null or missing.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to check |

```yaml
assertions:
- type: "is_null"
  path: "response.body.json.error"
```

### `not_empty`

Verify a value exists, is not null, and (if a string) is not empty.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |

```yaml
assertions:
- type: "not_empty"
  path: "response.body.json.name"
```

## Boolean assertions

### `assert_true`

Verify a value is exactly `true` (not a string or number).

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a boolean |

```yaml
assertions:
- type: "assert_true"
  path: "response.body.json.isActive"
```

### `assert_false`

Verify a value is exactly `false`.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a boolean |

```yaml
assertions:
- type: "assert_false"
  path: "response.body.json.isDeleted"
```

## Numeric assertions

All numeric assertions accept `Number` instances or strings parseable as `double`.

### `greater_than`

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a number |
| `expected` | Double | Threshold |

```yaml
assertions:
- type: "greater_than"
  path: "response.body.json.count"
  expected: 0
```

### `greater_than_or_equal`

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a number |
| `expected` | Double | Threshold |

```yaml
assertions:
- type: "greater_than_or_equal"
  path: "response.body.json.score"
  expected: 80
```

### `less_than`

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a number |
| `expected` | Double | Threshold |

### `less_than_or_equal`

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a number |
| `expected` | Double | Threshold |

### `range`

Verify a number falls within an inclusive range.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to a number |
| `min` | Double | Minimum (inclusive) |
| `max` | Double | Maximum (inclusive) |

```yaml
assertions:
- type: "range"
  path: "response.body.json.temperature"
  min: -50.0
  max: 150.0
```

## Membership and type assertions

### `one_of`

Verify a value is one of a list of acceptable values.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the value |
| `expected` | List | List of acceptable values (strings, numbers, booleans) |

```yaml
assertions:
- type: "one_of"
  path: "response.body.json.status"
  expected: ["active", "inactive", "pending"]
```

### `value_type`

Verify the JSON type of a value.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to check |
| `expected` | String | Expected type: `string`, `number`, `boolean`, `array`, `object`, `null` |

```yaml
assertions:
- type: "value_type"
  path: "response.body.json.metadata"
  expected: "object"
```

## Array assertions

All array assertions require the value at the path to be a JSON array.

### `array_contains`

Verify an array contains a value.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |
| `expected` | Any (scalar) | Value to find |

```yaml
assertions:
- type: "array_contains"
  path: "response.body.json.tags"
  expected: "important"
```

### `array_contains_all`

Verify an array contains all values in a list.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |
| `expected` | List | All required values |

```yaml
assertions:
- type: "array_contains_all"
  path: "response.body.json.permissions"
  expected: ["read", "write", "delete"]
```

### `array_is_empty`

Verify an array is empty.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |

```yaml
assertions:
- type: "array_is_empty"
  path: "response.body.json.errors"
```

### `array_is_not_empty`

Verify an array has at least one element.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |

```yaml
assertions:
- type: "array_is_not_empty"
  path: "response.body.json.results"
```

### `array_size`

Verify an array has an exact size.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |
| `expected` | Integer | Expected element count |

```yaml
assertions:
- type: "array_size"
  path: "response.body.json.items"
  expected: 5
```

### `array_size_min`

Verify an array has at least N elements.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |
| `min` | Integer | Minimum element count |

```yaml
assertions:
- type: "array_size_min"
  path: "response.body.json.users"
  min: 1
```

### `array_size_max`

Verify an array has at most N elements.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the array |
| `max` | Integer | Maximum element count |

```yaml
assertions:
- type: "array_size_max"
  path: "response.body.json.warnings"
  max: 10
```

## Header assertion

### `has_header`

Verify the response contains a specific header.

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Header name (case-insensitive) |

```yaml
assertions:
- type: "has_header"
  name: "x-request-id"
```

## Response time assertion

### `response_time`

Verify the HTTP round-trip time does not exceed a threshold.

| Field | Type | Description |
|-------|------|-------------|
| `milliseconds` | Long | Maximum acceptable time in milliseconds |

```yaml
assertions:
- type: "response_time"
  milliseconds: 1000
```

## JSON body assertions

### `json_match`

Verify the response JSON matches an expected JSON document. Optionally ignore volatile fields.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the JSON value (e.g. `response.body.json` or `response.body.json.user`) |
| `expected` | Object or String | Either an object with `type` (inline or file) and `content` (JSON string or file path), plus optional `ignore` list — or a plain string, treated as inline content |

The `ignore` field removes specified top-level keys from both actual and expected before comparison, useful for timestamps and generated IDs.

A plain string is a shorthand for `type: inline`. The following two assertions are equivalent:

```yaml
assertions:
- type: "json_match"
  path: "response.body.json"
  expected: '{"id": 1, "name": "Alice"}'
# is equivalent to
- type: "json_match"
  path: "response.body.json"
  expected:
    type: "inline"
    content: '{"id": 1, "name": "Alice"}'
```

```yaml
assertions:
- type: "json_match"
  path: "response.body.json"
  expected:
    type: "inline"
    content: |
      {
        "id": 123,
        "name": "Alice",
        "status": "active"
      }
    ignore:
      - "id"
      - "createdAt"
```

Or load the expected response from a file:

```yaml
assertions:
- type: "json_match"
  path: "response.body.json"
  expected:
    type: "file"
    content: "expected-responses/user.json"
    ignore:
      - "id"
      - "createdAt"
```

File paths are resolved relative to the suite directory.

### `json_schema`

Validate the response JSON against a JSON Schema document.

| Field | Type | Description |
|-------|------|-------------|
| `path` | String | Path to the JSON value |
| `expected` | Object or String | Either an object with `type` and `content` (schema as string or file path) — or a plain string, treated as an inline schema |

Supports JSON Schema Draft 4, 6, 7, 2019-09, and 2020-12. Schema version is auto-detected from the `$schema` keyword.

As with `json_match`, a plain string is a shorthand for `type: inline` (an inline schema).

```yaml
assertions:
- type: "json_schema"
  path: "response.body.json"
  expected:
    type: "file"
    content: "schemas/user-schema.json"
```

Schema file example (resolves relative to suite directory):

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id": { "type": "integer" },
    "name": { "type": "string" },
    "email": { "type": "string", "format": "email" },
    "age": { "type": "integer", "minimum": 0 }
  }
}
```

---

See [Test Suite Configuration](test-suite-configuration.md) for complete test suite examples with assertions.
