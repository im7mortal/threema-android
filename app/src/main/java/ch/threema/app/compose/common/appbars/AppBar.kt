@file:OptIn(ExperimentalMaterial3Api::class)

package ch.threema.app.compose.common.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.R
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.preview.PreviewThreemaTabletAll
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import org.koin.compose.koinInject

private val windowInsets: WindowInsets
    @Composable
    get() = TopAppBarDefaults.windowInsets

private val defaultColors: TopAppBarColors
    @Composable
    @ReadOnlyComposable
    get() = TopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        subtitleContentColor = MaterialTheme.colorScheme.onSurface,
    )

/**
 *  A M3-style [TopAppBar] that displays a red indicator if the server connection is currently lost.
 *
 *  The [title], [subtitle], [navigationIcon] and [actions] components will be placed as defined by M3. To place custom content between the optional
 *  navigation- and action-icons, use the alternative composable expecting a dynamic `content` composable.
 *
 *  @param actions A row of action icon buttons. Make sure to use `LocalContentColor.current` for these icons.
 */
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    navigationIcon: NavigationIcon? = null,
    colors: TopAppBarColors = defaultColors,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val serverConnectionState: ConnectionState by watchServerConnectionState()
    AppBarBase(
        modifier = modifier,
        serverConnectionState = serverConnectionState,
        appBar = {
            AppBarDefault(
                title = title,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                colors = colors,
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    )
}

/**
 *  A M3-style [TopAppBar] that displays a red indicator if the server connection is currently lost.
 *
 *  The [navigationIcon] and [actions] components will be placed as defined by M3. Use [content] to specify content in between.
 *
 *  @param actions A row of action icon buttons. Make sure to use `LocalContentColor.current` for these icons.
 */
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
    navigationIcon: NavigationIcon? = null,
    colors: TopAppBarColors = defaultColors,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val serverConnectionState: ConnectionState by watchServerConnectionState()
    AppBarBase(
        modifier = modifier,
        serverConnectionState = serverConnectionState,
        appBar = {
            AppBarCustom(
                content = content,
                navigationIcon = navigationIcon,
                colors = colors,
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
    )
}

/**
 * Produces a state holding the most recent value of [ConnectionState].
 *
 * If composed inside a preview, a static default value [ConnectionState.LOGGED_IN] will be used.
 */
@Composable
private fun watchServerConnectionState(): State<ConnectionState> {
    if (LocalInspectionMode.current) {
        return remember {
            mutableStateOf(ConnectionState.LOGGED_IN)
        }
    }
    val serverConnection = koinInject<ServerConnection>()
    val connectionStateFlow = remember(serverConnection) {
        serverConnection.watchConnectionState()
    }
    return connectionStateFlow.collectAsStateWithLifecycle()
}

@Composable
private fun ConnectionStateIndicator(
    serverConnectionState: ConnectionState,
) {
    val color: Color? = when (serverConnectionState) {
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
        ConnectionState.CONNECTING -> MaterialTheme.colorScheme.error
        ConnectionState.CONNECTED -> colorResource(R.color.material_orange)
        ConnectionState.LOGGED_IN -> null
    }
    if (color != null) {
        Box(
            modifier = Modifier
                .offset(
                    y = windowInsets.asPaddingValues().calculateTopPadding(),
                )
                .fillMaxWidth()
                .height(2.dp)
                .background(color),
        )
    }
}

@Composable
private fun AppBarDefault(
    title: String,
    subtitle: String? = null,
    navigationIcon: NavigationIcon? = null,
    colors: TopAppBarColors = defaultColors,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            Column {
                ThemedText(
                    text = title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalContentColor.current,
                )
                if (!subtitle.isNullOrBlank()) {
                    ThemedText(
                        text = subtitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalContentColor.current,
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationIcon != null) {
                IconButton(
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = navigationIcon.containerColor,
                    ),
                    onClick = navigationIcon.onClick,
                ) {
                    Icon(
                        painter = painterResource(navigationIcon.icon.iconRes),
                        contentDescription = navigationIcon.icon.contentDescription?.let { stringRes ->
                            stringResource(stringRes)
                        },
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        windowInsets = windowInsets,
        colors = colors,
    )
}

@Composable
private fun AppBarCustom(
    content: @Composable () -> Unit,
    navigationIcon: NavigationIcon? = null,
    colors: TopAppBarColors,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = content,
        navigationIcon = {
            if (navigationIcon != null) {
                IconButton(
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = navigationIcon.containerColor,
                    ),
                    onClick = navigationIcon.onClick,
                ) {
                    Icon(
                        painter = painterResource(navigationIcon.icon.iconRes),
                        contentDescription = navigationIcon.icon.contentDescription?.let { stringRes ->
                            stringResource(stringRes)
                        },
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        windowInsets = windowInsets,
        colors = colors,
    )
}

@Composable
private fun AppBarBase(
    modifier: Modifier,
    serverConnectionState: ConnectionState,
    appBar: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
    ) {
        appBar()
        ConnectionStateIndicator(serverConnectionState)
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Disconnected() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.DISCONNECTED,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Connecting() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.CONNECTING,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Connected() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.CONNECTED,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Subtitle() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                    subtitle = "Member, Member, Member, Member",
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_NavigationIcon() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_NavigationIcon_Subtitle() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Group Name",
                    subtitle = "Member, Member, Member, Member, Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Actions() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Lastname",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_videocall),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Actions_Subtitle() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Group name",
                    subtitle = "Member, Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_videocall),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Overflow() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Laaaaaaaaaaaaaaaaaaastname",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Overflow_Actions() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Firstname Laaaaaaaaaaaaaaaaaaaaaaaaaaaaastname",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_Overflow_Actions_Subtitle() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Group naaaaaaaaaaaaaaaaaaaaaaaaaaame",
                    subtitle = "Member, Member, Member, Member, Member, Member, Member, Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@Preview(fontScale = 2.0f)
@Composable
private fun Preview_AppBar_Overflow_Actions_Subtitle_Zoom() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Group name",
                    subtitle = "Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewDynamicColors
@Composable
private fun Preview_AppBar_Dynamic_Colors() {
    ThreemaThemePreview(shouldUseDynamicColors = true) {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.LOGGED_IN,
            appBar = {
                AppBarDefault(
                    title = "Group name",
                    subtitle = "Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(
                            onClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun Preview_AppBar_All() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.DISCONNECTED,
            appBar = {
                AppBarDefault(
                    title = "Group name",
                    subtitle = "Member, Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_videocall),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}

@PreviewThreemaTabletAll
@Composable
private fun Preview_AppBar_Tablet() {
    ThreemaThemePreview {
        AppBarBase(
            modifier = Modifier,
            serverConnectionState = ConnectionState.DISCONNECTED,
            appBar = {
                AppBarDefault(
                    title = "Group name",
                    subtitle = "Member, Member, Member, Member",
                    navigationIcon = NavigationIcon.back(onClick = {}),
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phone_locked),
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(R.drawable.ic_videocall),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        )
    }
}
