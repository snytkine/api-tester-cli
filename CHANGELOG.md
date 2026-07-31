# Changelog

## [1.1.0](https://github.com/snytkine/cmd-rest/compare/v1.0.0...v1.1.0) (2026-07-31)


### Features

* upgrade Spring Boot to 4.0.7 to remediate critical CVE-2026-41855 ([424d437](https://github.com/snytkine/cmd-rest/commit/424d437c6985fff52e85fb0be8baffaeebd88523))

## [1.0.0](https://github.com/snytkine/cmd-rest/compare/v0.8.0...v1.0.0) (2026-07-31)


### ⚠ BREAKING CHANGES

* groupId, artifactId, and Java packages renamed from api-tester-cli/io.github.snytkine.apitester to cmd-rest/io.github.snytkine.cmdrest. CMDREST_ALLOW_SCRIPTS replaces APITESTER_ALLOW_SCRIPTS.

### Features

* add proxy support to rest-client config ([#71](https://github.com/snytkine/cmd-rest/issues/71)) ([83d02f4](https://github.com/snytkine/cmd-rest/commit/83d02f49d1d975976c07b6195bf56aec98205ad4))
* rename project to cmd-rest ([0eb7c55](https://github.com/snytkine/cmd-rest/commit/0eb7c556527217b88715dff505518e8e3baeeb53))

## [0.8.0](https://github.com/snytkine/api-tester-cli/compare/v0.7.1...v0.8.0) (2026-07-26)


### Features

* add follow-redirects option to rest-client config ([#72](https://github.com/snytkine/api-tester-cli/issues/72)) ([1e61a54](https://github.com/snytkine/api-tester-cli/commit/1e61a546a46f042b6dbaf825ff0ed6dcae61057b))

## [0.7.1](https://github.com/snytkine/api-tester-cli/compare/v0.7.0...v0.7.1) (2026-07-25)


### Bug Fixes

* skip JaCoCo coverage gate on Windows builds ([8f7b79b](https://github.com/snytkine/api-tester-cli/commit/8f7b79b74eef87243c8f5e8c6d5c61f79ae669cf))

## [0.7.0](https://github.com/snytkine/api-tester-cli/compare/v0.6.0...v0.7.0) (2026-07-25)


### Features

* add custom SSL/TLS certificate support for rest-clients ([#68](https://github.com/snytkine/api-tester-cli/issues/68)) ([19a7820](https://github.com/snytkine/api-tester-cli/commit/19a78207e4554b68c7bfd57d81424a6184521592))
* add implicit base_server_response assertion ([#69](https://github.com/snytkine/api-tester-cli/issues/69)) ([868ee60](https://github.com/snytkine/api-tester-cli/commit/868ee60f295f5b721bdb1c58458e2cc693815a7d))
* make test-case assertions an optional field ([#70](https://github.com/snytkine/api-tester-cli/issues/70)) ([e875b4f](https://github.com/snytkine/api-tester-cli/commit/e875b4f23b5038e6ba61311989efac6bed82c352))
* render report path as clickable OSC 8 hyperlink ([#73](https://github.com/snytkine/api-tester-cli/issues/73)) ([308b53c](https://github.com/snytkine/api-tester-cli/commit/308b53ce0002fb9371c110ecc56b232d09dbd676))

## [0.6.0](https://github.com/snytkine/api-tester-cli/compare/v0.5.0...v0.6.0) (2026-07-21)


### Features

* add --env-file option with explicit-file, cwd, then suite-dir resolution ([678364f](https://github.com/snytkine/api-tester-cli/commit/678364fbfc5cb7288de1c6bcd53a8345e190a42a)), closes [#62](https://github.com/snytkine/api-tester-cli/issues/62)
* add unique runID to identify each test-suite execution ([79c5c3c](https://github.com/snytkine/api-tester-cli/commit/79c5c3c9ed4664beec6b6b2046c767850d1d488e)), closes [#18](https://github.com/snytkine/api-tester-cli/issues/18) [#18](https://github.com/snytkine/api-tester-cli/issues/18)
* adding auth details to report if auth was used in specific test. auth credentials are masked ([3d9394b](https://github.com/snytkine/api-tester-cli/commit/3d9394b39eb283376fd1ee9f73e31292ce63473e))
* Implemented version check feature to check for latest version and show link to new version in report ([#55](https://github.com/snytkine/api-tester-cli/issues/55)) ([31b284d](https://github.com/snytkine/api-tester-cli/commit/31b284d455b98bd213585e9afdbd26514d9250a7))


### Bug Fixes

* block dependent test and session capture when depends-on parent fails ([056606e](https://github.com/snytkine/api-tester-cli/commit/056606ebd40da093a0c5fb641231b33c1a32e830)), closes [#56](https://github.com/snytkine/api-tester-cli/issues/56)


### Documentation

* use https for cmdrest.com documentation links in README ([7ee2f92](https://github.com/snytkine/api-tester-cli/commit/7ee2f92596c7aa66b5e170dea655075606667424))

## [0.5.0](https://github.com/snytkine/api-tester-cli/compare/v0.4.1...v0.5.0) (2026-07-04)


### Features

* added support for multiple rest-clients. Implements ([#33](https://github.com/snytkine/api-tester-cli/issues/33)) ([fa06a58](https://github.com/snytkine/api-tester-cli/commit/fa06a583762547dd6ef9a93de1c9042ae7267fa8))


### Bug Fixes

* json_match assertion now honors the path field. Fixes Issue [#29](https://github.com/snytkine/api-tester-cli/issues/29) … ([9d47ce5](https://github.com/snytkine/api-tester-cli/commit/9d47ce534973d075c54a13fdaee0f1483eb13678))
* json_match assertion now honors the path field. Fixes Issue [#29](https://github.com/snytkine/api-tester-cli/issues/29) https://github.com/snytkine/api-tester-cli/issues/29 ([9dbeee8](https://github.com/snytkine/api-tester-cli/commit/9dbeee8883f17d2e96cd2b0f812b4f0c5258ff6d))

## [0.4.1](https://github.com/snytkine/api-tester-cli/compare/v0.4.0...v0.4.1) (2026-06-14)


### Bug Fixes

* fixing GraalVM build issue on Windows. Fixes Issue [#49](https://github.com/snytkine/api-tester-cli/issues/49) https://github.com/snytkine/api-tester-cli/issues/49 ([cb08fac](https://github.com/snytkine/api-tester-cli/commit/cb08fac984c850201369acd944d8b7e5ea266ef0))

## [0.4.0](https://github.com/snytkine/api-tester-cli/compare/v0.3.0...v0.4.0) (2026-06-14)


### Features

* added support for version command to display application version ([5aa624e](https://github.com/snytkine/api-tester-cli/commit/5aa624e539ba70b7fa1d004c586156971eb0dbfd))
* added support for version command to display application version ([8008452](https://github.com/snytkine/api-tester-cli/commit/80084524dcdadeb74fe2a6a25c6fe8b31f1dd9f1))

## [0.3.0](https://github.com/snytkine/api-tester-cli/compare/v0.2.0...v0.3.0) (2026-06-14)


### Features

* adding handler for --version command to show application version. Adding footer to generated reports that shows version of program. ([def1fa3](https://github.com/snytkine/api-tester-cli/commit/def1fa3b4ab8c30bfe8feee2bcf536a166f58735))


### Bug Fixes

* provide fallback BuildProperties bean when build-info.properties is absent ([e7728d1](https://github.com/snytkine/api-tester-cli/commit/e7728d1ebba5174873e8c1a4db896e5dbf4c35bd))
* resolve versionCommand bean collision that blocked application startup ([e0395a0](https://github.com/snytkine/api-tester-cli/commit/e0395a0bc6ec5cf2f90af346c49a5146e9c05a29))


### Documentation

* document the version command in README and docs/ ([d6669e7](https://github.com/snytkine/api-tester-cli/commit/d6669e78de5f85791c1d2d7befca1c00c83449f4))

## [0.2.0](https://github.com/snytkine/api-tester-cli/compare/v0.1.0...v0.2.0) (2026-06-14)


### Features

* added minification of generated report by removing unnecessary whitespaces and linebreaks with regex while preserving contents inside 'pre' tags ([ef56e40](https://github.com/snytkine/api-tester-cli/commit/ef56e4050e894c1959ed82c720c44ef4d0d71b19))
* added option to use non pretty-printed json in report and have tiny javascript in report to convert raw json into pretty-printed format. This allows to reduce size of html report by about 10K with one large response body and 2 json bodies in assertion ([8137e15](https://github.com/snytkine/api-tester-cli/commit/8137e15426c2a8eb52a950e8640475e232318f25))
* added shell command to export test-suite JSON schema to a file ([b637fe9](https://github.com/snytkine/api-tester-cli/commit/b637fe901aca3fd227110a6fd1a2deeb9ede9bb3))
* added support for negated tags on command line. ([b200c2b](https://github.com/snytkine/api-tester-cli/commit/b200c2bd193b97c612fa87b4c8733012d3f65cb6))
* passing --suite=/path/to/file is optional. If not passed will use file test-suite.yml from current directory. If file not found in current directory then displays error. ([4bea280](https://github.com/snytkine/api-tester-cli/commit/4bea2800ba8337021e3e4f2071e95d716db64fef))
* Show assertion error message in terminal UI failure table ([deb6780](https://github.com/snytkine/api-tester-cli/commit/deb6780887c1dbfae83ea542b22d77851d7d18f8))


### Documentation

* **readme:** add project overview and usage guide ([035db0e](https://github.com/snytkine/api-tester-cli/commit/035db0e02f26559af7d07f4b16dc3c882db54319))
* **readme:** add usage and suite format guide ([352ff33](https://github.com/snytkine/api-tester-cli/commit/352ff3374f8da2115225e9c8c2180ccb692dbe39))
