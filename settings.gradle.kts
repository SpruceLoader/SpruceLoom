import groovy.lang.MissingPropertyException

rootProject.name = extra["project.name"]?.toString() ?:
        throw MissingPropertyException("Project name is missing!")
include(":bootstrap")
