/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

pluginManagement {
  repositories {
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-ui-test-robot")
    maven("https://repo1.maven.org/maven2/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
  }
}

include("ui-tests")
