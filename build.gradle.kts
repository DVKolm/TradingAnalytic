plugins {
    java
    application
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.panteleyev.jpackageplugin") version "1.6.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Dependency versions
val junitVersion = "5.10.2"
val lombokVersion = "1.18.30"
val sqliteVersion = "3.44.1.0"
val hibernateVersion = "6.6.6.Final"
val poiVersion = "5.4.0"


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

springBoot {
    mainClass = "com.example.ta.TradingAnalyticsApplication"
}

application {
    mainClass = "com.example.ta.JavaFxApplication"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.example.ta.TradingAnalyticsApplication")
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls", "javafx.fxml", "javafx.web",
        "javafx.swing", "javafx.media"
    )
}

// Annotation processor configurations
configurations {
    create("compileOnlyApi")
    getByName("compileOnly") {
        extendsFrom(configurations.getByName("compileOnlyApi"))
    }
    getByName("annotationProcessor") {
        extendsFrom(configurations.getByName("compileOnly"))
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // JavaFX UI libs
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("com.dlsc.formsfx:formsfx-core:11.6.0") {
        exclude(group = "org.openjfx")
    }
    implementation("net.synedra:validatorfx:0.5.0") {
        exclude(group = "org.openjfx")
    }
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-material2-pack:12.3.1")
    implementation("org.kordamp.bootstrapfx:bootstrapfx-core:0.4.0")
    implementation("eu.hansolo:tilesfx:21.0.3") {
        exclude(group = "org.openjfx")
    }
    implementation("com.github.almasb:fxgl:17.3") {
        exclude(group = "org.openjfx")
        exclude(group = "org.jetbrains.kotlin")
    }

    // DB
    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("org.hibernate.orm:hibernate-community-dialects:$hibernateVersion")

    // Lombok
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // Apache POI (Excel)
    implementation("org.apache.poi:poi:$poiVersion")
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    // Utilities
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io:2.16.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
}

// Spring Boot fat JAR
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("trading-analytics.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    manifest {
        attributes(
            "Main-Class" to "org.springframework.boot.loader.launch.JarLauncher",
            "Start-Class" to "com.example.ta.TradingAnalyticsApplication",
            "Spring-Boot-Version" to "3.3.0",
            "Spring-Boot-Classes" to "BOOT-INF/classes/",
            "Spring-Boot-Lib" to "BOOT-INF/lib/"
        )
    }
}

// Disable plain JAR
tasks.named<Jar>("jar") {
    enabled = false
    dependsOn(tasks.named("bootJar"))
}

tasks.jpackage {
    dependsOn("bootJar")

    appName = "Trading Analytics"
    appVersion = "1.0.0"
    vendor = "Trading Analytics"
    appDescription = "Trading Analytics"
    input = layout.buildDirectory.dir("libs").get().asFile.absolutePath
    destination = layout.buildDirectory.get().asFile.absolutePath
    mainJar = tasks.bootJar.get().archiveFileName.get()
    val iconFile = file("src/main/resources/app.ico")
    if (iconFile.exists()) {
        icon = iconFile.absolutePath
    }
    windows {
        winConsole = false
        winMenu = true
        winShortcut = true
        winShortcutPrompt = true
        winDirChooser = true
        winPerUserInstall = true
        winUpdateUrl = "https://github.com/DVKolm/TradingAnalytic"
        winMenuGroup = "TradingAnalytics"
    }

}

tasks.named("jpackage").configure {
    dependsOn(tasks.named("bootJar"))
}
