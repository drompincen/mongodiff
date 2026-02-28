///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS org.springframework.boot:spring-boot-starter-web:3.2.5
//DEPS org.springframework.boot:spring-boot-starter-data-mongodb:3.2.5

//SOURCES src/main/java/com/example/comparison/ComparisonApplication.java
//SOURCES src/main/java/com/example/comparison/model/Account.java
//SOURCES src/main/java/com/example/comparison/model/ComparisonBreak.java
//SOURCES src/main/java/com/example/comparison/service/GenericComparisonService.java
//SOURCES src/main/java/com/example/comparison/controller/SampleDataController.java

//FILES static/index.html=src/main/resources/static/index.html

import com.example.comparison.ComparisonApplication;

public class MongoDiffUI {
    public static void main(String[] args) {
        ComparisonApplication.main(args);
    }
}
