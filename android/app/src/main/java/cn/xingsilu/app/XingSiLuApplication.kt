package cn.xingsilu.app

import android.app.Application
import cn.xingsilu.app.data.*

class XingSiLuApplication : Application() {
    val database by lazy { NoteDatabase.open(this) }
    val repository by lazy { NoteRepository(database) }
    val preferences by lazy { PreferencesRepository(this) }
}
