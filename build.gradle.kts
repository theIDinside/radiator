buildscript {
    val compose_version: String by extra("1.4.0")
}// Top-level build file where you can add configuration options common to all sub-projects/modules.

configurations {
    implementation {
        resolutionStrategy.failOnVersionConflict()
    }
}

plugins {
    kotlin("jvm") version "1.8.0"
    id("com.android.application") version "8.0.0" apply false
    id("com.android.library") version "8.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.0" apply false
}