package org.zotero.android.sync.syncactions

import org.zotero.android.BuildConfig
import org.zotero.android.api.SyncApi
import org.zotero.android.api.mappers.SettingsResponseMapper
import org.zotero.android.api.network.CustomResult
import org.zotero.android.api.network.safeApiCall
import org.zotero.android.database.DbWrapper
import org.zotero.android.database.requests.StoreSettingsDbRequest
import org.zotero.android.sync.LibraryIdentifier
import org.zotero.android.sync.SyncAction
import org.zotero.android.sync.SyncError

class SyncSettingsSyncAction(
    private val currentVersion: Int?,
    private val sinceVersion: Int,
    private val libraryId: LibraryIdentifier,
    private val userId: Long,
    private val syncApi: SyncApi,
    private val settingsResponseMapper: SettingsResponseMapper,
    private val dbWrapper: DbWrapper,
) : SyncAction<CustomResult<Pair<Boolean, Int>>> {
    override suspend fun result(): CustomResult<Pair<Boolean, Int>> {
        val url =
            BuildConfig.BASE_API_URL + "/" + libraryId.apiPath(userId = this.userId) + "/settings"

        val networkResult = safeApiCall {
            syncApi.settingsRequest(
                url = url,
                since = sinceVersion,
                headers = mapOf("If-Modified-Since-Version" to this.sinceVersion.toString())
            )
        }

        if (networkResult !is CustomResult.GeneralSuccess.NetworkSuccess) {
            return networkResult as CustomResult.GeneralError
        }
        val newVersion = networkResult.lastModifiedVersion
        val current = this.currentVersion
        if (current != null && current != newVersion) {
            return CustomResult.GeneralError.CodeError(SyncError.NonFatal.versionMismatch(this.libraryId))
        }
        val value = networkResult.value!!
        val response = settingsResponseMapper.fromJson(value)
        val request = StoreSettingsDbRequest(response = response, libraryId = this.libraryId)
        dbWrapper.realmDbStorage.perform(request = request)
        val settingsChanged = newVersion != this.sinceVersion
        return CustomResult.GeneralSuccess(Pair(settingsChanged, newVersion))
    }
}