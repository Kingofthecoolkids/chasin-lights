package com.clearscreen.app.wallpaper

import android.service.wallpaper.WallpaperService

class ClearScreenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ClearScreenEngine(this)
}
