
A hook can be one of two implementations:
 1. script
 2. web

 If its a script it will require the collowing properties:
 1. path - required, a full system path to an executible script.
 2. async - optional with default to false. If true then script can be executed in separate thread and failed script will not alter or fail the suite execution. 

 Hooks can be defined in the suite configuration file as follows:
 under top-level 'hooks' property there will be 6 possible lifecycle properties: beforeAll, afterAll, beforeEach, afterEach, beforeReport, afterReport.
 Each property will be a list of hooks.


 If callback is 'web' then it has following properties:
 1. rest-client - required, a named rest-client
 2. url - required, a relative or absolute URL to call.
 3.  method - optional with default to POST. Only PUT or POST methods are allowed.
 4. async - optional with default to false. async callback makes an http(s) request in separate thread and does not alter or fail the suite execution if it fails.


 ## script hook logic
 Script hook will receive following arguments passed to it in the form of param=value
 1. suite_id - id of the test suite. Required.
 2. run_id - value of runID that is generated for every suite run. Required.
 3. report_dir - value of report argument passed to run-suite command. Optional, only passed when --report value is passed on command line
 3. interactive - value of interactive or non-interactive execution mode. For interactive mode value is 'true', for non-interactive value is 'false'. Required
 4. test_name - optional, passed only if --test is passed to run-suite
 5. tag - optional, passed only if --tag is passed to run-suite
 6.  env_file - path to env file as it is passed on command line or resolved. Required.
 7. id - a string with unique hook id.
 8. timeout - number of seconds to wait for script execution before considering it as failed. 
 9. report_path - this is passed only in case of beforeReport, afterReport and afterAll hooks and onlyl when --report value is passed on command line. 
 10. url - a full url of test call. in case of beforeEach and afterEach hooks only.
 11. method - http method user in test call. Only for beforeEach and afterEach hook


 In case of timeout the program must also kill that running script. Optional, default is 10 seconds. 

 The script is expected to execute and return status code 0 for successful execution or any other code for failed execution. In case of error the script will write to stderr a message that will be logged in the test (optionally when logging is enabled) and also for a blocking script this error will be printed to the output with message starting with "Error execution hook ${hook_name}: ${str_error_message}". 
 For non-blocking scripts, if an error occurs, the UI will display warning message at the botton of the screen. When non-blocking async scripts still running after the test suite execution finished a spinner will be displayed on the bottom line of the UI with text "hook ${id} is still running". One line will be displayed for every hook that still running. When all hooks are done, the spinner will disappear and if any hook failed it will display error message. The generation of report will not be affected by running async scripts, program should not be blocked from generating a report if any hooks still run asynchronously.


 ## web hook logic
 Similar to script hooks.
 The same arguments will be passed to web hooks in a form of json payload.
 In addition to the standard arguments, web hooks will receive a 'body' and 'headers' properties in case of beforeEach and afterEach hooks. body is passed only when http(s) request has body in request.

 

