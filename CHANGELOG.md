# Changelog

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
