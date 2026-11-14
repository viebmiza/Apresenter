package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.utils.appTelemetryIdentity
import org.churchpresenter.diagnostics.BuildIdentity
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.UiWatchdog
import org.churchpresenter.lowerthird.render.LottieFonts
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.signageFolder
import org.churchpresenter.sharedui.utils.SystemFonts
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.signage.hub.SignageHub
import org.churchpresenter.songs.bundledSongCount
import org.churchpresenter.telemetry.GpuInfo
import org.churchpresenter.telemetry.LiveMapReporter
import org.churchpresenter.updater.UpdateChecker
import org.churchpresenter.updater.UpdaterIdentity
import org.churchpresenter.updater.deleteLeftoverUpdateInstallers
import org.churchpresenter.web.presenter.CefManager

internal fun setupCrashReporter(startupSettings: AppSettings) {
    CrashReporter.initialize(
        startupSettings.analyticsReportingEnabled,
        BuildIdentity(
            versionDisplay = BuildConfig.VERSION_DISPLAY,
            appVersion = BuildConfig.APP_VERSION,
            isRelease = BuildConfig.IS_RELEASE,
            buildType = BuildConfig.BUILD_TYPE,
            buildChannel = BuildConfig.BUILD_CHANNEL,
        ),
    )
    CrashReporter.breadcrumb("Application started", category = "lifecycle")
    UiWatchdog.start(uiStallReporter::budgetMs, uiStallReporter::answered, uiStallReporter::stalled)
    UpdateChecker.initialize(UpdaterIdentity(appVersion = BuildConfig.APP_VERSION, isRelease = BuildConfig.IS_RELEASE))
    Thread { deleteLeftoverUpdateInstallers() }.apply { isDaemon = true; name = "installer-cleanup" }.start()
    CrashReporter.setTag("render.api", System.getProperty("skiko.renderApi") ?: "default")
    Thread {
        runCatching { CrashReporter.setConfigTags(GpuInfo.crashTags()) }
    }.apply { isDaemon = true; name = "gpu-tags" }.start()
}

internal fun launchTelemetryPing(startupSettings: AppSettings) {
    Thread {
        val pendingUsageEvents = LiveMapReporter.eventsToReport(startupSettings, UsageEvents.unreported())
        val previousRun = UsageEvents.lastRun()
        LiveMapReporter.pingOnOpen(
            identity = appTelemetryIdentity,
            installId = analyticsInstallId(startupSettings.analyticsReportingEnabled) { CrashReporter.installId() },
            updateCheckInterval = startupSettings.updateCheckInterval,
            setup = {
                LiveMapReporter.setupFacts(
                    startupSettings,
                    screenCount = LiveMapReporter.detectScreenCount(),
                    songCounts = LiveMapReporter.gatherSongCounts(startupSettings, ::bundledSongCount),
                    sessionMinutes = previousRun.sessionMinutes,
                    liveMinutes = previousRun.liveMinutes,
                    liveStalls = previousRun.liveStalls,
                    signageScreens = SignageHub.pairedScreensByPlatform(startupSettings.signageFolder().toPath()),
                )
            },
            events = pendingUsageEvents,
            onDelivered = {
                UsageEvents.markReported(pendingUsageEvents)
                if (!previousRun.isEmpty) UsageEvents.clearLastRun()
            },
        )
    }.apply { isDaemon = true; name = "telemetry-ping" }.start()
}

internal fun initCef(offscreenWeb: Boolean) {
    if (offscreenWeb) {
        CefManager.init(offscreen = true)
        CrashReporter.setTag("jcef.available", CefManager.initialized.toString())
        CrashReporter.setTag("web.render", "offscreen")
        if (CefManager.macOsUnsupported) CrashReporter.setTag("jcef.macos_unsupported", "true")
        if (CefManager.windowsUnsupported) CrashReporter.setTag("jcef.windows_unsupported", "true")
    } else {
        Thread {
            CefManager.init(offscreen = false)
            CrashReporter.setTag("jcef.available", CefManager.initialized.toString())
            CrashReporter.setTag("web.render", "windowed")
            if (CefManager.macOsUnsupported) CrashReporter.setTag("jcef.macos_unsupported", "true")
            if (CefManager.windowsUnsupported) CrashReporter.setTag("jcef.windows_unsupported", "true")
        }.apply { isDaemon = true; name = "jcef-init" }.start()
    }
}

internal fun warmUpFontsInBackground() {
    Thread {
        runCatching {
            LottieFonts.bundledFontResources().forEach { resource ->
                LottieFonts::class.java.getResourceAsStream(resource)?.let {
                    SlideFontRegistry.registerFontStream(it)
                }
            }
            SlideFontRegistry.initialize()
            SystemFonts.families()
        }
    }.apply { isDaemon = true }.start()
}
