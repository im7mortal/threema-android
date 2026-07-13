package ch.threema.app.ui

import android.content.Context
import androidx.paging.PageKeyedDataSource
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkDirectory
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkDirectoryContact
import ch.threema.domain.protocol.api.work.WorkDirectoryFilter
import ch.threema.domain.stores.IdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("DirectoryDataSource")

class DirectoryDataSource(
    private val appContext: Context,
    private val preferenceService: PreferenceService,
    private val apiConnector: APIConnector,
    private val identityStore: IdentityStore,
    private val dispatcherProvider: DispatcherProvider,
) : PageKeyedDataSource<WorkDirectory, WorkDirectoryContact>() {

    private val coroutineScope = CoroutineScope(dispatcherProvider.main.immediate)

    init {
        addInvalidatedCallback {
            destroy()
        }
    }

    fun setQueryText(query: String?) {
        queryText = query
    }

    fun setQueryCategories(categories: List<WorkDirectoryCategory>) {
        queryCategories = categories
    }

    override fun loadInitial(
        params: LoadInitialParams<WorkDirectory>,
        callback: LoadInitialCallback<WorkDirectory, WorkDirectoryContact>,
    ) {
        if (!queryCategories.isEmpty()) {
            if (queryText.isNullOrEmpty()) {
                queryText = WILDCARD_SEARCH_ALL
            }
        } else if ((queryText?.length ?: 0) < MIN_SEARCH_STRING_LENGTH) {
            callback.onResult(emptyList(), null, null)
            return
        }

        logger.debug("Fetching query {} #categories {}", queryText, queryCategories.size)
        fetchInitialData(callback)
    }

    private fun fetchInitialData(callback: LoadInitialCallback<WorkDirectory, WorkDirectoryContact>) {
        val username = preferenceService.getLicenseUsername()
        val password = preferenceService.getLicensePassword()
        if (username == null || password == null) {
            logger.warn("Credentials missing, cannot fetch initial work directory")
            return
        }
        coroutineScope.launch {
            val workDirectory = try {
                withContext(dispatcherProvider.io) {
                    val workDirectoryFilter = WorkDirectoryFilter()
                    for (queryCategory in queryCategories) {
                        workDirectoryFilter.addCategory(queryCategory)
                    }
                    workDirectoryFilter.page(0)
                    workDirectoryFilter.sortBy(
                        if (preferenceService.isContactListSortingFirstName()) {
                            WorkDirectoryFilter.SORT_BY_FIRST_NAME
                        } else {
                            WorkDirectoryFilter.SORT_BY_LAST_NAME
                        },
                        true,
                    )
                    workDirectoryFilter.query(queryText)

                    apiConnector.fetchWorkDirectory(
                        username,
                        password,
                        identityStore,
                        workDirectoryFilter,
                    )
                }
            } catch (e: Exception) {
                logger.error("Unable to fetch directory", e)
                appContext.showToast(R.string.directory_request_failed, ToastDuration.LONG)
                null
            }

            callback.onResult(workDirectory?.workContacts ?: emptyList(), workDirectory, workDirectory)
        }
    }

    override fun loadBefore(
        params: LoadParams<WorkDirectory>,
        callback: LoadCallback<WorkDirectory, WorkDirectoryContact>,
    ) {
        fetchData(params.key.previousFilter ?: return, callback)
    }

    override fun loadAfter(
        params: LoadParams<WorkDirectory>,
        callback: LoadCallback<WorkDirectory, WorkDirectoryContact>,
    ) {
        logger.debug("*** loadAfter: {}", params.key.nextFilter?.page)
        fetchData(params.key.nextFilter ?: return, callback)
    }

    private fun fetchData(
        workDirectoryFilter: WorkDirectoryFilter,
        callback: LoadCallback<WorkDirectory, WorkDirectoryContact>,
    ) {
        val username = preferenceService.getLicenseUsername()
        val password = preferenceService.getLicensePassword()
        if (username == null || password == null) {
            logger.warn("Credentials missing, cannot fetch work directory")
            return
        }

        coroutineScope.launch {
            val workDirectory = try {
                withContext(dispatcherProvider.io) {
                    apiConnector.fetchWorkDirectory(
                        username,
                        password,
                        identityStore,
                        workDirectoryFilter,
                    )
                }
            } catch (e: Exception) {
                logger.error("Unable to fetch directory", e)
                appContext.showToast(R.string.directory_request_failed, ToastDuration.LONG)
                return@launch
            }

            callback.onResult(workDirectory.workContacts, workDirectory)
        }
    }

    fun destroy() {
        coroutineScope.cancel()
    }

    companion object {
        const val MIN_SEARCH_STRING_LENGTH = 3
        private const val WILDCARD_SEARCH_ALL = "*"

        private var queryText: String? = null
        private var queryCategories: List<WorkDirectoryCategory> = emptyList()
    }
}
