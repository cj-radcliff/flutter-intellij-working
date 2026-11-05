/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import java.net.URI

/**
 * Chooses the IDE that integration tests run against.
 *
 * The IDE is selected with two system properties, which the `integration` Gradle task forwards from Gradle properties:
 *
 * - `ide.product`: `IU` (default; the unified IntelliJ IDEA distribution), `AI` (Android Studio), or `IC` (IntelliJ
 *   IDEA Community Edition, which has no releases after 2025.2).
 * - `ide.version`: `latest` (default; the newest stable release of that product), a version (for example `2026.2.3`), or
 *   a build number (for example `262.10968.63`). For Android Studio, pass its version (for example `2026.1.2.8`, the
 *   same format as `androidStudioVersion` in `gradle.properties`).
 *
 * Example: `./gradlew integration -Pide.product=AI -Pide.version=2026.1.2.8`.
 */
object IdeSelection {
  const val PRODUCT_PROPERTY = "ide.product"
  const val VERSION_PROPERTY = "ide.version"
  const val LATEST = "latest"

  private const val ANDROID_STUDIO_RELEASES_URL = "https://jb.gg/android-studio-releases-list.json"

  /** Android Studio release-list channels that are stable releases. */
  private val ANDROID_STUDIO_STABLE_CHANNELS = setOf("Release", "Patch")

  /** A JetBrains build number such as `262.10968.63`, as opposed to a version such as `2026.2.3`. */
  private val BUILD_NUMBER = Regex("""^\d{3}\.\d+(\.\d+)*$""")

  /** Returns the IDE selected by the `ide.product` and `ide.version` system properties. */
  fun fromSystemProperties(): IdeInfo =
    resolve(System.getProperty(PRODUCT_PROPERTY), System.getProperty(VERSION_PROPERTY))

  /**
   * Returns the IDE for [product] and [version], where `null` or blank values select the defaults (`IU` and
   * [LATEST]).
   */
  fun resolve(product: String?, version: String?): IdeInfo {
    val code = product?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "IU"
    val requested = version?.trim()?.takeIf { it.isNotEmpty() } ?: LATEST
    val latest = requested.equals(LATEST, ignoreCase = true)

    val base = when (code) {
      "IU" -> IdeProductProvider.IU
      "IC" -> IdeProductProvider.IC
      "AI" -> IdeProductProvider.AI
      else -> error("Unsupported $PRODUCT_PROPERTY '$product'. Use IU, AI, or IC.")
    }.copy(buildType = BuildType.RELEASE.type)

    return when {
      // IDE Starter's Android Studio installer takes the Android Studio version in `buildNumber` and has no "latest".
      code == "AI" -> base.copy(buildNumber = if (latest) latestStableAndroidStudioVersion() else requested)
      code == "IC" && latest -> error(
        "IntelliJ IDEA Community Edition has no releases after 2025.2 (JetBrains now ships one unified IntelliJ IDEA " +
        "distribution). Use -P$PRODUCT_PROPERTY=IU, or pass -P$VERSION_PROPERTY with a 2025.2 or earlier build."
      )
      // With neither a build number nor a version, IDE Starter downloads the newest release of the product.
      latest -> base
      BUILD_NUMBER.matches(requested) -> base.copy(buildNumber = requested)
      else -> base.copy(version = requested)
    }
  }

  /**
   * Returns the newest stable Android Studio version, for example `2026.2.2.9`.
   *
   * This doesn't use IDE Starter's `AndroidInstaller.fetchLatestMajorReleaseVersion()`, which builds the version from
   * the release name ("2026.2.2 Patch 1" becomes `2026.2.2.1`) and so can pick an unrelated build: `2026.2.2.1` is
   * 2026.2.2 Canary 1. Instead, this reads the release list that the installer downloads from, which is ordered newest
   * first, and takes its first stable entry.
   */
  private fun latestStableAndroidStudioVersion(): String {
    val items = URI.create(ANDROID_STUDIO_RELEASES_URL).toURL().openStream().use { ObjectMapper().readTree(it) }
      .path("content").path("item")
    return items.firstOrNull { it.path("channel").asText() in ANDROID_STUDIO_STABLE_CHANNELS }
             ?.path("version")?.asText()?.takeIf { it.isNotBlank() }
           ?: error("No stable Android Studio release found in $ANDROID_STUDIO_RELEASES_URL")
  }
}
