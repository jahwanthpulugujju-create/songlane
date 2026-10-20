package app.songlane

import android.app.Application
import app.songlane.data.LibraryRepository

class SonglaneApplication : Application() {
    val repository by lazy { LibraryRepository(this) }
}
