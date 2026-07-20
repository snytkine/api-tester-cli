add following decisions: will add new templating namespace called 'session' to be available alongside cli, env, suite, test. 

Add new new property for the new parameter to test object in the test-suite model. New parameter name will be 'saved-session'. This parameter will hold a list of objects with following properties: 
 - name: a string value of the parameter name that will be added to global 'session' object. 
 - path - a json path expression that will be used to extract data from response. For example response.body.json.inventory.price
 - type: one of (string, integer, double, boolean) The extracted parameter with json path will in most cases be a string. However, if type is specified it will be converted to one of these types. If extracted values' original type is not a primitive value (not string, integer, boolean, double) it will count as an error with error message "Extracted session parameter ${name} in test ${name} at path ${path} is not a primitive type". Please check what types does our json path library returns for extracted json path values. This limitation of extracted types must be documented. It must be clear that extracted session parameters cannot be an object.
 - default optional default value. If omitted name will not be added to global 'session' object.
 - required: boolean flag indicating if this parameter is required. Default false. If true and value was not extracted from response's path will result in test failure with an error "Failed to extract session parameter ${name} from response at path ${path}". When another test depends-on the test that failed to extract parameter or failed for other reason then the dependent test will automatically be marked as failed with error 'Parent test ${id} failed with error ${parent_error}

 When variable is successfully extracted from a test it will be added to the global 'session' namespace under the key of the name property. This value can be used in other tests using the template's placeholder. For example if the name of save-session parameter in a test is 'widgetprice' then [[${sessioin.widgetsprice}]] It will be an end-user responsibility to make sure that values of name under the save-session are unique across all tests. If name is not unique it will be overritten by the last value extracted from response.

 When session parameter is extracted it must be logged at log.debug level. 

 The session namespace originally will be an empty object. The values in the session namespace will be available only in tests when test template is evaluated. Similar to 'test' namespae the session namespaced variables are available only during test execution when when resolving request url, headers and body content.

 Similar how the 'test' namespace variables are available in processing of body from file, the 'session' variables will be available in body template when body or json_match are stored in external file.

A test can have a new property 'depends-on' with the list of other test names. If this is set then the current test will not run until the dependent tests run. Dependent tests will run in order listed in the depends-on list. 

If multiple tests depend on same dependent test the dependent test must be run each time - once per each test that depends on it. This way if test depends on another test to create new record in database and then capture the id of created item the dependent test must be executed each time so than new item is created again.

Another new optional property will be available in each test 'transient' with boolean value. Default is false. When transient is true it means that test will only be run when another test has this test as a dependency. It will not run as a separate test - only when another test needs to run it as part of depends-on logic.