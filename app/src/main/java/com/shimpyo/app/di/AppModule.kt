package com.shimpyo.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.shimpyo.app.data.location.LocationGateway
import com.shimpyo.app.data.location.PleosLocationGateway
import com.shimpyo.app.data.navi.NaviGateway
import com.shimpyo.app.data.navi.PleosNaviGateway
import com.shimpyo.app.data.vehicle.PleosVehicleGateway
import com.shimpyo.app.data.vehicle.VehicleGateway
import com.shimpyo.app.data.voice.PleosVoiceGateway
import com.shimpyo.app.data.voice.VoiceGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Pleos SDK adapters behind interfaces, so tests and demos can swap them. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {
    @Binds abstract fun vehicle(impl: PleosVehicleGateway): VehicleGateway
    @Binds abstract fun navi(impl: PleosNaviGateway): NaviGateway
    @Binds abstract fun voice(impl: PleosVoiceGateway): VoiceGateway
    @Binds abstract fun location(impl: PleosLocationGateway): LocationGateway
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("shimpyo_settings") })
}
