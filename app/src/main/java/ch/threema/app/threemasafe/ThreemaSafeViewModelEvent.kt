package ch.threema.app.threemasafe

sealed interface ThreemaSafeViewModelEvent {
    data object Finish : ThreemaSafeViewModelEvent
}
