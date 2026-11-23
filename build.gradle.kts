plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// multiplatform-markdown-renderer 0.46.0 ships Java 21 (class file version 65) bytecode,
// so desktop tests and Java compilation must run on a Java 21+ toolchain.
subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val toolchains = extensions.getByType(JavaToolchainService::class.java)
        tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
            javaLauncher.set(
                toolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            )
        }
        tasks.withType<JavaCompile>().matching {
            it.name == "compileDesktopMainJava" || it.name == "compileDesktopTestJava"
        }.configureEach {
            javaCompiler.set(
                toolchains.compilerFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            )
        }
    }
}
