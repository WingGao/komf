rootProject.name = "komf"

pluginManagement {
    repositories {
        gradlePluginPortal()
//        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
//        maven("https://maven.aliyun.com/repository/public/")
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

include(":komf-app")
include(":komf-core")
include(":komf-mediaserver")
include(":komf-notifications")
include(":komf-client")
include(":komf-api-models")
