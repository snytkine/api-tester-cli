# Schema Support

`api-tester-cli` ships a JSON Schema that describes the complete structure of a test-suite YAML file. When you wire this schema to your editor, you get:

- **Inline validation** — fields with wrong types, missing required keys, or unrecognised assertion `type` values are flagged immediately as you type.
- **Autocompletion** — the editor suggests valid field names, assertion types, and enumeration values at the cursor position.
- **Hover documentation** — many editors surface the schema's `description` annotations as tooltips when you hover over a key.

This makes writing and maintaining test suites significantly faster and less error-prone.

## Exporting the schema

The schema is bundled inside the JAR and GraalVM native binary. Use the `export-schema` command
(alias `es`) to write a local copy to disk:

```bash
# JVM jar
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar export-schema --out ./schemas

# Native binary (full command name)
./target/api-tester-cli export-schema --out ./schemas

# Native binary (short alias)
./target/api-tester-cli es --out ./schemas
```

The `--out` option accepts an absolute or relative path to an **output directory**. The file is
always written as `test-suite-schema.json` inside that directory. The directory is created
automatically if it does not exist. An existing file is overwritten. On success the command prints
the absolute path of the written file:

```
Schema written to: /path/to/schemas/test-suite-schema.json
```

Keep the exported schema alongside your test-suite YAML files or in a shared location that your IDE project can reference.

## Wiring the schema to your editor

### Inline file header (universal)

The simplest approach — and the one that works across virtually all editors that support
[yaml-language-server](https://github.com/redhat-developer/yaml-language-server) — is to add a
single comment line at the very top of each test-suite YAML file:

```yaml
# yaml-language-server: $schema=/path/to/schemas/test-suite-schema.json
```

Replace `/path/to/schemas/test-suite-schema.json` with the real path where you saved
the exported schema file. Relative paths are resolved from the location of the YAML file:

```yaml
# yaml-language-server: $schema=./schemas/test-suite-schema.json
```

This approach requires no IDE-level configuration: any editor (VS Code, IntelliJ, Neovim, Emacs,
Helix, …) that has yaml-language-server active will pick up the schema automatically when it opens
the file. It also makes the schema binding self-documenting and portable — cloning the repository
on a different machine or in a different editor "just works" as long as the schema file is present
at the referenced path.

> **Note:** The `yaml-language-server` comment is read by the language server itself, not by the
> YAML parser, so it has no effect on how the file is loaded or executed by the CLI.

### VS Code

Install the [YAML extension by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml) (it is not bundled with VS Code by default).

Add a schema mapping to your workspace settings in `.vscode/settings.json`:

```json
{
  "yaml.schemas": {
    "./schemas/test-suite-schema.json": "**/*suite*.yml"
  }
}
```

The glob pattern on the right controls which files the schema applies to. Adjust it to match your naming convention. You can also use an array of patterns:

```json
{
  "yaml.schemas": {
    "./schemas/test-suite-schema.json": [
      "**/*-suite.yml",
      "**/*-suite-*.yml",
      "**/test-suite*.yml"
    ]
  }
}
```

Once saved, open any matching YAML file — the editor will underline invalid fields and offer completions as you type.

### IntelliJ IDEA / WebStorm

No additional plugin is required; JetBrains IDEs include JSON Schema support out of the box.

1. Open **Preferences** (or **Settings** on Windows/Linux).
2. Navigate to **Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings**.
3. Click **+** to add a new mapping.
4. Set **Schema file or URL** to the path of your exported schema file.
5. Add a **File path pattern** such as `*-suite.yml` or a directory pattern such as `test-suites/`.
6. Click **OK** and reopen any matching YAML file.

The IDE will highlight unknown keys, suggest completions from `enum` values, and show documentation popups from schema `description` fields.

### Neovim / Vim (via yaml-language-server)

Install [yaml-language-server](https://github.com/redhat-developer/yaml-language-server) and configure it with the exported schema path. With `nvim-lspconfig`:

```lua
require('lspconfig').yamlls.setup({
  settings = {
    yaml = {
      schemas = {
        ["/path/to/schemas/test-suite-schema.json"] = "*-suite.yml"
      }
    }
  }
})
```

### Emacs (via lsp-mode)

After installing `lsp-mode` and `yaml-language-server`, add to your config:

```elisp
(setq lsp-yaml-schemas
      '((local . ("/path/to/schemas/test-suite-schema.json"
                  . "*-suite.yml"))))
```

### Other editors

Any editor that supports the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) with [yaml-language-server](https://github.com/redhat-developer/yaml-language-server) can be configured similarly. Consult your editor's LSP plugin documentation for the exact configuration format.

> **Note:** Some editors require a third-party YAML or JSON Schema plugin to enable schema-based validation and autocompletion. If completions or error highlights do not appear after wiring the schema, check that a YAML language plugin is installed and active in your editor.

## What the schema validates

The schema enforces the complete test-suite structure, including:

- **Top-level fields** — `name`, `description`, `rest-client` / `rest-clients` (exactly one required), `variables`, `tests`
- **`rest-client` / `rest-clients` blocks** — `base-url`, `connect-timeout`, `headers`, and `auth` (with `type: "basic"` and credential fields); `rest-clients` entries also carry an `id`
- **Test cases** — `name`, `description`, `skip`, `tags`, `variables`, `request`, `assertions`
- **Request definitions** — method, URL, `rest-client` selector, headers, body (`inline` or `file`), and per-request auth
- **All 30+ assertion types** — each `type` value resolves to its own sub-schema with the correct required and optional fields

When you type `- type: ` inside an `assertions` list, a compliant editor will offer all valid assertion type names. Selecting one immediately reveals the fields that assertion expects.

## Keeping the schema up to date

The schema is versioned with the tool. When you upgrade `api-tester-cli`, re-run `export-schema`
(or `es`) to overwrite the local copy with the version matching the new binary. The command always
overwrites the destination file, so no manual cleanup is required.
