plugins {
    application
    id("io.freefair.lombok") version "8.10"
    id("io.github.goooler.shadow") version "8.1.8"
}

repositories {
    mavenCentral()
    maven("https://archiva.reflex.rip/repository/public/")
}

dependencies {
    val asmVersion = "9.7"

    implementation("me.darksidecode.kantanj:kantanj:1.1.0")
    implementation("me.darksidecode.jminima:jminima:1.4.0")
    implementation("org.ow2.asm:asm:$asmVersion")
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.diogonunes:JColor:5.5.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "me.darksidecode.keiko.proxy.Keiko"
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    mergeServiceFiles()

    exclude("DebugProbesKt.bin", "*.SF", "*.DSA", "*.RSA")

    dependencies {
        exclude(dependency("org.checkerframework:.*"))
        exclude(dependency("org.jetbrains:annotations"))
    }
}