package com.maxrave.simpmusic.spacekai

import org.koin.dsl.module

// SPACEKAI FEATURE
//
// Koin module for SpaceKai-scoped dependencies. It is additive: loaded via
// `loadKoinModules(spacekaiModule)` next to `viewModelModule`, and every
// definition here is optional (nothing upstream requires it), so an upstream
// merge that drops the load call simply disables the SpaceKai layer.
val spacekaiModule =
    module {
        // Holds the active feature set so SpaceKai-gated code can inject it
        // instead of reading the global. Falls back to defaults when
        // configSpaceKai was never called.
        single<SpaceKaiFeatures> { currentFeatures() }
    }