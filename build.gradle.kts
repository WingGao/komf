import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}
plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    id("com.github.johnrengelman.shadow").version("8.1.1")
    id("com.google.devtools.ksp").version("1.9.10-1.0.13")
    id("org.flywaydb.flyway") version "9.22.3"
    id("nu.studer.jooq") version "8.2.1"
    id("com.apollographql.apollo3").version("3.8.2")
}

group = "org.snd"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-core:1.4.11")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
    implementation("com.apollographql.apollo3:apollo-runtime")

    implementation("io.javalin:javalin:5.6.2")

    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.1.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.1.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.1.0")

    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.jooq:jooq:3.18.7")
    implementation("org.xerial:sqlite-jdbc:3.43.0.0")
    jooqGenerator("org.xerial:sqlite-jdbc:3.43.0.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    implementation("io.github.pdvrieze.xmlutil:core-jvm:0.86.2")
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.86.2")
    implementation("com.charleskorn.kaml:kaml:0.55.0")

    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    implementation("commons-io:commons-io:2.14.0")
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("commons-validator:commons-validator:1.7")
    implementation("org.apache.velocity:velocity-engine-core:2.3")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("net.grey-panther:natural-comparator:1.1")

    implementation("com.microsoft.signalr:signalr:7.0.8")
    implementation("org.bitbucket.b_c:jose4j:0.9.3")

    implementation("com.twelvemonkeys.imageio:imageio-core:3.9.4")
    runtimeOnly("com.twelvemonkeys.imageio:imageio-jpeg:3.9.4")

}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi"
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks {
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "org.snd.ApplicationKt"))
        }
    }
}

sourceSets {
    // add a flyway sourceSet
    val flyway by creating {
        compileClasspath += sourceSets.main.get().compileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
    // main sourceSet depends on the output of flyway sourceSet
    main {
        output.dir(flyway.output)
    }
}

val dbSqlite = mapOf(
    "url" to "jdbc:sqlite:${project.buildDir}/generated/flyway/database.sqlite"
)
val migrationDirsSqlite = listOf(
    "$projectDir/src/flyway/resources/db/migration/sqlite",
)
flyway {
    url = dbSqlite["url"]
    locations = arrayOf("classpath:db/migration/sqlite")
}
tasks.flywayMigrate {
    // in order to include the Java migrations, flywayClasses must be run before flywayMigrate
    dependsOn("flywayClasses")
    migrationDirsSqlite.forEach { inputs.dir(it) }
    outputs.dir("${project.buildDir}/generated/flyway")
    doFirst {
        delete(outputs.files)
        mkdir("${project.buildDir}/generated/flyway")
    }
    mixed = true
}

jooq {
    version.set("3.18.7")
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.sqlite.JDBC"
                    url = dbSqlite["url"]
                }
                generator.apply {
                    database.apply {
                        name = "org.jooq.meta.sqlite.SQLiteDatabase"
                    }
                    target.apply {
                        packageName = "org.snd.jooq"
                    }
                }
            }
        }
    }
}

tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
    migrationDirsSqlite.forEach { inputs.dir(it) }
    allInputsDeclared.set(true)
    dependsOn("flywayMigrate")
}

apollo {
    service("service") {
        packageName.set("org.snd")
    }
}

tasks.wrapper {
    gradleVersion = "8.4"
    distributionType = Wrapper.DistributionType.ALL
}

tasks.register("depsize") {
    description = "Prints dependencies for \"runtime\" configuration"
    doLast {
        listConfigurationDependencies(configurations["runtimeClasspath"])
    }
}

fun listConfigurationDependencies(configuration: Configuration) {
    val formatStr = "%,10.2f"

    val size = configuration.sumOf { it.length() / (1024.0 * 1024.0) }

    val out = StringBuffer()
    out.append("\nConfiguration name: \"${configuration.name}\"\n")
    if (size > 0) {
        out.append("Total dependencies size:".padEnd(65))
        out.append("${String.format(formatStr, size)} Mb\n\n")

        configuration.sortedBy { -it.length() }
            .forEach {
                out.append(it.name.padEnd(65))
                out.append("${String.format(formatStr, (it.length() / 1024.0))} kb\n")
            }
    } else {
        out.append("No dependencies found")
    }
    println(out)
}
