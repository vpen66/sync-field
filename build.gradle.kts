plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.vpen"
version = "1.0.1"

repositories {
//   mavenCentral()
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
}



// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath.set("/Applications/IntelliJ IDEA.app/Contents")
//    version.set("2024.3.1")
//    type.set("IU") // Target IDE Platform

    plugins.set(listOf("java"))
}

dependencies {
//    // 添加 IntelliJ Platform SDK 依赖
//    implementation("com.jetbrains.intellij.idea:ideaIC:2024.3.4.1") // 替换为实际版本号
//    implementation("com.jetbrains.intellij.platform:platform-api:2024.3.4.1")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        // 解决编译时中文报错
        options.encoding = "UTF-8"
    }
    // 添加以下内容，解决运行时控制台中文乱码
    withType<JavaExec> {
        jvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("243.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
