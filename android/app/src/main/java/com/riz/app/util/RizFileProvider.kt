package com.riz.app.util

import androidx.core.content.FileProvider

// Subclass so the consumer FileProvider has a distinct android:name from
// the one declared by the :updater AAR. Without this, AGP's manifest
// merger sees two <provider> elements with the same class and fails.
class RizFileProvider : FileProvider()
