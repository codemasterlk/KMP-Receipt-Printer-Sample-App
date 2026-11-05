package dev.escpos.sample.presentation

/** Short badge text for the "Running on" section — cosmetic only. A presentation-layer concern
 *  (purely for display), unlike `dev.escpos.sample.data.platform.PlatformContext`, which no UI
 *  code touches. */
expect fun platformLabel(): String
