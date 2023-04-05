import org.gradle.kotlin.dsl.`kotlin-dsl`

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
    id("com.android.application") version "7.4.2" apply false
    id("com.android.library") version "7.4.2" apply false
}