
plugins {
    java
    application
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.openjfx.javafxplugin") version "0.0.13"
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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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

tasks.withType<Test> {
    useJUnitPlatform()
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

// ============================================================================
// ФУНКЦИИ УТИЛИТЫ ДЛЯ ОЧИСТКИ
// ============================================================================

// Улучшенная функция принудительной очистки
fun forceCleanDirectory(dir: File, maxAttempts: Int = 5): Boolean {
    if (!dir.exists()) return true

    repeat(maxAttempts) { attempt ->
        try {
            println("🧹 Попытка ${attempt + 1} удаления: ${dir.absolutePath}")

            // Завершаем процессы на Windows
            if (org.gradle.internal.os.OperatingSystem.current().isWindows()) {
                try {
                    exec {
                        commandLine("cmd", "/c", "taskkill /f /im TradingAnalytics*.exe 2>nul")
                        isIgnoreExitValue = true
                    }
                    exec {
                        commandLine("cmd", "/c", "taskkill /f /im javaw.exe /fi \"WINDOWTITLE eq TradingAnalytics*\" 2>nul")
                        isIgnoreExitValue = true
                    }
                    Thread.sleep(2000)
                } catch (e: Exception) {
                    // Игнорируем ошибки taskkill
                }
            }

            // Принудительное удаление через attrib и del
            if (org.gradle.internal.os.OperatingSystem.current().isWindows()) {
                try {
                    exec {
                        commandLine("cmd", "/c", "attrib -r -h -s \"${dir.absolutePath}\\*\" /s /d 2>nul")
                        isIgnoreExitValue = true
                    }
                    exec {
                        commandLine("cmd", "/c", "rmdir /s /q \"${dir.absolutePath}\" 2>nul")
                        isIgnoreExitValue = true
                    }
                } catch (e: Exception) {
                    // Продолжаем с обычным удалением
                }
            }

            // Обычное удаление
            if (dir.exists()) {
                dir.deleteRecursively()
            }

            if (!dir.exists()) {
                println("✅ Директория успешно удалена")
                return true
            }

            if (attempt < maxAttempts - 1) {
                println("⏳ Ожидание ${(attempt + 1) * 2} секунд...")
                Thread.sleep((attempt + 1) * 2000L)
            }
        } catch (e: Exception) {
            println("⚠️ Ошибка при попытке ${attempt + 1}: ${e.message}")
            if (attempt < maxAttempts - 1) {
                Thread.sleep((attempt + 1) * 2000L)
            }
        }
    }

    return !dir.exists()
}

// ============================================================================
// ЗАДАЧИ ОЧИСТКИ
// ============================================================================

// Улучшенная задача cleanInstallers
tasks.register("cleanInstallers") {
    group = "build"
    description = "Force clean all installer directories"

    doLast {
        println("🧹 Принудительная очистка всех директорий установщиков...")

        val directories = listOf(
            layout.buildDirectory.dir("installer").get().asFile,
            layout.buildDirectory.dir("installer-msi").get().asFile,
            layout.buildDirectory.dir("installer-runtime").get().asFile,
            layout.buildDirectory.dir("installer-debug").get().asFile,
            layout.buildDirectory.dir("portable").get().asFile,
            layout.buildDirectory.dir("runtime").get().asFile,
            layout.buildDirectory.dir("jpackage-temp").get().asFile
        )

        directories.forEach { dir ->
            if (dir.exists()) {
                val success = forceCleanDirectory(dir)
                if (success) {
                    println("✅ ${dir.name} - очищено")
                } else {
                    println("❌ ${dir.name} - не удалось очистить полностью")
                }
            } else {
                println("ℹ️ ${dir.name} - уже отсутствует")
            }
        }

        println("🏁 Очистка завершена")
    }
}

// Расширяем задачу clean
tasks.named("clean") {
    dependsOn("cleanInstallers")
}

// ============================================================================
// ЗАДАЧИ ТЕСТИРОВАНИЯ
// ============================================================================

// Задача для тестирования JAR файла
tasks.register<JavaExec>("testJar") {
    dependsOn("bootJar")
    group = "verification"
    description = "Test the built JAR file"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile

    doFirst {
        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }
        println("🧪 Тестирование JAR файла: ${jarFile.absolutePath}")
    }

    classpath = files(jarFile)
    mainClass.set("com.example.ta.TradingAnalyticsApplication")

    jvmArgs = listOf(
        "-Xmx1024m",
        "-Dfile.encoding=UTF-8",
        "-Djava.awt.headless=false",
        "-Dspring.profiles.active=test"
    )
}

// ============================================================================
// ПОРТАТИВНАЯ ВЕРСИЯ
// ============================================================================

tasks.register<Exec>("createPortable") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create portable application"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("portable").get().asFile
    val iconFile = file("src/main/resources/app.ico")
    val javaHome = System.getProperty("java.home")

    doFirst {
        if (!forceCleanDirectory(outputDir)) {
            println("⚠️ Не удалось полностью очистить директорию, продолжаем...")
        }
        outputDir.mkdirs()

        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }

        println("🔨 Создание портативной версии...")
        println("JAR: ${jarFile.absolutePath}")
        println("Папка: ${outputDir.absolutePath}")
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalytics",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "app-image",
        "--dest", outputDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "TradingAnalytics",
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-portable").get().asFile.absolutePath
    )

    if (iconFile.exists()) {
        jpackageCmd.addAll(listOf("--icon", iconFile.absolutePath))
    }

    // JVM настройки для JavaFX + Spring Boot
    jpackageCmd.addAll(listOf(
        "--java-options", "-Xmx2048m",
        "--java-options", "-Xms512m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Djava.awt.headless=false",
        "--java-options", "-Dspring.aop.proxy-target-class=true",
        "--java-options", "-Dspring.main.web-application-type=none",
        "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--java-options", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED"
    ))

    commandLine(jpackageCmd)

    doLast {
        println("✅ Портативная версия создана!")
        println("📁 Расположение: ${outputDir.absolutePath}")

        val appDir = File(outputDir, "TradingAnalytics")
        if (appDir.exists()) {
            val totalSize = appDir.walkTopDown().filter { it.isFile() }.map { it.length() }.sum()
            println("📏 Размер: ${String.format("%.1f", totalSize / (1024.0 * 1024.0))} MB")

            val exeFile = File(appDir, "TradingAnalytics.exe")
            if (exeFile.exists()) {
                println("🚀 Запустите: ${exeFile.absolutePath}")
            }
        }
        Thread.sleep(500)
    }
}

// ============================================================================
// EXE УСТАНОВЩИК
// ============================================================================

tasks.register<Exec>("createInstaller") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create Windows EXE installer using jpackage"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("installer").get().asFile
    val iconFile = file("src/main/resources/app.ico")
    val javaHome = System.getProperty("java.home")

    doFirst {
        println("🔨 Создание EXE установщика...")
        println("JAR: ${jarFile.absolutePath}")
        println("Папка установщика: ${outputDir.absolutePath}")

        // Принудительная очистка с несколькими попытками
        if (!forceCleanDirectory(outputDir)) {
            throw GradleException("""
                ❌ Не удалось очистить директорию: ${outputDir.absolutePath}
                
                💡 Попробуйте:
                1. Закрыть все экземпляры TradingAnalytics
                2. Завершить процессы в Диспетчере задач
                3. Выполнить: ./gradlew cleanInstallers
                4. Или использовать: ./gradlew createInstallerSafe
                5. Или перезагрузить компьютер
            """.trimIndent())
        }

        outputDir.mkdirs()

        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalytics",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "exe",
        "--dest", outputDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "TradingAnalytics",
        "--copyright", "Copyright 2024 TradingAnalytics",
        "--description", "Trading Analytics Application",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-per-user-install",
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-exe").get().asFile.absolutePath
    )

    if (iconFile.exists()) {
        jpackageCmd.addAll(listOf("--icon", iconFile.absolutePath))
    }

    // JVM настройки
    jpackageCmd.addAll(listOf(
        "--java-options", "-Xmx2048m",
        "--java-options", "-Xms512m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Djava.awt.headless=false",
        "--java-options", "-Dspring.aop.proxy-target-class=true",
        "--java-options", "-Dspring.main.web-application-type=none",
        "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--java-options", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--java-options", "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED"
    ))

    commandLine(jpackageCmd)

    doLast {
        println("✅ EXE установщик создан успешно!")
        println("📁 Расположение: ${outputDir.absolutePath}")
        outputDir.listFiles()?.forEach { file ->
            println("   📦 ${file.name}")
            if (file.name.endsWith(".exe")) {
                println("   🎯 Установщик готов для распространения!")
                println("   📏 Размер: ${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB")
            }
        }
        Thread.sleep(500)
    }
}

// ============================================================================
// MSI УСТАНОВЩИК
// ============================================================================

tasks.register<Exec>("createMsiInstaller") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create MSI installer using jpackage"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("installer-msi").get().asFile
    val iconFile = file("src/main/resources/app.ico")
    val javaHome = System.getProperty("java.home")

    doFirst {
        if (!forceCleanDirectory(outputDir)) {
            println("⚠️ Не удалось полностью очистить директорию, продолжаем...")
        }
        outputDir.mkdirs()

        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }

        println("🔨 Создание MSI установщика...")
        println("JAR: ${jarFile.absolutePath}")
        println("Папка установщика: ${outputDir.absolutePath}")
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalytics",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "msi",
        "--dest", outputDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "TradingAnalytics",
        "--copyright", "Copyright 2024 TradingAnalytics",
        "--description", "Trading Analytics Application",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-per-user-install",
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-msi").get().asFile.absolutePath
    )

    if (iconFile.exists()) {
        jpackageCmd.addAll(listOf("--icon", iconFile.absolutePath))
    }

    // JVM настройки
    jpackageCmd.addAll(listOf(
        "--java-options", "-Xmx2048m",
        "--java-options", "-Xms512m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Djava.awt.headless=false",
        "--java-options", "-Dspring.aop.proxy-target-class=true",
        "--java-options", "-Dspring.main.web-application-type=none",
        "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--java-options", "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--java-options", "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
        "--java-options", "--add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED"
    ))

    commandLine(jpackageCmd)

    doLast {
        println("✅ MSI установщик создан успешно!")
        println("📁 Расположение: ${outputDir.absolutePath}")
        outputDir.listFiles()?.forEach { file ->
            println("   📦 ${file.name}")
            if (file.name.endsWith(".msi")) {
                println("   🎯 MSI установщик готов для распространения!")
                println("   📏 Размер: ${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB")
            }
        }
        Thread.sleep(500)
    }
}

// ============================================================================
// УСТАНОВЩИК С ВСТРОЕННОЙ JVM
// ============================================================================

tasks.register<Exec>("createInstallerWithRuntime") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create installer with embedded JVM runtime"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("installer-runtime").get().asFile
    val runtimeDir = layout.buildDirectory.dir("runtime").get().asFile
    val iconFile = file("src/main/resources/app.ico")
    val javaHome = System.getProperty("java.home")

    doFirst {
        if (!forceCleanDirectory(outputDir)) {
            println("⚠️ Не удалось полностью очистить output директорию")
        }
        if (!forceCleanDirectory(runtimeDir)) {
            println("⚠️ Не удалось полностью очистить runtime директорию")
        }
        outputDir.mkdirs()
        runtimeDir.mkdirs()

        if (!jarFile.exists()) {
            throw GradleException("JAR file not found: ${jarFile.absolutePath}")
        }

        println("🔨 Создание runtime образа...")
    }

    // Создаём минимальный runtime модуль
    exec {
        commandLine(
            "$javaHome/bin/jlink",
            "--add-modules", "java.base,java.desktop,java.logging,java.xml,java.sql,java.naming,java.security.jgss,java.instrument,java.management,jdk.unsupported",
            "--output", runtimeDir.absolutePath,
            "--compress", "2",
            "--no-header-files",
            "--no-man-pages"
        )
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalytics",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "exe",
        "--dest", outputDir.absolutePath,
        "--runtime-image", runtimeDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "TradingAnalytics",
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-runtime").get().asFile.absolutePath
    )

    if (iconFile.exists()) {
        jpackageCmd.addAll(listOf("--icon", iconFile.absolutePath))
    }

    commandLine(jpackageCmd)

    doLast {
        println("✅ Установщик с встроенной JVM создан!")
        println("📁 Расположение: ${outputDir.absolutePath}")
        outputDir.listFiles()?.forEach { file ->
            println("   📦 ${file.name}")
            if (file.name.endsWith(".exe")) {
                println("   🎯 Самодостаточный установщик (не требует Java на целевой машине)!")
                println("   📏 Размер: ${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB")
            }
        }
    }
}

// ============================================================================
// БЕЗОПАСНЫЕ АЛЬТЕРНАТИВЫ
// ============================================================================

// Альтернативная задача с уникальным именем файла
tasks.register<Exec>("createInstallerSafe") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create installer with timestamp to avoid file conflicts"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val timestamp = System.currentTimeMillis()
    val outputDir = layout.buildDirectory.dir("installer-$timestamp").get().asFile
    val iconFile = file("src/main/resources/app.ico")
    val javaHome = System.getProperty("java.home")

    doFirst {
        outputDir.mkdirs()
        println("🔨 Создание установщика с уникальным именем...")
        println("📁 Папка: ${outputDir.absolutePath}")
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalytics",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "exe",
        "--dest", outputDir.absolutePath,
        "--app-version", "1.0.0",
        "--vendor", "TradingAnalytics",
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-safe").get().asFile.absolutePath
    )

    if (iconFile.exists()) {
        jpackageCmd.addAll(listOf("--icon", iconFile.absolutePath))
    }

    jpackageCmd.addAll(listOf(
        "--java-options", "-Xmx2048m",
        "--java-options", "-Xms512m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Djava.awt.headless=false",
        "--java-options", "-Dspring.aop.proxy-target-class=true"
    ))

    commandLine(jpackageCmd)

    doLast {
        println("✅ Установщик создан: ${outputDir.absolutePath}")
        outputDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".exe")) {
                println("📦 ${file.name} - ${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB")
            }
        }
    }
}

// Отладочная версия установщика
tasks.register<Exec>("createDebugInstaller") {
    dependsOn("bootJar")
    group = "distribution"
    description = "Create debug version (app-image) for troubleshooting"

    val jarFile = tasks.bootJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("installer-debug").get().asFile
    val javaHome = System.getProperty("java.home")

    doFirst {
        if (!forceCleanDirectory(outputDir)) {
            println("⚠️ Не удалось полностью очистить debug директорию")
        }
        outputDir.mkdirs()
    }

    val jpackageCmd = mutableListOf(
        "$javaHome/bin/jpackage",
        "--input", jarFile.parent,
        "--name", "TradingAnalyticsDebug",
        "--main-jar", jarFile.name,
        "--main-class", "com.example.ta.TradingAnalyticsApplication",
        "--type", "app-image",
        "--dest", outputDir.absolutePath,
        "--verbose",
        "--temp", layout.buildDirectory.dir("jpackage-temp-debug").get().asFile.absolutePath
    )

    // Минимальные JVM настройки для тестирования
    jpackageCmd.addAll(listOf(
        "--java-options", "-Xmx1024m",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Djava.awt.headless=false"
    ))

    commandLine(jpackageCmd)

    doLast {
        val exeFile = File(outputDir, "TradingAnalyticsDebug/TradingAnalyticsDebug.exe")
        if (exeFile.exists()) {
            println("🔍 Отладочная версия создана: ${exeFile.absolutePath}")
            println("💡 Запустите её из командной строки для просмотра ошибок")
        }
    }
}

// ============================================================================
// СОСТАВНЫЕ ЗАДАЧИ
// ============================================================================

// Основная задача сборки всех установщиков
tasks.register("buildAllInstallers") {
    dependsOn("createInstaller", "createMsiInstaller", "createPortable")
    group = "distribution"
    description = "Build EXE installer, MSI installer and portable version"
}

// Безопасная сборка всех установщиков
tasks.register("buildAllInstallersSafe") {
    dependsOn("createInstallerSafe", "createPortable", "createDebugInstaller")
    group = "distribution"
    description = "Build all installers using safe methods"
}

// Основная задача распространения (совместимость с предыдущей версией)
tasks.register("buildDistribution") {
    dependsOn("buildAllInstallers")
    group = "distribution"
    description = "Build all distribution packages"
}

// Полный цикл сборки с очисткой
tasks.register("buildDistributionClean") {
    dependsOn("cleanInstallers", "buildAllInstallers")
    group = "distribution"
    description = "Clean and build all distribution packages"
}