#Clojure Examples

## Example 2 - Serving A static Webpage

Contains a clojure project using leiningen and
./project.clj file for the project definitions, such as dependencies


./resources/index.html is a simple static html page that your server returns
it uses a compjure route and the ring middleware

./src/core.clj file for the clojure code serving the static content



## Example 1 - RESTful Interface Mockup

The previous example is extended by adding a RESTful interface delivering data.
For invoking the interface, you can either use

$ curl -i http://localhost:8000/accounts/101  returns the account object
$ curl -i http://localhost:8000/accounts/1012 returns a 404

or past the URL into your browser
