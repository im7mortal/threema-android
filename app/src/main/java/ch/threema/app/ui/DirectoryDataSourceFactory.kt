package ch.threema.app.ui

import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import ch.threema.domain.protocol.api.work.WorkDirectory
import ch.threema.domain.protocol.api.work.WorkDirectoryContact
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class DirectoryDataSourceFactory : DataSource.Factory<WorkDirectory, WorkDirectoryContact>(), KoinComponent {

    private var init = true

    // Used to hold a reference to the data source
    @JvmField
    var postLiveData: MutableLiveData<DirectoryDataSource>? = null

    override fun create(): DataSource<WorkDirectory, WorkDirectoryContact> {
        val dataSource = DirectoryDataSource(
            appContext = get(),
            preferenceService = get(),
            apiConnector = get(),
            identityStore = get(),
            dispatcherProvider = get(),
        )

        if (init) {
            dataSource.setQueryText(null)
            init = false
        }

        postLiveData = MutableLiveData<DirectoryDataSource>()
        postLiveData!!.postValue(dataSource)

        return dataSource
    }

    fun destroy() {
        postLiveData?.value?.destroy()
    }
}
