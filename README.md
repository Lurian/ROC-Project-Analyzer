# ROC-Project-Analyzer

This software tool was the fruit of my research during my master's course at the Federal University of Campina Grande (UFCG).

More information about my research can be found on my [research web site](https://sites.google.com/view/lucasandrade-research/home) 

## The tool

This tool was implemented using mostly the [Scala](https://www.scala-lang.org/) language with the [Akka](https://akka.io/) framework. Some tool integrations were done using [Java](https://www.java.com)

**Disclaimer**: This tool was created to aid in the internal process of gathering data and computing metrics of my research. 
It was not created for external use or have a general purpose.

With the above disclaimer in mind, I still bring use information below for those who want to study, use or upgrade the tool.

### External Services and Integration

For gathering data our tool integrates with other services:
 - **Gitlab**: VCS integration for source code and changes information
 - **ELK Stack**: Logging processing service for operational information
 - **Bugzilla**: Bug tracking and reporting information 
 - **MongoDB**: For storage of computed/gathered data

These services must be set up beforehand use of this tool. Some of these services are presumed to be running on the localhost.

## Setup

Four configuration files must be filled. Each section represents the config file that should be created in the src/main/resources folder and which information it should contain.

### mongoConfig.yaml
   - databaseName: *String*, containing the  name of the mongo database to use as storage
   - connectionString: *String*, representing the connection string to the mongo database, ex: "mongodb://localhost:27017"
### projectConfig.yaml
   - projectId: *String*, the ID of the Gitlab project 
   - projectDir: *String*, the absolute path to the folder containing the projects folders (each project should be an inner folder with the version name as its name)
   - restDir: *String*, the path to the rest Java files of the project from the root folder of the project
   - jarSubPath: *String*, the path to the generated JAR file of the project from the root folder of the project
   - rootPackage: *String*, root package used to identify projects java files
   - projectRootApiCall: *String*, root API string used to identify calls to the java server
### restConfig.yaml
   - restMap: *key-value map*
     -  Containing the string value contained in the class name (key) and the tag that endpoint tag to be associated if the class have it (value) 
   - subResourceSlashesThreshold: *Integer*, representing after how many slashes an endpoint should be considered as a sub-resource 
   - subResourceTag: *String*, representing the tag for sub-resources
   - resourceTag: *String*, representing the tag for resources
### gitlabConfig.yaml
   - url: *String*, the url to the Gitlab server instance
   - privateToken: *String*, the private token to access the instance in the v4 API
   - repoAddress: *String*, the repository address for the project to be analyzed
