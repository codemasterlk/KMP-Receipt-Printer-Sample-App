package dev.escpos.sample.presentation

import androidx.compose.runtime.Composable
import com.kmpgaraj.kmpescposprintersampleapp.getPlatform

/** The "Running on" badge's text — delegates to the sample scaffold's existing
 *  [com.kmpgaraj.kmpescposprintersampleapp.Platform] rather than duplicating per-platform
 *  detection. A presentation-layer concern (it's Composable, purely for display) — unlike
 *  `dev.escpos.sample.data.platform.PlatformContext`, which no UI code touches. */
@Composable
fun platformLabel(): String = getPlatform().name
