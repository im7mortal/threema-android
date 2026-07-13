package ch.threema.android

import android.view.Window

fun Window.hasFlag(flag: Int): Boolean =
    (attributes.flags and flag) != 0
